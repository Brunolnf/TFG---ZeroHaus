
package com.example.zerohaus.Modelos

/**
 * Preferencias del usuario, guardadas en `/ajustes/{uid}`. El servidor lee
 * los interruptores de notificaciones para decidir si envía push o email
 * (ver `_preferencias` en functions/main.py).
 */
data class AjustesUsuario(
    val uid: String = "",
    val notificacionesPush: Boolean = true,
    val notificacionesEmail: Boolean = false,
    val notificacionesSonido: Boolean = true,
    // Granularidad de notificaciones (por tipo)
    val notificacionesMensajes: Boolean = true,
    val notificacionesValoraciones: Boolean = true,
    val idioma: String = "Español",
    val tema: String = "Sistema",
    val unidadEnergia: String = "kWh",
    val unidadMoneda: String = "EUR"
)
