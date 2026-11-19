package com.example.zerohaus.Util

import com.google.firebase.functions.FirebaseFunctionsException

/**
 * Interpretación pura de la respuesta de `eliminar_mi_cuenta`, que cancela la
 * renovación en Google Play antes de borrar la cuenta.
 */
object BorradoCuenta {
    /** Ficha de suscripciones de Google Play, para cancelar a mano. */
    const val URL_SUSCRIPCIONES_PLAY =
        "https://play.google.com/store/account/subscriptions?package=es.zerohaus.app"

    /**
     * `true` si el servidor borró la cuenta pero alguna suscripción quedó sin
     * cancelar en Play (`cancelarEnPlay: true`): el usuario debe cancelarla a mano.
     */
    fun requiereCancelarEnPlay(respuesta: Any?): Boolean =
        (respuesta as? Map<*, *>)?.get("cancelarEnPlay") == true

    /**
     * `true` si el error es el fallo pasajero de Play (`UNAVAILABLE` con
     * `motivo: "play"`): no se ha borrado nada y se puede reintentar.
     */
    fun esFalloTransitorioDePlay(codigo: String?, detalles: Any?): Boolean =
        codigo == "UNAVAILABLE" && (detalles as? Map<*, *>)?.get("motivo") == "play"

    /**
     * `true` si el servidor no borra porque hay un pago en curso
     * (`FAILED_PRECONDITION` con `motivo: "pagos"`): hay que terminarlo o cancelarlo.
     */
    fun esBloqueoPorPagos(codigo: String?, detalles: Any?): Boolean =
        codigo == "FAILED_PRECONDITION" && (detalles as? Map<*, *>)?.get("motivo") == "pagos"

    enum class TipoError { RED, PLAY, PAGOS, OTRO }

    /** Clasifica el fallo de `eliminar_mi_cuenta` para elegir el mensaje a mostrar. */
    fun clasificarError(e: Exception): TipoError {
        val fe = e as? FirebaseFunctionsException
        return clasificar(e.esFalloDeRed(), fe?.code?.name, fe?.details)
    }

    /** Parte pura de [clasificarError]: la red tiene prioridad sobre el fallo de Play. */
    fun clasificar(esFalloDeRed: Boolean, codigo: String?, detalles: Any?): TipoError = when {
        esFalloDeRed -> TipoError.RED
        esFalloTransitorioDePlay(codigo, detalles) -> TipoError.PLAY
        esBloqueoPorPagos(codigo, detalles) -> TipoError.PAGOS
        else -> TipoError.OTRO
    }
}
