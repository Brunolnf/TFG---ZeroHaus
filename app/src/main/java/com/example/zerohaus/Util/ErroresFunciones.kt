package com.example.zerohaus.Util

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException

/**
 * True si una llamada a una Cloud Function falló por la red (sin conexión o
 * sin respuesta a tiempo) y no por el servidor.
 *
 * El SDK de Functions envuelve los fallos de red en un
 * FirebaseFunctionsException con código INTERNAL (o DEADLINE_EXCEEDED si se
 * agota el tiempo) cuya causa es la IOException original. Mirar solo el
 * código confundiría un error del servidor, o una función aún no desplegada
 * (NOT_FOUND), con "sin conexión".
 */
fun Exception.esFalloDeRed(): Boolean = when (this) {
    is FirebaseNetworkException, is IOException -> true
    is FirebaseFunctionsException ->
        code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED || cause is IOException
    else -> false
}
