package com.example.zerohaus.Util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppEstado {
    var tema by mutableStateOf("Sistema")
    var idioma by mutableStateOf("Español")
    var unidadEnergia by mutableStateOf("kWh")
    var unidadMoneda by mutableStateOf("EUR")
    var notificacionesPush by mutableStateOf(true)
    var notificacionesSonido by mutableStateOf(true)

    fun inicializar(prefs: AppPreferencias) {
        tema = prefs.getTema()
        idioma = prefs.getIdioma()
        unidadEnergia = prefs.getUnidadEnergia()
        unidadMoneda = prefs.getUnidadMoneda()
        notificacionesPush = prefs.getNotificacionesPush()
        notificacionesSonido = prefs.getNotificacionesSonido()
    }
}
