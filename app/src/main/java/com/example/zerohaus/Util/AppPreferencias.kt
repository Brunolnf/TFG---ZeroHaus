package com.example.zerohaus.Util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Preferencias locales: idioma, tema, unidades, notificaciones y cachés de
 * sesión. Una sola instancia para toda la app ([de]).
 *
 * Antes se guardaban con EncryptedSharedPreferences (obsoleto desde
 * security-crypto 1.1.0), que además se creaba de nuevo en cada uso con
 * operaciones del Keystore en el hilo principal. Nada de lo que se guarda es
 * secreto (la sesión la guarda Firebase Auth), así que ahora son unas
 * SharedPreferences normales, privadas de la app y excluidas de las copias
 * (ver backup_rules / data_extraction_rules). La primera vez se copian los
 * valores del fichero cifrado antiguo para que nadie pierda sus ajustes.
 */
class AppPreferencias private constructor(private val prefs: SharedPreferences) {

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

    // Permiso de ubicación del directorio: se pide solo una vez sin que lo pulse
    fun getUbicacionPedida(): Boolean = prefs.getBoolean("ubicacion_pedida", false)
    fun setUbicacionPedida(v: Boolean) = prefs.edit().putBoolean("ubicacion_pedida", v).apply()

    // Valoración in-app: solo la pedimos una vez y tras varias interacciones.
    fun getResenaPedida(): Boolean = prefs.getBoolean("resena_pedida", false)
    fun setResenaPedida(v: Boolean) = prefs.edit().putBoolean("resena_pedida", v).apply()
    fun incrementarInteracciones(): Int {
        val n = prefs.getInt("interacciones", 0) + 1
        prefs.edit().putInt("interacciones", n).apply()
        return n
    }

    companion object {
        private const val ARCHIVO = "zerohaus_preferencias"
        private const val MIGRADO = "_migrado_de_cifradas"
        // Ficheros de versiones anteriores: el cifrado y su respaldo sin cifrar
        private const val ARCHIVO_CIFRADO = "zerohaus_secure_prefs"
        private const val ARCHIVO_RESPALDO = "zerohaus_prefs"

        @Volatile private var instancia: AppPreferencias? = null

        /** Las preferencias de la app (se crean y, si hace falta, se migran una sola vez). */
        fun de(ctx: Context): AppPreferencias =
            instancia ?: synchronized(this) {
                instancia ?: AppPreferencias(abrir(ctx.applicationContext)).also { instancia = it }
            }

        private fun abrir(ctx: Context): SharedPreferences {
            val prefs = ctx.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(MIGRADO, false)) migrar(ctx, prefs)
            return prefs
        }

        /** Copia los valores de los ficheros antiguos y los borra. */
        @Suppress("DEPRECATION")
        private fun migrar(ctx: Context, destino: SharedPreferences) {
            val editor = destino.edit()
            copiar(ctx.getSharedPreferences(ARCHIVO_RESPALDO, Context.MODE_PRIVATE).all, editor)
            if (File(ctx.applicationInfo.dataDir, "shared_prefs/$ARCHIVO_CIFRADO.xml").exists()) {
                runCatching {
                    val clave = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                    EncryptedSharedPreferences.create(
                        ctx, ARCHIVO_CIFRADO, clave,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    ).all
                }.getOrNull()?.let { copiar(it, editor) }
            }
            editor.putBoolean(MIGRADO, true).commit()
            ctx.deleteSharedPreferences(ARCHIVO_CIFRADO)
            ctx.deleteSharedPreferences(ARCHIVO_RESPALDO)
        }

        private fun copiar(valores: Map<String, *>, editor: SharedPreferences.Editor) {
            valores.forEach { (clave, valor) ->
                when (valor) {
                    is String -> editor.putString(clave, valor)
                    is Boolean -> editor.putBoolean(clave, valor)
                    is Int -> editor.putInt(clave, valor)
                    is Long -> editor.putLong(clave, valor)
                    is Float -> editor.putFloat(clave, valor)
                }
            }
        }
    }
}
