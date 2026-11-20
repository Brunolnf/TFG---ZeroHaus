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

/**
 * Lectura con plazo máximo: llama al callback EXACTAMENTE una vez, con la
 * respuesta del servidor o, si no llega a tiempo, con la caché local (o null).
 * Evita spinners infinitos cuando la red o App Check se quedan colgados.
 */
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

/**
 * Resultado de una lectura que distingue de dónde vienen los datos, para no
 * confundir «no hay nada» con «no se ha podido leer».
 */
sealed interface Lectura<out T> {
    /** Datos confirmados por el servidor. */
    data class Servidor<T>(val datos: T) : Lectura<T>
    /** Datos de la caché del móvil: el servidor no respondió a tiempo o no hay red. */
    data class Guardada<T>(val datos: T) : Lectura<T>
    /** No se pudo leer ni del servidor ni de la caché. */
    data class Fallo(val causa: Throwable? = null) : Lectura<Nothing>
}

/**
 * Clasifica el resultado de una lectura. Una caché VACÍA no prueba que no haya
 * datos (puede que simplemente no se haya sincronizado nunca), así que cuenta
 * como fallo y no como «sin resultados».
 */
fun <T> clasificarLectura(datos: T, vacio: Boolean, deCache: Boolean): Lectura<T> = when {
    !deCache -> Lectura.Servidor(datos)
    vacio -> Lectura.Fallo()
    else -> Lectura.Guardada(datos)
}

fun <T, R> Lectura<T>.mapear(f: (T) -> R): Lectura<R> = when (this) {
    is Lectura.Servidor -> Lectura.Servidor(f(datos))
    is Lectura.Guardada -> Lectura.Guardada(f(datos))
    is Lectura.Fallo -> this
}

/**
 * Igual que [getOrTimeout] (callback exactamente una vez, con tope de espera), pero
 * indicando el origen de los datos o el error en vez de convertirlo en `null`.
 */
fun Query.getConOrigen(timeoutMs: Long = DEFAULT_TIMEOUT_MS, callback: (Lectura<QuerySnapshot>) -> Unit) {
    val handler = Handler(Looper.getMainLooper())
    var done = false
    fun finish(result: Lectura<QuerySnapshot>) {
        if (done) return
        done = true
        handler.removeCallbacksAndMessages(null)
        callback(result)
    }
    fun clasificar(snap: QuerySnapshot) = clasificarLectura(snap, snap.isEmpty, snap.metadata.isFromCache)
    handler.postDelayed({
        get(Source.CACHE)
            .addOnSuccessListener { finish(clasificarLectura(it, it.isEmpty, deCache = true)) }
            .addOnFailureListener { finish(Lectura.Fallo(it)) }
    }, timeoutMs)
    get()
        .addOnSuccessListener { finish(clasificar(it)) }
        .addOnFailureListener { finish(Lectura.Fallo(it)) }
}
