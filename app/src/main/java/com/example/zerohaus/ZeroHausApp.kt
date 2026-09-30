package com.example.zerohaus

import android.app.Application
import android.util.Log
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.AppPreferencias
import com.example.zerohaus.Util.NotificacionesLocales
import com.example.zerohaus.Util.SecurityUtil
import com.example.zerohaus.Util.Diagnostico
import com.example.zerohaus.Util.ProveedorAppCheck
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * Clase Application: inicialización global antes de cualquier pantalla,
 * servicio o ViewModel. Carga las preferencias en [AppEstado], crea los
 * canales de notificación, instala App Check (Play Integrity en release,
 * token de depuración en debug) y activa la caché persistente de Firestore.
 */
class ZeroHausApp : Application() {

    override fun onCreate() {
        super.onCreate()

        if (!BuildConfig.DEBUG) {
            val amenazas = SecurityUtil.evaluarAmenazas(this)
            val criticas = amenazas.filter { it.nivel == 3 }
            if (criticas.isNotEmpty()) {
                FirebaseAuth.getInstance().signOut()
                android.os.Process.killProcess(android.os.Process.myPid())
                return
            }
        }

        AppEstado.inicializar(AppPreferencias(this))
        Diagnostico.inicializar()

        // 2. Canales de notificación.
        NotificacionesLocales.crearCanales(this)

        // 3. App Check: Play Integrity en release y token de depuración en debug
        //    (ver Util/ProveedorAppCheck.kt de cada build). Las Cloud Functions
        //    con enforce_app_check (borrar usuarios del panel de admin, borrar
        //    mi cuenta, suscripciones, IA) rechazan con UNAUTHENTICATED las
        //    llamadas sin token, así que en debug hay que registrar el token que
        //    sale en Logcat en Firebase Console → App Check.
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(ProveedorAppCheck.fabrica)

        // 4. Caché persistente de Firestore: las pantallas ya visitadas cargan al
        //    instante desde disco y la app sigue funcionando aunque la red falle.
        //    Debe fijarse antes de la primera operación de Firestore (aquí lo es).
        runCatching {
            FirebaseFirestore.getInstance().firestoreSettings =
                FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build()
                    )
                    .build()
        }.onFailure { Log.e(TAG, "No se pudo configurar la caché de Firestore: ${it.message}") }
    }

    companion object { private const val TAG = "ZeroHausApp" }
}
