package com.example.zerohaus.Util

/**
 * Configuración del rol administrador.
 *
 * Diseño consciente: el TFG sólo contempla **un único administrador**
 * (el desarrollador), por lo que se identifica por email hardcodeado
 * y no por un campo de Firestore. Así nadie puede auto-promoverse
 * editando documentos.
 */
object AdminConfig {
    const val ADMIN_EMAIL = "brulinf9@gmail.com"

    fun esAdmin(email: String?): Boolean =
        email?.equals(ADMIN_EMAIL, ignoreCase = true) == true
}
