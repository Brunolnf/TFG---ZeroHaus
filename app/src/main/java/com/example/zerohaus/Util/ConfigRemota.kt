package com.example.zerohaus.Util

import android.util.Log
import com.example.zerohaus.BuildConfig
import com.example.zerohaus.Repositorios.PreciosEnergia
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

/**
 * Parámetros que se cambian desde Firebase Console → Remote Config sin
 * publicar una versión nueva: los precios de la energía de los informes.
 *
 * Al arrancar se aplican los últimos valores descargados (o los de la app si
 * nunca se ha descargado nada) y se piden los nuevos en segundo plano. Un
 * informe usa los precios vigentes cuando se genera; los ya guardados no cambian.
 */
object ConfigRemota {

    private const val TAG = "ConfigRemota"

    private const val PRECIO_ELECTRICIDAD = "precio_electricidad"
    private const val PRECIO_GAS = "precio_gas"
    private const val PRECIO_BIOMASA = "precio_biomasa"

    fun inicializar() {
        val rc = runCatching { Firebase.remoteConfig }.getOrNull() ?: return
        rc.setConfigSettingsAsync(remoteConfigSettings {
            // En depuración se puede probar un cambio al momento; en producción
            // basta con mirar dos veces al día (los precios cambian poco).
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 60 else 12 * 3600
        })
        rc.setDefaultsAsync(mapOf(
            PRECIO_ELECTRICIDAD to PreciosEnergia.ELECTRICIDAD_DEFECTO,
            PRECIO_GAS to PreciosEnergia.GAS_DEFECTO,
            PRECIO_BIOMASA to PreciosEnergia.BIOMASA_DEFECTO
        ))
        aplicar(rc)
        rc.fetchAndActivate()
            .addOnSuccessListener { aplicar(rc) }
            .addOnFailureListener { Log.w(TAG, "No se pudo descargar Remote Config: ${it.message}") }
    }

    private fun aplicar(rc: FirebaseRemoteConfig) {
        PreciosEnergia.actualizar(
            electricidad = rc.getDouble(PRECIO_ELECTRICIDAD),
            gas = rc.getDouble(PRECIO_GAS),
            biomasa = rc.getDouble(PRECIO_BIOMASA)
        )
    }
}
