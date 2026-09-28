package com.example.zerohaus.Modelos

/**
 * Valoración de un cliente a un profesional (`/resenas`). Una por pareja
 * cliente-profesional (id `{uid}_{tecnicoId}`) y respaldada por un chat real.
 */
data class Resena(
    val id: String = "",
    val tecnicoId: String = "",
    val uid: String = "",
    val nombreUsuario: String = "",
    val puntuacion: Int = 5,
    val comentario: String = "",
    // Conversación real que respalda la reseña (las rules lo exigen al crear)
    val chatId: String = "",
    val fecha: Long = System.currentTimeMillis()
)
