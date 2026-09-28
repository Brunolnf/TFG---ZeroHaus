package com.example.zerohaus.Util

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Valoración dentro de la app con la API oficial de Google Play (In-App Review).
 *
 * Reglas de buen gusto para no molestar (y para que Play no lo ignore):
 *  - Solo se pide tras varias interacciones reales del usuario.
 *  - Solo se pide UNA vez por instalación (Play además limita la frecuencia).
 *  - Solo se lanza en un "momento feliz" (p. ej. el panel con un informe listo).
 *
 * En debug o sin Play Store el flujo falla en silencio: nunca rompe la app.
 */
object ResenaApp {

    fun pedirSiProcede(activity: Activity, prefs: AppPreferencias) {
        if (prefs.getResenaPedida()) return
        if (prefs.incrementarInteracciones() < UMBRAL_INTERACCIONES) return

        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Marcamos como pedida aunque el usuario no llegue a valorar:
                // el objetivo es no volver a interrumpirle.
                prefs.setResenaPedida(true)
                runCatching { manager.launchReviewFlow(activity, task.result) }
            }
        }
    }

    private const val UMBRAL_INTERACCIONES = 3
}
