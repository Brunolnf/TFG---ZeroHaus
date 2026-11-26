package com.example.zerohaus

import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Repositorios.cambiosDeChat
import com.example.zerohaus.Util.EstadoGuardado
import com.example.zerohaus.Util.Lectura
import com.example.zerohaus.Util.ListadoInformes
import com.example.zerohaus.Util.Programador
import com.example.zerohaus.Util.conservandoAnterior
import com.example.zerohaus.Util.guardarConTope
import com.example.zerohaus.Util.leerConTope
import com.example.zerohaus.ViewModel.SuscripcionEstado
import com.example.zerohaus.ViewModel.alExitoActivacion
import com.example.zerohaus.ViewModel.alFalloActivacion
import com.example.zerohaus.ViewModel.alTopeActivacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reloj manual: guarda lo programado y permite dispararlo o ver si se canceló. */
private class RelojManual {
    var accion: (() -> Unit)? = null
    var cancelado = false
    var msProgramados = -1L
    val programador: Programador = { ms, a ->
        msProgramados = ms
        accion = a
        ({ cancelado = true })
    }
    fun saltarTope() { if (!cancelado) accion?.invoke() }
}

class TopesYRevisionTest {

    // ---- getConOrigen (leerConTope)

    @Test fun lecturaDelServidorATiempoCancelaElTopeYNoLeeLaCache() {
        val reloj = RelojManual()
        var servidor: ((Lectura<Int>) -> Unit)? = null
        var cacheLeida = false
        val resultados = mutableListOf<Lectura<Int>>()
        leerConTope(8000, reloj.programador, { servidor = it }, { cacheLeida = true }, { resultados += it })

        servidor!!(Lectura.Servidor(1))
        reloj.saltarTope()
        assertEquals(listOf<Lectura<Int>>(Lectura.Servidor(1)), resultados)
        assertTrue(reloj.cancelado)
        assertFalse(cacheLeida)
        assertEquals(8000L, reloj.msProgramados)
    }

    @Test fun alSaltarElTopeDevuelveLaCacheYIgnoraAlServidorTardio() {
        val reloj = RelojManual()
        var servidor: ((Lectura<Int>) -> Unit)? = null
        val resultados = mutableListOf<Lectura<Int>>()
        leerConTope<Int>(8000, reloj.programador, { servidor = it }, { it(Lectura.Guardada(7)) }, { resultados += it })

        reloj.saltarTope()
        servidor!!(Lectura.Servidor(9)) // llega tarde
        assertEquals(listOf<Lectura<Int>>(Lectura.Guardada(7)), resultados)
    }

    @Test fun elTopeSinCacheDevuelveFallo() {
        val reloj = RelojManual()
        val resultados = mutableListOf<Lectura<Int>>()
        leerConTope<Int>(1, reloj.programador, { }, { it(Lectura.Fallo()) }, { resultados += it })
        reloj.saltarTope()
        assertEquals(listOf<Lectura<Int>>(Lectura.Fallo()), resultados)
    }

    @Test fun elCallbackSeLlamaUnaSolaVezAunqueRespondanLosDos() {
        val reloj = RelojManual()
        var servidor: ((Lectura<Int>) -> Unit)? = null
        var n = 0
        leerConTope<Int>(1, reloj.programador, { servidor = it }, { it(Lectura.Guardada(1)) }, { n++ })
        reloj.saltarTope()
        servidor!!(Lectura.Servidor(2))
        servidor!!(Lectura.Fallo())
        assertEquals(1, n)
    }

    // ---- guardarViviendaEInforme (guardarConTope)

    @Test fun guardadoConfirmadoATiempo() {
        val reloj = RelojManual()
        var fin: ((Result<Unit>) -> Unit)? = null
        val res = mutableListOf<EstadoGuardado>()
        guardarConTope(8000, reloj.programador, { fin = it }, { res += it }, { })
        fin!!(Result.success(Unit))
        reloj.saltarTope()
        assertEquals(listOf<EstadoGuardado>(EstadoGuardado.Confirmado), res)
    }

