package com.example.zerohaus.Modelos

data class Notificacion(
    val id: String = "",
    val uid: String = "",
    val titulo: String = "",
    val detalle: String = "",
    val fecha: Long = System.currentTimeMillis(),
    val leida: Boolean = false,
    val tipo: String = "general" // general, presupuesto, proyecto, valoracion
)