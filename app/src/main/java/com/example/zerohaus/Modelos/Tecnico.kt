package com.example.zerohaus.Modelos

data class Tecnico(
    val id: String = "",
    val uid: String = "",
    val nombre: String = "",
    val rating: Double = 0.0,
    val opiniones: Int = 0,
    val proyectosCompletados: Int = 0,
    val distanciaKm: Double = 0.0,
    val especialidades: List<String> = emptyList(),
    val descripcion: String = "",
    val telefono: String = "",
    val emailContacto: String = ""
)