    @Test fun sinRespuestaAlTopeQuedaPendienteYElFalloTardioAvisa() {
        val reloj = RelojManual()
        var fin: ((Result<Unit>) -> Unit)? = null
        val res = mutableListOf<EstadoGuardado>()
        val tardios = mutableListOf<Throwable>()
        guardarConTope(8000, reloj.programador, { fin = it }, { res += it }, { tardios += it })
        reloj.saltarTope()
        assertEquals(listOf<EstadoGuardado>(EstadoGuardado.PendienteDeConfirmar), res)

        val error = RuntimeException("PERMISSION_DENIED")
        fin!!(Result.failure(error))
        assertEquals(1, res.size)          // no se repite el resultado
        assertEquals(listOf<Throwable>(error), tardios)
    }

    @Test fun laConfirmacionTardiaNoAvisaNiRepite() {
        val reloj = RelojManual()
        var fin: ((Result<Unit>) -> Unit)? = null
        var res = 0
        var tardios = 0
        guardarConTope(8000, reloj.programador, { fin = it }, { res++ }, { tardios++ })
        reloj.saltarTope()
        fin!!(Result.success(Unit))
        assertEquals(1, res); assertEquals(0, tardios)
    }

    @Test fun falloAntesDelTopeSeDevuelveComoFallo() {
        val reloj = RelojManual()
        var fin: ((Result<Unit>) -> Unit)? = null
        val res = mutableListOf<EstadoGuardado>()
        guardarConTope(8000, reloj.programador, { fin = it }, { res += it }, { })
        fin!!(Result.failure(RuntimeException("x")))
        assertTrue(res.single() is EstadoGuardado.Fallo)
        assertTrue(reloj.cancelado)
    }

    // ---- Suscripción: tope y activación tardía

    @Test fun alTopeSaleDeActivandoConElAviso() {
        val e = alTopeActivacion(SuscripcionEstado(activando = true), "tarda")
        assertFalse(e.activando); assertEquals("tarda", e.error)
    }

    @Test fun elTopeNoPisaUnaActivacionYaTerminada() {
        val ya = SuscripcionEstado(activando = false, exitoMensaje = "ok")
        assertEquals(ya, alTopeActivacion(ya, "tarda"))
    }

    @Test fun laActivacionTardiaLimpiaElAvisoYDejaSoloElExito() {
        val trasTope = alTopeActivacion(SuscripcionEstado(activando = true), "tarda")
        val e = alExitoActivacion(trasTope, avisar = true, mensajeExito = "activada")
        assertNull(e.error); assertEquals("activada", e.exitoMensaje); assertFalse(e.activando)
    }

    @Test fun unFalloTardioTrasUnExitoNoMachacaNada() {
        val conExito = alExitoActivacion(SuscripcionEstado(activando = true), true, "activada")
        val e = alFalloActivacion(conExito, "error")
        assertNull(e.error); assertEquals("activada", e.exitoMensaje)
        // sin éxito previo, el fallo sí se muestra
        assertEquals("error", alFalloActivacion(SuscripcionEstado(activando = true), "error").error)
    }

    // ---- Recarga fallida conserva la lista

    @Test fun siFallaLaRecargaSeConservaLaListaAnteriorConElError() {
        val antes = listOf(InformeEnergetico(id = "a"))
        val r = conservandoAnterior(antes, ListadoInformes(errorCarga = true))
        assertEquals(antes, r.informes); assertTrue(r.errorCarga)
        // sin lista previa, no se inventa nada
        assertTrue(conservandoAnterior(emptyList(), ListadoInformes(errorCarga = true)).informes.isEmpty())
        // una carga correcta sustituye la lista
        assertEquals(emptyList<InformeEnergetico>(), conservandoAnterior(antes, ListadoInformes()).informes)
    }

    // ---- cambiosDeChat

    @Test fun cambiosDeChatSoloTocaLosCamposQueLasReglasPermiten() {
        val c = cambiosDeChat("hola", "receptor", 123L)
        assertEquals(setOf("ultimoMensaje", "fechaUltimoMensaje", "noLeidosPor.receptor"), c.keys)
        assertEquals("hola", c["ultimoMensaje"])
        assertEquals(123L, c["fechaUltimoMensaje"])
        assertTrue(c["noLeidosPor.receptor"] is com.google.firebase.firestore.FieldValue)
    }
}
