package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Modelos.Recomendacion
import com.example.zerohaus.Modelos.Vivienda

/**
 * Cálculo de eficiencia energética simplificado basado en los parámetros del CTE
 * (Código Técnico de la Edificación, DB HE - Ahorro de Energía) y en los factores
 * de conversión del IDAE (Instituto para la Diversificación y Ahorro de la Energía).
 *
 * La clasificación energética (A–G) sigue la escala de etiquetado energético de
 * edificios establecida en el RD 390/2021.
 */
object AlgoritmoEnergetico {

    data class ResultadoCalculo(
        val etiqueta: String,
        val estadoEficiencia: String,
        val consumoEstimado: Double,     // kWh/año de energía final
        val consumoPorM2: Double,        // kWh/m²·año (base de la etiqueta)
        val emisiones: Double,
        val costeAnual: Double,
        val recomendaciones: List<Recomendacion>,
        val consumoLuzEstimado: Double,  // kWh/año de electricidad de red
        val precioLuz: Double            // €/kWh aplicado (factura o precio medio)
    )

    // Zona climática de invierno por capital de provincia (CTE DB-HE).
    // α = invierno casi nulo; A→E severidad creciente. En zonas frías la
    // calefacción pesa más en el consumo total, por lo que aislar ahorra
    // más kWh/año en absoluto (se refleja al multiplicar por el factor).
    val zonaClimaticaPorProvincia: Map<String, String> = mapOf(
        "Las Palmas" to "α", "Santa Cruz de Tenerife" to "α",
        "Almería" to "A", "Cádiz" to "A", "Huelva" to "A", "Málaga" to "A",
        "Ceuta" to "A", "Melilla" to "A",
        "Alicante" to "B", "Illes Balears" to "B", "Barcelona" to "B",
        "Castellón" to "B", "Murcia" to "B", "Sevilla" to "B",
        "Tarragona" to "B", "Valencia" to "B",
        "A Coruña" to "C", "Asturias" to "C", "Bizkaia" to "C",
        "Cantabria" to "C", "Cáceres" to "C", "Córdoba" to "C",
        "Girona" to "C", "Granada" to "C", "Gipuzkoa" to "C",
        "Jaén" to "C", "Lugo" to "C", "Ourense" to "C", "Pontevedra" to "C",
        "Albacete" to "D", "Álava" to "D", "Badajoz" to "D",
        "Ciudad Real" to "D", "Cuenca" to "D", "Guadalajara" to "D",
        "Huesca" to "D", "La Rioja" to "D", "Lleida" to "D", "Madrid" to "D",
        "Navarra" to "D", "Salamanca" to "D", "Segovia" to "D",
        "Teruel" to "D", "Toledo" to "D", "Valladolid" to "D",
        "Zamora" to "D", "Zaragoza" to "D",
        "Ávila" to "E", "Burgos" to "E", "León" to "E",
        "Palencia" to "E", "Soria" to "E"
    )

    val provinciasOrdenadas: List<String> = zonaClimaticaPorProvincia.keys.sorted()

    val opcionesIluminacion: List<String> = listOf(
        "Mayoría LED",
        "Mixta",
        "Mayoría halógenas o incandescentes"
    )

    // Compacidad / factor de forma (CTE DB-HE): cuántas caras de la envolvente
    // están expuestas al exterior. Un piso interior pierde mucho menos calor
    // que un chalet aislado de la misma superficie.
    val opcionesTipoVivienda: List<String> = listOf(
        "Piso interior",
        "Piso esquina o ático",
        "Adosado o pareado",
        "Unifamiliar aislado"
    )

    // Refrigeración: en zonas cálidas (α/A/B) el verano puede pesar tanto
    // como el invierno y la eficiencia del equipo cambia mucho el consumo.
    val opcionesRefrigeracion: List<String> = listOf(
        "Sin refrigeración",
        "Aerotermia",
        "A/A inverter eficiente",
        "A/A convencional o antiguo"
    )

