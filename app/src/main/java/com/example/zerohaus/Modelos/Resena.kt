package com.example.zerohaus.Modelos

data class Resena(
    val id: String = "",
    val tecnicoId: String = "",
    val uid: String = "",
    val solicitudId: String = "",   // proyecto/solicitud valorado: 1 reseña por proyecto completado
    val nombreUsuario: String = "",
    val puntuacion: Int = 5,
    val comentario: String = "",
    val fecha: Long = System.currentTimeMillis()
)