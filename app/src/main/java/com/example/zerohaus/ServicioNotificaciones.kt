package com.example.zerohaus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ServicioNotificaciones : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        guardarToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val titulo = message.notification?.title ?: message.data["titulo"] ?: "ZeroHaus"
        val cuerpo = message.notification?.body ?: message.data["detalle"] ?: ""
        mostrarNotificacion(titulo, cuerpo)
    }

    private fun mostrarNotificacion(titulo: String, cuerpo: String) {
        val channelId = "zerohaus_general"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                channelId,
                "ZeroHaus Notificaciones",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(canal)
        }

        val notificacion = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notificacion)
    }

    private fun guardarToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("usuarios")
            .document(uid)
            .update("tokenFCM", token)
    }

    companion object {
        fun registrarToken() {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnSuccessListener
                    FirebaseFirestore.getInstance()
                        .collection("usuarios")
                        .document(uid)
                        .update("tokenFCM", token)
                }
        }
    }
}