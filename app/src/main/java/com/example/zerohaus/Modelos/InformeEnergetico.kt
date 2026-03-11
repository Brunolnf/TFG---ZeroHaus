package com.example.zerohaus.Modelos

data class InformeEnergetico(
    val id: String,
    val viviendaId: String,
    val consumoEstimado: Double,
    val emisionesCO2: Double,
    val clasificacionEnergetica: String
)