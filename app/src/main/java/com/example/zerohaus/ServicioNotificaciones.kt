package com.example.zerohaus

import com.example.zerohaus.util.NotificacionesLocales
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
        val cuerpo  = message.notification?.body  ?: message.data["detalle"] ?: ""
        val tipo    = message.data["tipo"] ?: "general"
        NotificacionesLocales.mostrar(this, titulo, cuerpo, tipo)
    }

    private fun guardarToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("usuarios").document(uid)
            .update("tokenFCM", token)
    }

    companion object {
        fun registrarToken() {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnSuccessListener
                    FirebaseFirestore.getInstance()
                        .collection("usuarios").document(uid)
                        .update("tokenFCM", token)
                }
        }
    }
}