    // Autoconsumo fotovoltaico: una instalación residencial típica cubre
    // 30-50 % del consumo eléctrico anual; con baterías sube al 60-80 %.
    val opcionesFotovoltaica: List<String> = listOf(
        "Sin fotovoltaica",
        "Pequeña (1-3 kWp)",
        "Mediana (3-5 kWp)",
        "Grande (>5 kWp) o con baterías"
    )

    // Electrodomésticos (etiqueta energética A-G según RD 2019).
    val opcionesElectrodomesticos: List<String> = listOf(
        "Mayoría clase A o superior",
        "Clase B-C",
        "Clase D o antiguos"
    )

    // ── Parámetros de referencia ─────────────────────────────────────────
    // Intensidad de la vivienda de referencia (todos los factores = 1.0):
    // ~120 kWh/m²·año de energía final, en línea con el consumo medio de
    // los hogares españoles (IDAE, estudio SPAHOUSEC).
    private const val INTENSIDAD_BASE = 120.0

    // Reparto del consumo por usos (IDAE, SPAHOUSEC): calefacción y ACS son
    // las dos grandes partidas térmicas; el resto (iluminación, cocina,
    // electrodomésticos, refrigeración, standby) es siempre eléctrico.
    private const val CUOTA_CALEFACCION = 0.45
    private const val CUOTA_ACS = 0.20
    private const val CUOTA_ELECTRICA = 1.0 - CUOTA_CALEFACCION - CUOTA_ACS

    // Los precios de la energía están en [PreciosEnergia] (Remote Config).

    // Factores de emisión oficiales para la certificación energética
    // (documento reconocido RITE "Factores de emisión de CO₂ y coeficientes
    // de paso a energía primaria", 2016), en kg CO₂/kWh de energía final.
    private const val CO2_ELECTRICIDAD = 0.331
    private const val CO2_GAS = 0.252
    private const val CO2_BIOMASA = 0.018

    // La solar térmica cubre ~65 % del ACS; el resto lo aporta un apoyo eléctrico.
    private const val APOYO_SOLAR_TERMICA = 0.35

    private enum class Vector { ELECTRICIDAD, GAS, BIOMASA, SOLAR }

    private fun vectorCalefaccion(c: String) = when (c) {
        "Caldera de gas" -> Vector.GAS
        "Biomasa"        -> Vector.BIOMASA
        else             -> Vector.ELECTRICIDAD   // Eléctrica / Aerotermia
    }

    private fun vectorAcs(a: String) = when (a) {
        "Gas"           -> Vector.GAS
        "Solar térmica" -> Vector.SOLAR
        else            -> Vector.ELECTRICIDAD    // Eléctrico / Aerotermia
    }

    private fun precio(v: Vector, vivienda: Vivienda) = when (v) {
        Vector.ELECTRICIDAD -> PreciosEnergia.electricidadPara(vivienda.precioLuzFactura)
        Vector.GAS          -> PreciosEnergia.gas
        Vector.BIOMASA      -> PreciosEnergia.biomasa
        Vector.SOLAR        -> 0.0
    }

    private fun co2(v: Vector) = when (v) {
        Vector.ELECTRICIDAD -> CO2_ELECTRICIDAD
        Vector.GAS          -> CO2_GAS
        Vector.BIOMASA      -> CO2_BIOMASA
        Vector.SOLAR        -> 0.0
    }

    // Fracción del consumo ELÉCTRICO cubierta por autoconsumo fotovoltaico
    // (30-50 % sin baterías; hasta 60-80 % con baterías).
    private fun coberturaFotovoltaica(fv: String) = when (fv) {
        "Pequeña (1-3 kWp)"              -> 0.30
        "Mediana (3-5 kWp)"              -> 0.45
        "Grande (>5 kWp) o con baterías" -> 0.65
        else                             -> 0.0
    }

