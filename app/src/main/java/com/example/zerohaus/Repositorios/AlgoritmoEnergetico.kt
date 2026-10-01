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
        val precioLuz: Double,           // €/kWh aplicado (factura o precio medio)
        val energiaPrimariaM2: Double    // kWh/m²·año, base de la etiqueta
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

    // ── Modelo por usos ──────────────────────────────────────────────────
    // Cada uso de la energía se calcula por separado y cada factor afecta
    // solo al suyo: la envolvente y el clima a la calefacción, el equipo de
    // ACS al agua caliente, la iluminación a la luz, etc. Vivienda de
    // referencia (100 m², 3 personas, gas, todos los factores = 1):
    // ~11.000 kWh/año, de ellos ~3.000 de electricidad, en línea con un
    // hogar español medio (IDAE, estudio SPAHOUSEC).

    // Demanda útil de calefacción de referencia (kWh/m²·año), que luego
    // ajustan la envolvente, la orientación, el clima y el tipo de vivienda.
    private const val CALEFACCION_UTIL_M2 = 50.0

    // ACS: 28 L/persona·día a 60 °C (CTE DB-HE4) ≈ 570 kWh/año útiles, más
    // pérdidas de acumulación y distribución.
    private const val ACS_UTIL_PERSONA = 700.0
    // Para la etiqueta se usa una ocupación estándar ligada a la superficie
    // (3 personas en 100 m²), como en la certificación oficial: así la letra
    // no depende de cuántas personas vivan en la casa.
    private const val ACS_UTIL_M2_ETIQUETA = 21.0
    private const val OCUPANTES_POR_DEFECTO = 3

    // Demanda útil de refrigeración (kWh/m²·año) si hay equipo.
    private const val REFRIGERACION_UTIL_M2 = 15.0

    // Usos siempre eléctricos (frigorífico, cocina, lavadora, iluminación,
    // standby…): una parte fija por hogar y otra que crece con la superficie.
    private const val ELECTRICO_FIJO = 1_500.0
    private const val ELECTRICO_M2 = 15.0

    // La solar térmica cubre ~65 % del ACS; el resto lo aporta un apoyo eléctrico.
    private const val APOYO_SOLAR_TERMICA = 0.35
    private const val RENDIMIENTO_APOYO_SOLAR = 0.95

    // Los precios de la energía están en [PreciosEnergia] (Remote Config).

    // Factores de emisión oficiales para la certificación energética
    // (documento reconocido RITE "Factores de emisión de CO₂ y coeficientes
    // de paso a energía primaria", 2016), en kg CO₂/kWh de energía final.
    private const val CO2_ELECTRICIDAD = 0.331
    private const val CO2_GAS = 0.252
    private const val CO2_BIOMASA = 0.018

    // Coeficientes de paso a energía primaria NO renovable del mismo documento
    // (electricidad peninsular, gas natural, biomasa densificada).
    private const val PRIMARIA_ELECTRICIDAD = 1.954
    private const val PRIMARIA_GAS = 1.190
    private const val PRIMARIA_BIOMASA = 0.085

    private enum class Vector { ELECTRICIDAD, GAS, BIOMASA }

    /** Vector y rendimiento estacional (energía útil / final) de la calefacción. */
    private fun sistemaCalefaccion(c: String): Pair<Vector, Double> = when (c) {
        "Caldera de gas" -> Vector.GAS to 0.92
        "Biomasa"        -> Vector.BIOMASA to 0.85
        "Aerotermia"     -> Vector.ELECTRICIDAD to 3.0    // SCOP típico de bomba de calor
        else             -> Vector.ELECTRICIDAD to 1.0    // radiadores eléctricos (efecto Joule)
    }

    /** Vector y rendimiento del ACS (la solar térmica va aparte, ver [demandaAcs]). */
    private fun sistemaAcs(a: String): Pair<Vector, Double> = when (a) {
        "Gas"        -> Vector.GAS to 0.90
        "Aerotermia" -> Vector.ELECTRICIDAD to 2.8
        else         -> Vector.ELECTRICIDAD to 0.95       // termo eléctrico
    }

    /** Eficiencia estacional (SEER) del equipo de frío; null si no hay. */
    private fun seer(r: String): Double? = when (r) {
        "Aerotermia"                 -> 4.0
        "A/A inverter eficiente"     -> 5.0
        "A/A convencional o antiguo" -> 2.5
        else                         -> null               // sin refrigeración
    }

    private fun precio(v: Vector, vivienda: Vivienda) = when (v) {
        Vector.ELECTRICIDAD -> PreciosEnergia.electricidadPara(vivienda.precioLuzFactura)
        Vector.GAS          -> PreciosEnergia.gas
        Vector.BIOMASA      -> PreciosEnergia.biomasa
    }

    private fun co2(v: Vector) = when (v) {
        Vector.ELECTRICIDAD -> CO2_ELECTRICIDAD
        Vector.GAS          -> CO2_GAS
        Vector.BIOMASA      -> CO2_BIOMASA
    }

    private fun primaria(v: Vector) = when (v) {
        Vector.ELECTRICIDAD -> PRIMARIA_ELECTRICIDAD
        Vector.GAS          -> PRIMARIA_GAS
        Vector.BIOMASA      -> PRIMARIA_BIOMASA
    }

    // Fracción del consumo ELÉCTRICO cubierta por autoconsumo fotovoltaico
    // (30-50 % sin baterías; hasta 60-80 % con baterías).
    private fun coberturaFotovoltaica(fv: String) = when (fv) {
        "Pequeña (1-3 kWp)"              -> 0.30
        "Mediana (3-5 kWp)"              -> 0.45
        "Grande (>5 kWp) o con baterías" -> 0.65
        else                             -> 0.0
    }

    private fun factorVentanas(v: Vivienda) = when (v.tipoVentanas) {
        "Vidrio simple"          -> 1.4
        "Doble acristalamiento"  -> 1.0
        "Triple"                 -> 0.8
        else                     -> 1.2
    }

    private fun factorAislamiento(v: Vivienda) = when (v.aislamiento) {
        "Sin aislamiento"        -> 1.5
        "Aislamiento parcial"    -> 1.15
        "Aislamiento completo"   -> 0.8
        else                     -> 1.2
    }

    /**
     * Cuánto se aleja la demanda de calefacción de la de referencia por la
     * envolvente (ventanas, aislamiento, normativa del año de construcción),
     * la orientación, el clima y la forma de la vivienda.
     */
    private fun factorEnvolvente(v: Vivienda): Double {
        val factorAnio = when {
            v.anioConstruccion >= 2020 -> 0.8
            v.anioConstruccion >= 2006 -> 0.95   // entrada en vigor del CTE
            v.anioConstruccion >= 1980 -> 1.1    // NBE-CT-79
            else                       -> 1.3
        }
        // Orientación sur maximiza captación solar pasiva (CTE DB HE1)
        val factorOrientacion = when (v.orientacion) {
            "Sur"            -> 0.92
            "Este", "Oeste"  -> 1.0
            "Norte"          -> 1.08
            else             -> 1.0
        }
        // Severidad climática de invierno (CTE DB-HE). Provincia no mapeada → neutro.
        val factorZona = when (zonaClimaticaPorProvincia[v.provincia]) {
            "α"  -> 0.75
            "A"  -> 0.85
            "B"  -> 0.95
            "C"  -> 1.05
            "D"  -> 1.20
            "E"  -> 1.35
            else -> 1.0
        }
        // Factor de forma: a más caras de la envolvente expuestas, más demanda por m².
        val factorTipoVivienda = when (v.tipoVivienda) {
            "Piso interior"          -> 0.85
            "Piso esquina o ático"   -> 0.95
            "Adosado o pareado"      -> 1.05
            "Unifamiliar aislado"    -> 1.20
            else                     -> 1.0
        }
        return factorVentanas(v) * factorAislamiento(v) * factorAnio * factorOrientacion * factorZona * factorTipoVivienda
    }

    /** Usos siempre eléctricos (kWh/año): dependen de personas, iluminación y aparatos. */
    private fun demandaElectrica(v: Vivienda, superficie: Double): Double {
        val factorOcupantes = when {
            v.ocupantes <= 0    -> 1.0           // desconocido: hogar de referencia
            v.ocupantes == 1    -> 0.70
            v.ocupantes == 2    -> 0.85
            v.ocupantes in 3..4 -> 1.00
            else                -> 1.15
        }
        // Iluminación: ~10-15 % de estos usos; el LED ahorra ~75 % de esa partida.
        val factorIluminacion = when (v.iluminacion) {
            "Mayoría LED"                        -> 0.95
            "Mayoría halógenas o incandescentes" -> 1.10
            else                                 -> 1.0
        }
        val factorElectrodomesticos = when (v.electrodomesticos) {
            "Mayoría clase A o superior" -> 0.95
            "Clase B-C"                  -> 1.00
            "Clase D o antiguos"         -> 1.08
            else                         -> 1.0
        }
        return (ELECTRICO_FIJO + ELECTRICO_M2 * superficie) * factorOcupantes * factorIluminacion * factorElectrodomesticos
    }

    /** Energía final de ACS por vector para una demanda útil dada. */
    private fun demandaAcs(v: Vivienda, util: Double): Map<Vector, Double> =
        if (v.acs == "Solar térmica") {
            mapOf(Vector.ELECTRICIDAD to util * APOYO_SOLAR_TERMICA / RENDIMIENTO_APOYO_SOLAR)
        } else {
            val (vector, rendimiento) = sistemaAcs(v.acs)
            mapOf(vector to util / rendimiento)
        }

    /** Resultado numérico sin recomendaciones (se reutiliza para simular mejoras). */
    data class Balance(
        val consumoKwh: Double,         // energía final comprada (red + combustibles)
        val intensidad: Double,         // kWh/m²·año de energía final
        val emisiones: Double,          // kg CO₂/año
        val coste: Double,              // €/año
        val electricidadKwh: Double,    // kWh/año de electricidad de red
        val energiaPrimariaM2: Double   // kWh/m²·año, base de la etiqueta
    )

    fun balance(vivienda: Vivienda): Balance {
        val superficie = vivienda.superficie.coerceAtLeast(1).toDouble()
        val ocupantes = if (vivienda.ocupantes > 0) vivienda.ocupantes else OCUPANTES_POR_DEFECTO

        // Usos térmicos: calefacción, ACS y refrigeración
        val (vCalefaccion, rCalefaccion) = sistemaCalefaccion(vivienda.calefaccion)
        val calefaccion = superficie * CALEFACCION_UTIL_M2 * factorEnvolvente(vivienda) / rCalefaccion
        val refrigeracion = seer(vivienda.refrigeracion)?.let {
            superficie * REFRIGERACION_UTIL_M2 * factorVentanas(vivienda) * factorAislamiento(vivienda) / it
        } ?: 0.0
        fun termico(acsUtil: Double): MutableMap<Vector, Double> {
            val r = mutableMapOf(vCalefaccion to calefaccion)
            demandaAcs(vivienda, acsUtil).forEach { (k, kwh) -> r[k] = (r[k] ?: 0.0) + kwh }
            r[Vector.ELECTRICIDAD] = (r[Vector.ELECTRICIDAD] ?: 0.0) + refrigeracion
            return r
        }

        // El autoconsumo fotovoltaico solo descuenta electricidad de red
        val restoRed = 1.0 - coberturaFotovoltaica(vivienda.fotovoltaica)

        // Consumo real: ACS con las personas que viven y usos eléctricos
        val porVector = termico(ACS_UTIL_PERSONA * ocupantes)
        porVector[Vector.ELECTRICIDAD] = (porVector.getValue(Vector.ELECTRICIDAD) + demandaElectrica(vivienda, superficie)) * restoRed

        // Etiqueta: como en el certificado oficial, solo calefacción,
        // refrigeración y ACS (con ocupación estándar), en energía primaria
        // no renovable por m²
        val etiquetaVector = termico(ACS_UTIL_M2_ETIQUETA * superficie)
        etiquetaVector[Vector.ELECTRICIDAD] = etiquetaVector.getValue(Vector.ELECTRICIDAD) * restoRed
        val primariaM2 = etiquetaVector.entries.sumOf { (v, kwh) -> kwh * primaria(v) } / superficie

        val consumo = porVector.values.sum()
        val emisiones = porVector.entries.sumOf { (v, kwh) -> kwh * co2(v) }
        val coste = porVector.entries.sumOf { (v, kwh) -> kwh * precio(v, vivienda) }

        return Balance(consumo, consumo / superficie, emisiones, coste, porVector[Vector.ELECTRICIDAD] ?: 0.0, primariaM2)
    }

    /**
     * Escala A–G sobre energía primaria no renovable de calefacción,
     * refrigeración y ACS (kWh/m²·año), como el certificado del RD 390/2021.
     * Al trabajar por m² y con ocupación estándar, la letra NO depende del
     * tamaño: un chalet eficiente y un estudio eficiente obtienen la misma.
     */
    fun etiquetaPara(energiaPrimariaM2: Double): String = when {
        energiaPrimariaM2 < 40  -> "A"
        energiaPrimariaM2 < 65  -> "B"
        energiaPrimariaM2 < 100 -> "C"
        energiaPrimariaM2 < 150 -> "D"
        energiaPrimariaM2 < 225 -> "E"
        energiaPrimariaM2 < 320 -> "F"
        else                    -> "G"
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
            etiquetaActual = etiquetaPara(actual.energiaPrimariaM2),
            etiquetaNueva = etiquetaPara(nueva.energiaPrimariaM2),
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
        val etiqueta = etiquetaPara(actual.energiaPrimariaM2)

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
            precioLuz = PreciosEnergia.electricidadPara(vivienda.precioLuzFactura),
            energiaPrimariaM2 = redondear(actual.energiaPrimariaM2)
        )
    }
}
