package com.example.zerohaus.Util

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * App Check en builds de depuración: token de depuración. La primera vez que
 * arranca, el SDK escribe en Logcat (etiqueta DebugAppCheckProvider) un secreto
 * que hay que añadir en Firebase Console → App Check → la app Android →
 * Administrar tokens de depuración. Cambia al desinstalar la app o al usar
 * otro dispositivo o emulador.
 */
object ProveedorAppCheck {
    val fabrica: AppCheckProviderFactory get() = DebugAppCheckProviderFactory.getInstance()
}