    /** Intensidad de energía final demandada (kWh/m²·año), antes de fotovoltaica. */
    private fun intensidadDemanda(vivienda: Vivienda): Double {
        val factorVentanas = when (vivienda.tipoVentanas) {
            "Vidrio simple"          -> 1.4
            "Doble acristalamiento"  -> 1.0
            "Triple"                 -> 0.8
            else                     -> 1.2
        }
        val factorAislamiento = when (vivienda.aislamiento) {
            "Sin aislamiento"        -> 1.5
            "Aislamiento parcial"    -> 1.15
            "Aislamiento completo"   -> 0.8
            else                     -> 1.2
        }
        val factorCalefaccion = when (vivienda.calefaccion) {
            "Caldera de gas"         -> 1.2
            "Eléctrica"              -> 1.4
            "Aerotermia"             -> 0.7
            "Biomasa"                -> 0.9
            else                     -> 1.1
        }
        val factorAcs = when (vivienda.acs) {
            "Gas"                    -> 1.1
            "Eléctrico"              -> 1.3
            "Solar térmica"          -> 0.6
            "Aerotermia"             -> 0.7
            else                     -> 1.0
        }
        val factorAnio = when {
            vivienda.anioConstruccion >= 2020 -> 0.8
            vivienda.anioConstruccion >= 2006 -> 0.95   // entrada en vigor del CTE
            vivienda.anioConstruccion >= 1980 -> 1.1    // NBE-CT-79
            else                              -> 1.3
        }
        // Orientación sur maximiza captación solar pasiva (CTE DB HE1)
        val factorOrientacion = when (vivienda.orientacion) {
            "Sur"            -> 0.92
            "Este", "Oeste"  -> 1.0
            "Norte"          -> 1.08
            else             -> 1.0
        }
        // Severidad climática de invierno (CTE DB-HE). Provincia no mapeada → neutro.
        val factorZona = when (zonaClimaticaPorProvincia[vivienda.provincia]) {
            "α"  -> 0.75
            "A"  -> 0.85
            "B"  -> 0.95
            "C"  -> 1.05
            "D"  -> 1.20
            "E"  -> 1.35
            else -> 1.0
        }
        // Iluminación: ~10-15 % del consumo eléctrico; LED ahorra ~75 % de esa partida.
        val factorIluminacion = when (vivienda.iluminacion) {
            "Mayoría LED"                        -> 0.95
            "Mayoría halógenas o incandescentes" -> 1.10
            else                                 -> 1.0
        }
        // Factor de forma: a más caras de la envolvente expuestas, más demanda por m².
        val factorTipoVivienda = when (vivienda.tipoVivienda) {
            "Piso interior"          -> 0.85
            "Piso esquina o ático"   -> 0.95
            "Adosado o pareado"      -> 1.05
            "Unifamiliar aislado"    -> 1.20
            else                     -> 1.0
        }
        val factorRefrigeracion = when (vivienda.refrigeracion) {
            "Sin refrigeración"            -> 0.93
            "Aerotermia"                   -> 0.95
            "A/A inverter eficiente"       -> 1.00
            "A/A convencional o antiguo"   -> 1.10
            else                           -> 1.0
        }
        // ACS, cocina y electrodomésticos escalan con personas. Referencia: 3-4.
        val factorOcupantes = when {
            vivienda.ocupantes <= 0    -> 1.0
            vivienda.ocupantes == 1    -> 0.85
            vivienda.ocupantes == 2    -> 0.95
            vivienda.ocupantes in 3..4 -> 1.00
            else                       -> 1.10
        }
        val factorElectrodomesticos = when (vivienda.electrodomesticos) {
            "Mayoría clase A o superior" -> 0.95
            "Clase B-C"                  -> 1.00
            "Clase D o antiguos"         -> 1.08
            else                         -> 1.0
        }

        return INTENSIDAD_BASE * factorVentanas * factorAislamiento *
                factorCalefaccion * factorAcs * factorAnio * factorOrientacion *
                factorZona * factorIluminacion * factorTipoVivienda *
                factorRefrigeracion * factorOcupantes * factorElectrodomesticos
    }

    /** Resultado numérico sin recomendaciones (se reutiliza para simular mejoras). */
    data class Balance(
        val consumoKwh: Double,      // energía final comprada (red + combustibles)
        val intensidad: Double,      // kWh/m²·año (base de la etiqueta)
        val emisiones: Double,       // kg CO₂/año
        val coste: Double,           // €/año
        val electricidadKwh: Double  // kWh/año de electricidad de red
    )

