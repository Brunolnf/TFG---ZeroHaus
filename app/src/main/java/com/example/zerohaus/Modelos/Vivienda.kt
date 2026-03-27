package com.example.zerohaus.Modelos

data class Vivienda(
    val id: String = "",
    val uid: String = "",
    val nombre: String = "",
    val superficie: Int = 0,
    val anioConstruccion: Int = 2000,
    val tipoVentanas: String = "",
    val aislamiento: String = "",
    val calefaccion: String = "",
    val acs: String = "",
    val direccion: String = "",
    val orientacion: String = "",
    val fechaCreacion: Long = System.currentTimeMillis()
)