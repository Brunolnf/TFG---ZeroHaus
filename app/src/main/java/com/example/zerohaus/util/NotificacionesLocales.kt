package com.example.zerohaus.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.zerohaus.MainActivity
import com.example.zerohaus.R

object NotificacionesLocales {

    private const val CANAL_CHAT = "zerohaus_chat"
    private const val CANAL_PRESUPUESTO = "zerohaus_presupuesto"
    private const val CANAL_GENERAL = "zerohaus_general"
    private const val CANAL_PROYECTO = "zerohaus_proyecto"

    private var appContext: Context? = null

    fun crearCanales(context: Context) {
        appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            listOf(
                NotificationChannel(CANAL_CHAT, "Mensajes", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Nuevos mensajes de técnicos"
                    enableVibration(true)
                },
                NotificationChannel(CANAL_PRESUPUESTO, "Presupuestos", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Actualizaciones de presupuestos y solicitudes"
                    enableVibration(true)
                },
                NotificationChannel(CANAL_PROYECTO, "Proyectos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Actualizaciones de tus proyectos de reforma"
                },
                NotificationChannel(CANAL_GENERAL, "General", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notificaciones generales de ZeroHaus"
                }
            ).forEach { manager.createNotificationChannel(it) }
        }
    }

    // Versión sin Context — usa el appContext guardado en crearCanales
    fun mostrar(titulo: String, cuerpo: String, tipo: String = "general") {
        val ctx = appContext ?: return
        mostrar(ctx, titulo, cuerpo, tipo)
    }

    fun mostrar(context: Context, titulo: String, cuerpo: String, tipo: String = "general") {
        val canalId = when (tipo) {
            "chat"        -> CANAL_CHAT
            "presupuesto" -> CANAL_PRESUPUESTO
            "proyecto"    -> CANAL_PROYECTO
            else          -> CANAL_GENERAL
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, canalId)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), notif)
    }
}