    fun balance(vivienda: Vivienda): Balance {
        val superficie = vivienda.superficie.coerceAtLeast(1).toDouble()
        val demandaTotal = intensidadDemanda(vivienda) * superficie

        val porVector = mutableMapOf<Vector, Double>()
        fun sumar(v: Vector, kwh: Double) { porVector[v] = (porVector[v] ?: 0.0) + kwh }

        sumar(vectorCalefaccion(vivienda.calefaccion), demandaTotal * CUOTA_CALEFACCION)
        val acs = demandaTotal * CUOTA_ACS
        when (val vAcs = vectorAcs(vivienda.acs)) {
            Vector.SOLAR -> sumar(Vector.ELECTRICIDAD, acs * APOYO_SOLAR_TERMICA)
            else         -> sumar(vAcs, acs)
        }
        sumar(Vector.ELECTRICIDAD, demandaTotal * CUOTA_ELECTRICA)

        // El autoconsumo fotovoltaico solo descuenta electricidad de red
        val cobertura = coberturaFotovoltaica(vivienda.fotovoltaica)
        porVector[Vector.ELECTRICIDAD] = (porVector[Vector.ELECTRICIDAD] ?: 0.0) * (1.0 - cobertura)

        val consumo = porVector.values.sum()
        val emisiones = porVector.entries.sumOf { (v, kwh) -> kwh * co2(v) }
        val coste = porVector.entries.sumOf { (v, kwh) -> kwh * precio(v, vivienda) }

        return Balance(consumo, consumo / superficie, emisiones, coste, porVector[Vector.ELECTRICIDAD] ?: 0.0)
    }

    /**
     * Escala A–G sobre intensidad de energía final (kWh/m²·año). Al trabajar
     * por m², la letra NO depende del tamaño: un chalet eficiente y un estudio
     * eficiente obtienen la misma calificación.
     */
    fun etiquetaPara(intensidad: Double): String = when {
        intensidad < 50  -> "A"
        intensidad < 90  -> "B"
        intensidad < 140 -> "C"
        intensidad < 200 -> "D"
        intensidad < 280 -> "E"
        intensidad < 380 -> "F"
        else             -> "G"
    }

    private fun estadoPara(etiqueta: String) = when (etiqueta) {
        "A"  -> "Muy alta eficiencia"
        "B"  -> "Alta eficiencia"
        "C"  -> "Eficiencia buena"
        "D"  -> "Eficiencia media"
        "E"  -> "Eficiencia baja"
        "F"  -> "Eficiencia muy baja"
        else -> "Eficiencia mínima"
    }

    /**
     * Una mejora aplicable a la vivienda. [aplicar] cambia UN campo, así que
     * varias mejoras se pueden combinar sin pisarse. [inversion] es un coste
     * orientativo de mercado en España (2024, IVA incl.), sin subvenciones.
     */
    data class Mejora(
        val titulo: String,
        val inversion: Double,
        val aplicar: (Vivienda) -> Vivienda
    )

    fun mejorasAplicables(v: Vivienda): List<Mejora> = buildList {
        val m2 = v.superficie.coerceAtLeast(1).toDouble()
        if (v.tipoVentanas == "Vidrio simple")
            // ~15 % de la superficie útil es hueco; ~300 €/m² de ventana instalada
            add(Mejora("Mejorar ventanas a doble acristalamiento", m2 * 45) { it.copy(tipoVentanas = "Doble acristalamiento") })
        if (v.aislamiento != "Aislamiento completo")
            // Fachada/cubierta ≈ 0,8 m² por m² útil a ~60 €/m² (SATE / insuflado)
            add(Mejora("Completar el aislamiento térmico de la vivienda", m2 * 50) { it.copy(aislamiento = "Aislamiento completo") })
        if (v.calefaccion == "Caldera de gas" || v.calefaccion == "Eléctrica")
            add(Mejora("Instalar aerotermia como sistema de calefacción", 9_000.0) { it.copy(calefaccion = "Aerotermia") })
        if (v.acs != "Solar térmica" && v.acs != "Aerotermia")
            add(Mejora("Instalar solar térmica para ACS", 3_500.0) { it.copy(acs = "Solar térmica") })
        if (v.iluminacion == "Mayoría halógenas o incandescentes")
            add(Mejora("Sustituir halógenas e incandescentes por LED", 300.0) { it.copy(iluminacion = "Mayoría LED") })
        else if (v.iluminacion != "Mayoría LED")
            add(Mejora("Sustituir iluminación restante por LED", 150.0) { it.copy(iluminacion = "Mayoría LED") })
        if (v.fotovoltaica == "Sin fotovoltaica" || v.fotovoltaica.isBlank())
            add(Mejora("Instalar autoconsumo fotovoltaico", 5_500.0) { it.copy(fotovoltaica = "Mediana (3-5 kWp)") })
        if (v.refrigeracion == "A/A convencional o antiguo")
            add(Mejora("Sustituir aire acondicionado por equipo inverter", 1_500.0) { it.copy(refrigeracion = "A/A inverter eficiente") })
        if (v.electrodomesticos == "Clase D o antiguos")
            add(Mejora("Renovar electrodomésticos a etiqueta A o superior", 2_500.0) { it.copy(electrodomesticos = "Mayoría clase A o superior") })
    }

