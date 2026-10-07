package com.example.zerohaus

import android.util.Log
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.AppPreferencias
import com.example.zerohaus.Util.NotificacionesLocales
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Servicio de Firebase Cloud Messaging. Guarda en `/ajustes/{uid}` cómo
 * enviar avisos a este móvil y muestra los push que llegan con la app en
 * primer plano (en segundo plano los muestra Android con el canal que elige
 * el servidor).
 *
 * FCM pasa de los tokens de registro al ID de instalación (FID): desde
 * firebase-messaging 25.1 `getToken`, `deleteToken` y `onNewToken` están
 * obsoletos en favor de `register`, `unregister` y `onRegistered`. Durante la
 * transición se guardan los dos (`fidFCM` y `tokenFCM`): el servidor envía al
 * FID y, si falla, al token, y así las versiones anteriores de la app (que
 * solo guardan el token) siguen recibiendo avisos.
 */
class ServicioNotificaciones : FirebaseMessagingService() {

    override fun onRegistered(fid: String) {
        super.onRegistered(fid)
        Log.i(TAG, "onRegistered: app registrada en FCM")
        guardarEnAjustes(CAMPO_FID, fid)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "onNewToken: token nuevo recibido")
        guardarEnAjustes(CAMPO_TOKEN, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val prefs = AppPreferencias.de(this)
        if (!prefs.getNotificacionesPush()) {
            Log.i(TAG, "onMessageReceived: push desactivado por el usuario")
            return
        }

        val titulo = message.notification?.title ?: message.data["titulo"] ?: "ZeroHaus"
        val cuerpo  = message.notification?.body  ?: message.data["detalle"] ?: ""
        val tipo    = message.data["tipo"] ?: "general"
        val chatId  = message.data["chatId"]
        // Mensaje del chat que se está viendo: ya lo tiene delante
        if (chatId != null && chatId == AppEstado.chatAbiertoId) return
        NotificacionesLocales.mostrar(this, titulo, cuerpo, tipo, conSonido = prefs.getNotificacionesSonido(), chatId = chatId)
    }

    companion object {
        private const val TAG = "ServicioNotificaciones"
        private const val CAMPO_FID = "fidFCM"
        private const val CAMPO_TOKEN = "tokenFCM"

        /**
         * Guarda un dato de FCM en `/ajustes/{uid}` (cerrado al propio usuario y
         * al admin: no viaja en perfiles que vean otros).
         */
        private fun guardarEnAjustes(campo: String, valor: String) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
                Log.i(TAG, "Sin usuario: $campo no se guarda")
                return
            }
            if (valor.isBlank()) return
            FirebaseFirestore.getInstance()
                .collection("ajustes").document(uid)
                .set(mapOf(campo to valor), SetOptions.merge())
                .addOnFailureListener { e -> Log.e(TAG, "Fallo guardando $campo: ${e.message}") }
        }

        /**
         * Registra este móvil en FCM y guarda su FID (y, durante la
         * transición, el token antiguo) para la cuenta actual. Se llama al
         * arrancar y al iniciar sesión.
         */
        fun registrarToken() {
            if (FirebaseAuth.getInstance().currentUser == null) {
                Log.i(TAG, "registrarToken: sin usuario logueado, omitido")
                return
            }
            // El FID se guarda aquí y no solo en onRegistered: si otra cuenta
            // inicia sesión en el mismo móvil, onRegistered puede no volver a
            // llamarse y la cuenta nueva se quedaría sin avisos.
            FirebaseMessaging.getInstance().register()
                .addOnSuccessListener {
                    FirebaseInstallations.getInstance().id
                        .addOnSuccessListener { fid -> guardarEnAjustes(CAMPO_FID, fid) }
                }
                .addOnFailureListener { e -> Log.e(TAG, "Fallo registrando en FCM: ${e.message}") }
            @Suppress("DEPRECATION")
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> guardarEnAjustes(CAMPO_TOKEN, token.orEmpty()) }
        }

        /**
         * Al cerrar sesión: quita de `/ajustes` cómo avisar a este móvil y lo da
         * de baja en FCM. Si no, el móvil seguía recibiendo los avisos (nombre y
         * texto de los mensajes) de esa cuenta, y con otra cuenta iniciada
         * recibía los de las dos. Al volver a entrar, [registrarToken] lo
         * vuelve a dar de alta.
         */
        fun olvidarToken() {
            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                FirebaseFirestore.getInstance()
                    .collection("ajustes").document(uid)
                    .update(mapOf(CAMPO_FID to FieldValue.delete(), CAMPO_TOKEN to FieldValue.delete()))
            }
            FirebaseMessaging.getInstance().unregister()
                .addOnFailureListener { e -> Log.w(TAG, "No se pudo dar de baja en FCM: ${e.message}") }
            // Durante la transición también se invalida el token antiguo, que el
            // servidor usa como respaldo
            @Suppress("DEPRECATION")
            FirebaseMessaging.getInstance().deleteToken()
        }
    }
}
