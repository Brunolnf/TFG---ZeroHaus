package com.example.zerohaus.Modelos

data class InformeEnergetico(
    val id: String = "",
    val viviendaId: String = "",
    val uid: String = "",
    val nombreVivienda: String = "",
    val etiqueta: String = "",           // A, B, C, D, E, F, G
    val estadoEficiencia: String = "",
    val consumoEstimado: Double = 0.0,   // kWh/año
    val emisiones: Double = 0.0,         // kg CO₂/año
    val costeAnual: Double = 0.0,        // €/año
    val recomendaciones: List<Recomendacion> = emptyList(),
    val fechaGeneracion: Long = System.currentTimeMillis()
)

data class Recomendacion(
    val titulo: String = "",
    val ahorroEstimado: Int = 0 // porcentaje
)