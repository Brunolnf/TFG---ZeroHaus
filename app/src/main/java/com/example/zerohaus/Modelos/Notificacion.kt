package com.example.zerohaus.Modelos

/**
 * Notificación del historial dentro de la app (`/notificaciones`). La crean
 * las Cloud Functions al llegar un mensaje, una valoración o una suscripción.
 */
data class Notificacion(
    val id: String = "",
    val uid: String = "",
    val titulo: String = "",
    val detalle: String = "",
    val fecha: Long = System.currentTimeMillis(),
    val leida: Boolean = false,
    val tipo: String = "general" // general, chat, valoracion, suscripcion
)