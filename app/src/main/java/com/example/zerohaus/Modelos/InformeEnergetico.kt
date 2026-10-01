package com.example.zerohaus.Modelos

/**
 * Resultado de un preestudio energético (`/informes`). Lo calcula
 * [com.example.zerohaus.Repositorios.AlgoritmoEnergetico] a partir de una [Vivienda].
 */
data class InformeEnergetico(
    val id: String = "",
    val viviendaId: String = "",
    val uid: String = "",
    val nombreVivienda: String = "",
    val etiqueta: String = "",           // A, B, C, D, E, F, G
    val estadoEficiencia: String = "",
    val consumoEstimado: Double = 0.0,   // kWh/año
    val consumoPorM2: Double = 0.0,      // kWh/m²·año de energía final (0 en informes antiguos)
    // Energía primaria no renovable de calefacción, refrigeración y ACS
    // (kWh/m²·año): es lo que decide la etiqueta. 0 en informes anteriores al
    // modelo por usos, cuya etiqueta salía del consumo total por m².
    val energiaPrimariaM2: Double = 0.0,
    val emisiones: Double = 0.0,         // kg CO₂/año
    val costeAnual: Double = 0.0,        // €/año
    val recomendaciones: List<Recomendacion> = emptyList(),
    val fechaGeneracion: Long = System.currentTimeMillis(),
    // Electricidad: precio aplicado (el de la factura o el medio), consumo de
    // luz que estima el cálculo y el real de la factura (0 si no hay factura).
    // Todo 0 en informes antiguos.
    val precioLuz: Double = 0.0,           // €/kWh
    val consumoLuzEstimado: Double = 0.0,  // kWh/año
    val consumoLuzFactura: Double = 0.0    // kWh/año
)

/**
 * Mejora propuesta en un informe, con su ahorro recalculado por el algoritmo.
 */
data class Recomendacion(
    val titulo: String = "",
    val ahorroEstimado: Int = 0,     // porcentaje sobre el coste anual
    val ahorroKwh: Double = 0.0,     // kWh/año (0 en informes antiguos)
    val ahorroEuros: Double = 0.0    // €/año (0 en informes antiguos)
)