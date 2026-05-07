package com.example.zerohaus.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppEstado {
    var tema by mutableStateOf("Sistema")
    var idioma by mutableStateOf("Español")

    fun inicializar(prefs: AppPreferencias) {
        tema = prefs.getTema()
        idioma = prefs.getIdioma()
    }
}
