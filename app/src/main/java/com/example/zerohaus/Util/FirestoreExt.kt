package com.example.zerohaus.Util

import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source

/**
 * Lecturas de Firestore acotadas en el tiempo.
 *
 * Problema que resuelven: un `get()` normal espera al servidor (y al token de
 * App Check). Si esa negociación se cuelga, el callback NO llega nunca y el
 * spinner de la pantalla gira para siempre.
 *
 * `getOrTimeout` garantiza que el callback se invoca **exactamente una vez** y
 * como muy tarde a los [timeoutMs]:
 *  - si el servidor responde a tiempo → datos del servidor,
 *  - si se cuelga → al saltar el temporizador intentamos la caché local,
 *  - si tampoco hay caché → `null` (la pantalla muestra estado vacío, no spinner).
 *
 * El callback único es importante: ViewModels con contadores de queries en
 * paralelo se romperían si el callback se disparara dos veces.
 */
private const val DEFAULT_TIMEOUT_MS = 8000L

fun Query.getOrTimeout(timeoutMs: Long = DEFAULT_TIMEOUT_MS, callback: (QuerySnapshot?) -> Unit) {
    val handler = Handler(Looper.getMainLooper())
    var done = false
    fun finish(result: QuerySnapshot?) {
        if (done) return
        done = true
        handler.removeCallbacksAndMessages(null)
        callback(result)
    }
    handler.postDelayed({
        get(Source.CACHE)
            .addOnSuccessListener { finish(it) }
            .addOnFailureListener { finish(null) }
    }, timeoutMs)
    get()
        .addOnSuccessListener { finish(it) }
        .addOnFailureListener { finish(null) }
}

fun DocumentReference.getOrTimeout(timeoutMs: Long = DEFAULT_TIMEOUT_MS, callback: (DocumentSnapshot?) -> Unit) {
    val handler = Handler(Looper.getMainLooper())
    var done = false
    fun finish(result: DocumentSnapshot?) {
        if (done) return
        done = true
        handler.removeCallbacksAndMessages(null)
        callback(result)
    }
    handler.postDelayed({
        get(Source.CACHE)
            .addOnSuccessListener { finish(it) }
            .addOnFailureListener { finish(null) }
    }, timeoutMs)
    get()
        .addOnSuccessListener { finish(it) }
        .addOnFailureListener { finish(null) }
}
