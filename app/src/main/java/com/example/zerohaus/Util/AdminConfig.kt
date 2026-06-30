package com.example.zerohaus.Util

import com.google.firebase.auth.FirebaseAuth

/**
 * Identifica al administrador mediante el **custom claim `admin: true`** del
 * ID token de Firebase Auth.
 *
 * El claim se asigna server-side con el script `functions/set_admin_claim.py`
 * (Admin SDK) y viaja firmado en el JWT del usuario. El cliente no puede
 * falsificarlo: aunque alguien decompile el APK, no encontrará el email del
 * admin (no está aquí), y modificar la app local no cambia el JWT que emite
 * el servidor de Firebase.
 *
 * La decisión real "esta operación es de admin" se toma en `firestore.rules`
 * y en las Cloud Functions (`functions/main.py`), que leen el mismo claim.
 * Esta clase sólo sirve al cliente para decidir QUÉ pantalla pintar y
 * proteger la celda del admin en la lista de usuarios.
 */
object AdminConfig {
    /** Lee el flag cacheado (rellenado por SesionViewModel.refrescarClaims). */
    fun esAdmin(): Boolean = AppEstado.esAdminCache

    /** UID del admin actual, si la sesión está autenticada como admin. */
    val adminUid: String?
        get() = if (AppEstado.esAdminCache)
            FirebaseAuth.getInstance().currentUser?.uid
        else null
}
