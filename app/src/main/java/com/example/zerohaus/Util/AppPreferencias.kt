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
}
