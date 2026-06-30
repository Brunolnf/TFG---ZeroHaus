package com.example.zerohaus

import android.util.Log
import com.example.zerohaus.Util.AppPreferencias
import com.example.zerohaus.Util.NotificacionesLocales
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ServicioNotificaciones : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "onNewToken: token nuevo recibido")
        guardarToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val prefs = AppPreferencias(this)
        if (!prefs.getNotificacionesPush()) {
            Log.i(TAG, "onMessageReceived: push desactivado por el usuario")
            return
        }

        val titulo = message.notification?.title ?: message.data["titulo"] ?: "ZeroHaus"
        val cuerpo  = message.notification?.body  ?: message.data["detalle"] ?: ""
        val tipo    = message.data["tipo"] ?: "general"
        NotificacionesLocales.mostrar(this, titulo, cuerpo, tipo, conSonido = prefs.getNotificacionesSonido())
    }

    private fun guardarToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.w(TAG, "guardarToken: no hay usuario logueado, token no se persiste")
            return
        }
        // /ajustes/{uid} esta cerrado a esMio || esAdmin. Asi el tokenFCM no
        // viaja en /usuarios (que tiene lectura abierta a logueados para los
        // nombres de los chats), evitando harvesting de tokens cross-user.
        FirebaseFirestore.getInstance()
            .collection("ajustes").document(uid)
            .set(mapOf("tokenFCM" to token), SetOptions.merge())
            .addOnSuccessListener { Log.i(TAG, "tokenFCM guardado en /ajustes/$uid") }
            .addOnFailureListener { e -> Log.e(TAG, "Fallo al guardar tokenFCM: ${e.message}") }
    }

    companion object {
        private const val TAG = "ServicioNotificaciones"

        /**
         * Pide el token FCM actual y lo persiste en `/ajustes/{uid}.tokenFCM`.
         * Vive en /ajustes (cerrado a esMio||esAdmin) en lugar de /usuarios
         * (lectura abierta), para no exponer tokens FCM ajenos a cualquier
         * logueado.
         */
        fun registrarToken() {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
                Log.i(TAG, "registrarToken: sin usuario logueado, omitido")
                return
            }
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (token.isNullOrBlank()) {
                        Log.w(TAG, "registrarToken: FCM devolvio un token vacio")
                        return@addOnSuccessListener
                    }
                    FirebaseFirestore.getInstance()
                        .collection("ajustes").document(uid)
                        .set(mapOf("tokenFCM" to token), SetOptions.merge())
                        .addOnSuccessListener { Log.i(TAG, "tokenFCM registrado para $uid") }
                        .addOnFailureListener { e -> Log.e(TAG, "Fallo guardando tokenFCM: ${e.message}") }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Fallo obteniendo token FCM: ${e.message}")
                }
        }
    }
}
