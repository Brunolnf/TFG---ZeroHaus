package com.example.zerohaus.Modelos

data class Tecnico(
    val id: String,
    val nombre: String,
    val especialidad: String,
    val rating: Double,
    val opiniones: Int,
    val proyectosRealizados: Int
)