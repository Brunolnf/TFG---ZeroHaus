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
        val consumoEstimado: Double,
        val emisiones: Double,
        val costeAnual: Double,
        val recomendaciones: List<Recomendacion>
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

    fun calcular(vivienda: Vivienda): ResultadoCalculo {
        val baseConsumo = vivienda.superficie * 1.2

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
            vivienda.anioConstruccion >= 2006 -> 0.95
            vivienda.anioConstruccion >= 1980 -> 1.1
            else                              -> 1.3
        }
        // Orientación sur maximiza captación solar pasiva (CTE DB HE1)
        val factorOrientacion = when (vivienda.orientacion) {
            "Sur"            -> 0.92
            "Este", "Oeste"  -> 1.0
            "Norte"          -> 1.08
            else             -> 1.0
        }
        // Penalización por severidad climática del invierno (CTE DB-HE).
        // Si la provincia no está mapeada → factor neutro 1.0.
        val factorZona = when (zonaClimaticaPorProvincia[vivienda.provincia]) {
            "α"  -> 0.75
            "A"  -> 0.85
            "B"  -> 0.95
            "C"  -> 1.05
            "D"  -> 1.20
            "E"  -> 1.35
            else -> 1.0
        }
        // Iluminación: la partida supone ~10-15 % del consumo eléctrico.
        // Pasar de halógeno a LED ahorra ~75 % de esa partida.
        val factorIluminacion = when (vivienda.iluminacion) {
            "Mayoría LED"                        -> 0.95
            "Mayoría halógenas o incandescentes" -> 1.10
            else                                 -> 1.0
        }
        // Factor de forma (CTE DB-HE): a más caras de la envolvente expuestas,
        // mayor demanda de calefacción/refrigeración para misma superficie útil.
        val factorTipoVivienda = when (vivienda.tipoVivienda) {
            "Piso interior"          -> 0.85
            "Piso esquina o ático"   -> 0.95
            "Adosado o pareado"      -> 1.05
            "Unifamiliar aislado"    -> 1.20
            else                     -> 1.0
        }
        // Refrigeración: equipo y existencia. En zonas frías el verano apenas
        // cuenta; en zonas cálidas el A/A puede ser 20-30 % del consumo eléctrico.
        val factorRefrigeracion = when (vivienda.refrigeracion) {
            "Sin refrigeración"            -> 0.93
            "Aerotermia"                   -> 0.95
            "A/A inverter eficiente"       -> 1.00
            "A/A convencional o antiguo"   -> 1.10
            else                           -> 1.0
        }
        // Autoconsumo fotovoltaico: descuenta directamente del consumo de red.
        val factorFotovoltaica = when (vivienda.fotovoltaica) {
            "Sin fotovoltaica"               -> 1.00
            "Pequeña (1-3 kWp)"              -> 0.85
            "Mediana (3-5 kWp)"              -> 0.70
            "Grande (>5 kWp) o con baterías" -> 0.55
            else                             -> 1.0
        }
        // Ocupantes: el ACS, cocina, electrodomésticos e iluminación escalan
        // con personas, no con m². Referencia: hogar de 3 personas (factor 1.0).
        val factorOcupantes = when {
            vivienda.ocupantes <= 0   -> 1.0
            vivienda.ocupantes == 1   -> 0.85
            vivienda.ocupantes == 2   -> 0.95
            vivienda.ocupantes in 3..4 -> 1.00
            else                       -> 1.10  // 5+
        }
        // Etiqueta energética de electrodomésticos (RD 2019): clase A consume
        // ~la mitad que clase D para mismos servicios.
        val factorElectrodomesticos = when (vivienda.electrodomesticos) {
            "Mayoría clase A o superior" -> 0.95
            "Clase B-C"                  -> 1.00
            "Clase D o antiguos"         -> 1.08
            else                         -> 1.0
        }

        val consumo = baseConsumo * factorVentanas * factorAislamiento *
                factorCalefaccion * factorAcs * factorAnio * factorOrientacion *
                factorZona * factorIluminacion * factorTipoVivienda *
                factorRefrigeracion * factorFotovoltaica * factorOcupantes *
                factorElectrodomesticos

        // Factor de emisiones: 0,22 kg CO₂/kWh (mix eléctrico español, IDAE 2023)
        val emisiones = consumo * 0.22
        // Precio medio electricidad España 2024: 0,15 €/kWh
        val coste = consumo * 0.15

        val etiqueta = when {
            consumo < 50  -> "A"
            consumo < 90  -> "B"
            consumo < 140 -> "C"
            consumo < 200 -> "D"
            consumo < 280 -> "E"
            consumo < 380 -> "F"
            else          -> "G"
        }
        val estadoEficiencia = when (etiqueta) {
            "A"  -> "Muy alta eficiencia"
            "B"  -> "Alta eficiencia"
            "C"  -> "Eficiencia buena"
            "D"  -> "Eficiencia media"
            "E"  -> "Eficiencia baja"
            "F"  -> "Eficiencia muy baja"
            else -> "Eficiencia mínima"
        }

        val recs = mutableListOf<Recomendacion>()
        if (vivienda.tipoVentanas == "Vidrio simple")
            recs.add(Recomendacion("Mejorar aislamiento de ventanas a doble acristalamiento", 25))
        if (vivienda.calefaccion == "Caldera de gas" || vivienda.calefaccion == "Eléctrica")
            recs.add(Recomendacion("Instalar aerotermia como sistema de calefacción", 20))
        if (vivienda.aislamiento != "Aislamiento completo")
            recs.add(Recomendacion("Completar el aislamiento térmico de la vivienda", 18))
        if (vivienda.acs != "Solar térmica" && vivienda.acs != "Aerotermia")
            recs.add(Recomendacion("Instalar solar térmica para ACS", 15))
        if (vivienda.iluminacion == "Mayoría halógenas o incandescentes")
            recs.add(Recomendacion("Sustituir halógenas e incandescentes por LED", 10))
        else if (vivienda.iluminacion != "Mayoría LED")
            recs.add(Recomendacion("Sustituir iluminación restante por LED", 8))
        if (vivienda.fotovoltaica == "Sin fotovoltaica" || vivienda.fotovoltaica.isBlank())
            recs.add(Recomendacion("Instalar autoconsumo fotovoltaico", 30))
        if (vivienda.refrigeracion == "A/A convencional o antiguo")
            recs.add(Recomendacion("Sustituir aire acondicionado por equipo inverter", 12))
        if (vivienda.electrodomesticos == "Clase D o antiguos")
            recs.add(Recomendacion("Renovar electrodomésticos a etiqueta A o superior", 8))

        return ResultadoCalculo(
            etiqueta = etiqueta,
            estadoEficiencia = estadoEficiencia,
            consumoEstimado = Math.round(consumo * 10.0) / 10.0,
            emisiones = Math.round(emisiones * 10.0) / 10.0,
            costeAnual = Math.round(coste * 10.0) / 10.0,
            recomendaciones = recs
        )
    }
}
