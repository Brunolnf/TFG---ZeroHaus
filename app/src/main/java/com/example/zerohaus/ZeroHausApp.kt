package com.example.zerohaus

import android.app.Application
import android.util.Log
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.AppPreferencias
import com.example.zerohaus.Util.NotificacionesLocales
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * Inicialización centralizada de la app. Se ejecuta una sola vez al arrancar el
 * proceso, ANTES de cualquier Activity, ViewModel o servicio (FCM incluido).
 */
class ZeroHausApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Preferencias locales (tema, idioma, tipoUsuario cacheado…).
        AppEstado.inicializar(AppPreferencias(this))

        // 2. Canales de notificación.
        NotificacionesLocales.crearCanales(this)

        // 3. App Check SOLO en release (Play Integrity).
        //    En DEBUG NO se instala a propósito: el token de depuración cambia en
        //    cada reinstalación/dispositivo y, si no está en la Consola, Firestore
        //    BLOQUEA o CUELGA todas las queries → "se queda cargando para siempre".
        //    Sin provider en debug, las peticiones no esperan ningún token.
        //    (Para que en debug CARGUEN datos, ten App Check sin enforcement en la
        //    Consola mientras desarrollas; en la publicación final lo reactivas y
        //    el build release lo cubre con Play Integrity automáticamente.)
        if (!BuildConfig.DEBUG) {
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        } else {
            Log.w(TAG, "App Check NO instalado en debug (evita cuelgues por token de depuración).")
        }

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
