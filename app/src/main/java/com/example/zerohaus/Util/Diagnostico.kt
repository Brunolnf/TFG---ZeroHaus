package com.example.zerohaus.Util

import com.example.zerohaus.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.security.MessageDigest

/**
 * Crashlytics con contexto y errores no fatales.
 *
 * - En depuración no se recoge nada, para no mezclar los fallos de las
 *   pruebas locales con los de los usuarios.
 * - Cada informe lleva el idioma y el tipo de usuario (nunca datos
 *   personales: ni uid, ni email, ni nombre).
 * - Los fallos del servidor al llamar a una Cloud Function se registran como
 *   no fatales: la app los muestra al usuario y sigue, así que sin esto una
 *   función caída o sin desplegar pasaría desapercibida.
 */
object Diagnostico {

    private val crashlytics: FirebaseCrashlytics? get() =
        runCatching { FirebaseCrashlytics.getInstance() }.getOrNull()

    fun inicializar() {
        crashlytics?.isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        crashlytics?.setCustomKey("version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        actualizarContexto()
        // Identificador sin datos personales: se pone al iniciar sesión y se vacía al cerrarla.
        runCatching {
            FirebaseAuth.getInstance().addAuthStateListener { auth ->
                crashlytics?.setUserId(identificadorAnonimo(auth.currentUser?.uid))
            }
        }
    }

    /**
     * SHA-256 en hexadecimal del uid, para agrupar los informes de un mismo
     * usuario sin exponer el uid. Cadena vacía si no hay sesión.
     */
    fun identificadorAnonimo(uid: String?): String {
        if (uid.isNullOrEmpty()) return ""
        return MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** Idioma y tipo de usuario actuales; se llama al cambiar cualquiera de los dos. */
    fun actualizarContexto() {
        crashlytics?.apply {
            setCustomKey("idioma", codigoIdioma(AppEstado.idioma))
            setCustomKey("tipo_usuario", AppEstado.tipoUsuarioCache.ifBlank { "sin_sesion" })
        }
    }

    /**
     * Registra el fallo de una Cloud Function si no es de red (sin conexión no
     * es un error de la app ni del servidor).
     */
    fun errorDeFuncion(funcion: String, e: Exception) {
        if (e.esFalloDeRed()) return
        crashlytics?.apply {
            setCustomKey("funcion", funcion)
            log("Fallo en la Cloud Function $funcion")
            recordException(e)
        }
    }
}
