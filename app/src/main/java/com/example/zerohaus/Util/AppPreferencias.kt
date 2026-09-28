package com.example.zerohaus.Util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Preferencias locales cifradas (EncryptedSharedPreferences, AES-256): idioma,
 * tema, unidades, notificaciones y cachés de sesión.
 */
class AppPreferencias(ctx: Context) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(ctx.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx.applicationContext,
            "zerohaus_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        ctx.applicationContext.getSharedPreferences("zerohaus_prefs", Context.MODE_PRIVATE)
    }

    fun getTema(): String = prefs.getString("tema", "Sistema") ?: "Sistema"
    fun setTema(v: String) = prefs.edit().putString("tema", v).apply()

    fun getIdioma(): String = prefs.getString("idioma", "Español") ?: "Español"
    fun setIdioma(v: String) = prefs.edit().putString("idioma", v).apply()

    fun getUnidadEnergia(): String = prefs.getString("unidad_energia", "kWh") ?: "kWh"
    fun setUnidadEnergia(v: String) = prefs.edit().putString("unidad_energia", v).apply()

    fun getUnidadMoneda(): String = prefs.getString("unidad_moneda", "EUR") ?: "EUR"
    fun setUnidadMoneda(v: String) = prefs.edit().putString("unidad_moneda", v).apply()

    fun getNotificacionesPush(): Boolean = prefs.getBoolean("notif_push", true)
    fun setNotificacionesPush(v: Boolean) = prefs.edit().putBoolean("notif_push", v).apply()

    fun getNotificacionesSonido(): Boolean = prefs.getBoolean("notif_sonido", true)
    fun setNotificacionesSonido(v: Boolean) = prefs.edit().putBoolean("notif_sonido", v).apply()

    fun getViviendaSeleccionadaId(): String = prefs.getString("vivienda_sel_id", "") ?: ""
    fun setViviendaSeleccionadaId(v: String) = prefs.edit().putString("vivienda_sel_id", v).apply()

    fun getTipoUsuarioCached(): String = prefs.getString("tipo_usuario_cache", "") ?: ""
    fun setTipoUsuarioCached(v: String) = prefs.edit().putString("tipo_usuario_cache", v).apply()

    fun getEsAdminCached(): Boolean = prefs.getBoolean("es_admin_cache", false)
    fun setEsAdminCached(v: Boolean) = prefs.edit().putBoolean("es_admin_cache", v).apply()

    // Valoración in-app: solo la pedimos una vez y tras varias interacciones.
    fun getResenaPedida(): Boolean = prefs.getBoolean("resena_pedida", false)
    fun setResenaPedida(v: Boolean) = prefs.edit().putBoolean("resena_pedida", v).apply()
    fun incrementarInteracciones(): Int {
        val n = prefs.getInt("interacciones", 0) + 1
        prefs.edit().putInt("interacciones", n).apply()
        return n
    }
}
