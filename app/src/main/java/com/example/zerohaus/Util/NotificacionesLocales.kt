package com.example.zerohaus.Util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.zerohaus.MainActivity
import com.example.zerohaus.R

/**
 * Canales de notificación de Android y notificaciones locales (las que se
 * muestran con la app abierta), con deduplicación y modo silencioso.
 */
object NotificacionesLocales {

    // v2: nuevos IDs para forzar recreación con sonido configurado
    private const val CANAL_CHAT      = "zerohaus_chat_v2"
    private const val CANAL_GENERAL   = "zerohaus_general_v2"
    // Sin sonido: lo usa el servidor (y la app) cuando en Ajustes se desactiva
    // el sonido. En Android 8+ el sonido va por canal, no por notificación.
    // Mismo id que CANAL_SILENCIO en functions/main.py.
    private const val CANAL_SILENCIO  = "zerohaus_silencio_v1"

    private var appContext: Context? = null

    // Deduplicación: si la misma notificación (titulo+cuerpo+tipo) llega varias
    // veces en una ventana corta, mostramos solo la primera. Cubre los duplicados
    // típicos (Cloud Function disparándose dos veces, push FCM repetido por el
    // servidor, retries del transporte, etc.) sin tener que tocar el backend.
    private const val VENTANA_DEDUP_MS = 3000L
    private var ultimaKey: String? = null
    private var ultimaKeyMs: Long = 0L

    private fun esDuplicado(titulo: String, cuerpo: String, tipo: String): Boolean {
        val key = "$titulo|$cuerpo|$tipo"
        val ahora = System.currentTimeMillis()
        val esRepe = key == ultimaKey && (ahora - ultimaKeyMs) < VENTANA_DEDUP_MS
        ultimaKey = key
        ultimaKeyMs = ahora
        return esRepe
    }

    fun crearCanales(context: Context) {
        appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val sonidoUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val vibracion = longArrayOf(0, 250, 100, 250)

            listOf(
                NotificationChannel(CANAL_CHAT, "Mensajes", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Nuevos mensajes de técnicos y clientes"
                    enableVibration(true)
                    vibrationPattern = vibracion
                    setSound(sonidoUri, audioAttr)
                    enableLights(true)
                },
                NotificationChannel(CANAL_SILENCIO, "Silenciosas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notificaciones sin sonido (sonido desactivado en Ajustes)"
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(CANAL_GENERAL, "General", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notificaciones generales de ZeroHaus"
                    setSound(sonidoUri, audioAttr)
                }
            ).forEach { manager.createNotificationChannel(it) }

            // Eliminar canales de versiones anteriores (presupuestos, proyectos y
            // contactos, de modelos de negocio ya retirados)
            listOf(
                "zerohaus_chat", "zerohaus_presupuesto", "zerohaus_proyecto", "zerohaus_general",
                "zerohaus_presupuesto_v2", "zerohaus_proyecto_v2", "zerohaus_contactos_v1"
            ).forEach { manager.deleteNotificationChannel(it) }
        }
    }

    fun mostrar(titulo: String, cuerpo: String, tipo: String = "general", conSonido: Boolean = true) {
        val ctx = appContext ?: return
        mostrar(ctx, titulo, cuerpo, tipo, conSonido)
    }

    fun mostrar(context: Context, titulo: String, cuerpo: String, tipo: String = "general", conSonido: Boolean = true) {
        if (esDuplicado(titulo, cuerpo, tipo)) return
        val canalId = when {
            !conSonido                          -> CANAL_SILENCIO
            tipo == "chat" || tipo == "mensaje" -> CANAL_CHAT
            else                                -> CANAL_GENERAL
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sonidoUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notif = NotificationCompat.Builder(context, canalId)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 250, 100, 250))
            .apply {
                if (conSonido) setSound(sonidoUri)
                else setSilent(true)
            }
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), notif)

        // Vibrar también cuando la app está en primer plano
        if (conSonido) vibrar(context)
    }

    private fun vibrar(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 100, 250), -1))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 100, 250), -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(longArrayOf(0, 250, 100, 250), -1)
                }
            }
        } catch (_: Exception) {}
    }
}
