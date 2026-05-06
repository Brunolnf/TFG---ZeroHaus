
package com.example.zerohaus.Modelos

data class AjustesUsuario(
    val uid: String = "",
    val notificacionesPush: Boolean = true,
    val notificacionesEmail: Boolean = false,
    val notificacionesSonido: Boolean = true,
    val idioma: String = "Español",
    val tema: String = "Sistema",
    val unidadEnergia: String = "kWh",
    val unidadMoneda: String = "EUR"
)