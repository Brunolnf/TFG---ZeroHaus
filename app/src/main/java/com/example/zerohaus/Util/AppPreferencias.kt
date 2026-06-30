package com.example.zerohaus.Util

import android.content.Context

class AppPreferencias(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("zerohaus_prefs", Context.MODE_PRIVATE)

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

    // Vivienda activa seleccionada por el usuario (persiste entre sesiones)
    fun getViviendaSeleccionadaId(): String = prefs.getString("vivienda_sel_id", "") ?: ""
    fun setViviendaSeleccionadaId(v: String) = prefs.edit().putString("vivienda_sel_id", v).apply()

    // Tipo de usuario cacheado para evitar esperar a Firestore al abrir la app
    fun getTipoUsuarioCached(): String = prefs.getString("tipo_usuario_cache", "") ?: ""
    fun setTipoUsuarioCached(v: String) = prefs.edit().putString("tipo_usuario_cache", v).apply()

    // Cache del claim `admin` del ID token, para decidir startDestination al
    // arrancar sin esperar la llamada async a getIdToken().
    fun getEsAdminCached(): Boolean = prefs.getBoolean("es_admin_cache", false)
    fun setEsAdminCached(v: Boolean) = prefs.edit().putBoolean("es_admin_cache", v).apply()
}
