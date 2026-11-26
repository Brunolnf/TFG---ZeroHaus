package com.example.zerohaus.Util

import android.os.Handler
import android.os.Looper

/** Programa [accion] dentro de `ms` milisegundos y devuelve cómo cancelarla. */
typealias Programador = (ms: Long, accion: () -> Unit) -> (() -> Unit)

/** Programador real: hilo principal. En los tests se sustituye por uno manual. */
fun programadorPrincipal(): Programador = { ms, accion ->
    val handler = Handler(Looper.getMainLooper())
    handler.postDelayed(accion, ms)
    val cancelar: () -> Unit = { handler.removeCallbacks(accion) }
    cancelar
}

/**
 * Lectura con tope de espera que llama a [callback] EXACTAMENTE una vez: con la
 * respuesta de [servidor] si llega a tiempo o, al saltar el tope, con la de [cache].
 * Lo que llegue después se ignora.
 */
fun <T> leerConTope(
    timeoutMs: Long,
    programar: Programador,
    servidor: ((Lectura<T>) -> Unit) -> Unit,
    cache: ((Lectura<T>) -> Unit) -> Unit,
    callback: (Lectura<T>) -> Unit
) {
    var hecho = false
    var cancelarTope: (() -> Unit)? = null
    fun terminar(l: Lectura<T>) {
        if (hecho) return
        hecho = true
        cancelarTope?.invoke()
        callback(l)
    }
    cancelarTope = programar(timeoutMs) { cache { terminar(it) } }
    servidor { terminar(it) }
}

/** Cómo acaba un guardado que puede quedarse esperando al servidor. */
sealed interface EstadoGuardado {
    /** El servidor confirmó el guardado. */
    data object Confirmado : EstadoGuardado
    /** Pasó el tope sin respuesta: el guardado sigue en la cola de Firestore. */
    data object PendienteDeConfirmar : EstadoGuardado
    data class Fallo(val causa: Throwable) : EstadoGuardado
}

/**
 * Espera el resultado de [commit] hasta [topeMs]. [alResultado] se llama una sola
 * vez (confirmado, fallo, o pendiente al saltar el tope). Si pasado el tope el
 * commit acaba FALLANDO, [alFalloTardio] avisa: el usuario ya vio el informe como
 * «pendiente» y el servidor lo ha rechazado.
 */
fun guardarConTope(
    topeMs: Long,
    programar: Programador,
    commit: ((Result<Unit>) -> Unit) -> Unit,
    alResultado: (EstadoGuardado) -> Unit,
    alFalloTardio: (Throwable) -> Unit
) {
    var hecho = false
    var cancelarTope: (() -> Unit)? = null
    cancelarTope = programar(topeMs) {
        if (!hecho) {
            hecho = true
            alResultado(EstadoGuardado.PendienteDeConfirmar)
        }
    }
    commit { r ->
        if (!hecho) {
            hecho = true
            cancelarTope?.invoke()
            alResultado(r.fold({ EstadoGuardado.Confirmado }, { EstadoGuardado.Fallo(it) }))
        } else {
            r.onFailure(alFalloTardio)
        }
    }
}