    /** Resultado de aplicar a la vez varias mejoras ("¿qué pasa si…?"). */
    data class Simulacion(
        val etiquetaActual: String,
        val etiquetaNueva: String,
        val costeActual: Double,
        val costeNuevo: Double,
        val ahorroEuros: Double,     // €/año
        val ahorroKwh: Double,       // kWh/año
        val ahorroCo2: Double,       // kg CO₂/año
        val inversion: Double,       // €
        val amortizacionAnios: Double? // null si no hay ahorro
    )

    fun simular(vivienda: Vivienda, seleccion: List<Mejora>): Simulacion {
        val actual = balance(vivienda)
        val nueva = balance(seleccion.fold(vivienda) { v, m -> m.aplicar(v) })
        val ahorro = actual.coste - nueva.coste
        val inversion = seleccion.sumOf { it.inversion }
        return Simulacion(
            etiquetaActual = etiquetaPara(actual.intensidad),
            etiquetaNueva = etiquetaPara(nueva.intensidad),
            costeActual = redondear(actual.coste),
            costeNuevo = redondear(nueva.coste),
            ahorroEuros = redondear(ahorro),
            ahorroKwh = redondear(actual.consumoKwh - nueva.consumoKwh),
            ahorroCo2 = redondear(actual.emisiones - nueva.emisiones),
            inversion = inversion,
            amortizacionAnios = if (ahorro > 0 && inversion > 0) redondear(inversion / ahorro) else null
        )
    }

    private fun redondear(x: Double) = Math.round(x * 10.0) / 10.0

    fun calcular(vivienda: Vivienda): ResultadoCalculo {
        val actual = balance(vivienda)
        val etiqueta = etiquetaPara(actual.intensidad)

        val recs = mejorasAplicables(vivienda).mapNotNull { mejora ->
            val titulo = mejora.titulo
            val b = balance(mejora.aplicar(vivienda))
            val ahorroKwh = actual.consumoKwh - b.consumoKwh
            val ahorroEuros = actual.coste - b.coste
            if (ahorroEuros <= 0.0) return@mapNotNull null
            val pct = (ahorroEuros / actual.coste * 100).toInt()
            Recomendacion(
                titulo = titulo,
                ahorroEstimado = pct.coerceIn(1, 99),
                ahorroKwh = redondear(ahorroKwh),
                ahorroEuros = redondear(ahorroEuros)
            )
        }.sortedByDescending { it.ahorroEuros }

        return ResultadoCalculo(
            etiqueta = etiqueta,
            estadoEficiencia = estadoPara(etiqueta),
            consumoEstimado = redondear(actual.consumoKwh),
            consumoPorM2 = redondear(actual.intensidad),
            emisiones = redondear(actual.emisiones),
            costeAnual = redondear(actual.coste),
            recomendaciones = recs,
            consumoLuzEstimado = redondear(actual.electricidadKwh),
            precioLuz = PreciosEnergia.electricidadPara(vivienda.precioLuzFactura)
        )
    }
}
