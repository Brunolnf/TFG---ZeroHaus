package com.example.zerohaus.util

import android.content.Context

class AppPreferencias(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("zerohaus_prefs", Context.MODE_PRIVATE)

    fun getTema(): String = prefs.getString("tema", "Sistema") ?: "Sistema"
    fun setTema(v: String) = prefs.edit().putString("tema", v).apply()

    fun getIdioma(): String = prefs.getString("idioma", "Español") ?: "Español"
    fun setIdioma(v: String) = prefs.edit().putString("idioma", v).apply()
}
