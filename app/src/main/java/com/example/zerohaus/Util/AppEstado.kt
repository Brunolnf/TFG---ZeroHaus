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
    var tipoUsuarioCache by mutableStateOf("")

    private var prefs: AppPreferencias? = null

    fun inicializar(p: AppPreferencias) {
        prefs = p
        tema = p.getTema()
        idioma = p.getIdioma()
        unidadEnergia = p.getUnidadEnergia()
        unidadMoneda = p.getUnidadMoneda()
        notificacionesPush = p.getNotificacionesPush()
        notificacionesSonido = p.getNotificacionesSonido()
        tipoUsuarioCache = p.getTipoUsuarioCached()
    }

    /** ID de la vivienda activa seleccionada por el usuario (persiste entre sesiones). */
    fun getViviendaSeleccionadaId(): String = prefs?.getViviendaSeleccionadaId() ?: ""
    fun setViviendaSeleccionadaId(id: String) { prefs?.setViviendaSeleccionadaId(id) }

    fun guardarTipoUsuario(tipo: String) {
        tipoUsuarioCache = tipo
        prefs?.setTipoUsuarioCached(tipo)
    }

    fun limpiarTipoUsuario() {
        tipoUsuarioCache = ""
        prefs?.setTipoUsuarioCached("")
    }
}
