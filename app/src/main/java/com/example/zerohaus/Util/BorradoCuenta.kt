package com.example.zerohaus.Util

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
}
