package com.example.zerohaus.Repositorios

import com.example.zerohaus.Util.esFalloDeRed
import com.example.zerohaus.Util.Diagnostico
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException

/** Por qué falló un envío o una comprobación, para mostrarlo traducido. */
sealed class ErrorVerificacion {
    data class Incorrecto(val intentosRestantes: Int) : ErrorVerificacion()
    object Caducado : ErrorVerificacion()
    object DemasiadosIntentos : ErrorVerificacion()
    object LimiteEnvios : ErrorVerificacion()
    object NoEnviado : ErrorVerificacion()
    object SinConexion : ErrorVerificacion()
    // El servidor respondió con un error inesperado (o la función no existe)
    object Servicio : ErrorVerificacion()
}

class VerificacionException(val error: ErrorVerificacion) : Exception(error.toString())

/** Resultado de pedir un código: ya verificado, enviado, o hay que esperar. */
data class EnvioCodigo(val yaVerificado: Boolean, val enviado: Boolean, val esperaSeg: Int)

/**
 * Verificación del email con código de 6 dígitos. El código lo genera, envía
 * y comprueba el servidor (Cloud Functions `enviar_codigo_verificacion` y
 * `verificar_codigo_email`); la app nunca lo conoce.
 */
class RepositorioVerificacion {

    private val auth = FirebaseAuth.getInstance()
    private val functions = FirebaseFunctions.getInstance("europe-west1")

    val emailActual: String get() = auth.currentUser?.email.orEmpty()

    fun enviarCodigo(idioma: String, callback: (Result<EnvioCodigo>) -> Unit) {
        functions.getHttpsCallable("enviar_codigo_verificacion")
            .call(hashMapOf("idioma" to idioma))
            .addOnSuccessListener { r ->
                val m = r.getData() as? Map<*, *> ?: emptyMap<String, Any>()
                callback(Result.success(EnvioCodigo(
                    yaVerificado = m["verificado"] == true,
                    enviado = m["enviado"] == true,
                    esperaSeg = (m["esperaSeg"] as? Number)?.toInt() ?: 0
                )))
            }
            .addOnFailureListener { callback(Result.failure(VerificacionException(traducir("enviar_codigo_verificacion", it)))) }
    }

    /** Comprueba el código y, si es correcto, refresca la sesión para que el
     *  token ya lleve email_verified=true (lo exigen las reglas de Firestore). */
    fun verificarCodigo(codigo: String, callback: (Result<Unit>) -> Unit) {
        functions.getHttpsCallable("verificar_codigo_email")
            .call(hashMapOf("codigo" to codigo))
            .addOnSuccessListener { refrescarSesion { ok ->
                callback(if (ok) Result.success(Unit) else Result.failure(VerificacionException(ErrorVerificacion.SinConexion)))
            } }
            .addOnFailureListener { callback(Result.failure(VerificacionException(traducir("verificar_codigo_email", it)))) }
    }

    /** Recarga el usuario y fuerza un token nuevo. Devuelve si el email ya está verificado. */
    fun refrescarSesion(callback: (Boolean) -> Unit) {
        val user = auth.currentUser ?: run { callback(false); return }
        user.reload()
            .continueWithTask { user.getIdToken(true) }
            .addOnSuccessListener { callback(auth.currentUser?.isEmailVerified == true) }
            .addOnFailureListener { callback(auth.currentUser?.isEmailVerified == true) }
    }

    private fun traducir(funcion: String, e: Exception): ErrorVerificacion {
        if (e.esFalloDeRed()) return ErrorVerificacion.SinConexion
        val error = traducirRespuesta(e)
        // Código incorrecto, caducado o límites son uso normal; el resto, un fallo
        if (error == ErrorVerificacion.Servicio || error == ErrorVerificacion.NoEnviado) {
            Diagnostico.errorDeFuncion(funcion, e)
        }
        return error
    }

    private fun traducirRespuesta(e: Exception): ErrorVerificacion {
        val fe = e as? FirebaseFunctionsException ?: return ErrorVerificacion.Servicio
        val detalles = fe.details as? Map<*, *>
        return when (fe.code) {
            FirebaseFunctionsException.Code.INVALID_ARGUMENT -> {
                val restantes = (detalles?.get("restantes") as? Number)?.toInt()
                ErrorVerificacion.Incorrecto(restantes ?: 0)
            }
            FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                if (detalles?.get("motivo") == "caducado") ErrorVerificacion.Caducado
                else ErrorVerificacion.Servicio
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                if (detalles?.get("motivo") == "envios") ErrorVerificacion.LimiteEnvios
                else ErrorVerificacion.DemasiadosIntentos
            FirebaseFunctionsException.Code.UNAVAILABLE -> ErrorVerificacion.NoEnviado
            else -> ErrorVerificacion.Servicio
        }
    }
}
