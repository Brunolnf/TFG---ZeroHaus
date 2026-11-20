package com.example.zerohaus

import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Util.Lectura
import com.example.zerohaus.Util.ReservaIds
import com.example.zerohaus.Util.clasificarLectura
import com.example.zerohaus.Util.esRespuestaVigente
import com.example.zerohaus.Util.listadoDe
import com.example.zerohaus.Util.mapear
import com.example.zerohaus.Util.reservarIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardadoAtomicoYListadoTest {

    @Test fun elReintentoUsaLosMismosIds() {
        var n = 0
        val primera = reservarIds(null, null, { "v${++n}" }, { "i${++n}" })
        val reintento = reservarIds(primera, null, { "v${++n}" }, { "i${++n}" })
        assertEquals(primera, reintento)
        assertEquals(2, n) // no se reservó nada más
    }

    @Test fun laViviendaExistenteMandaSobreLaReservada() {
        val r = reservarIds(ReservaIds("v1", "i1"), "existente", { "otra" }, { "otro" })
        assertEquals(ReservaIds("existente", "i1"), r)
    }

    @Test fun lecturaVaciaDeCacheNoEsSinInformesSinoFallo() {
        assertEquals(Lectura.Fallo(), clasificarLectura(emptyList<Int>(), vacio = true, deCache = true))
        assertEquals(Lectura.Servidor(emptyList<Int>()), clasificarLectura(emptyList<Int>(), vacio = true, deCache = false))
        assertEquals(Lectura.Guardada(listOf(1)), clasificarLectura(listOf(1), vacio = false, deCache = true))
    }

    @Test fun tresCasosDelHistorial() {
        val vacio = listadoDe(Lectura.Servidor(emptyList()))
        assertTrue(vacio.informes.isEmpty()); assertFalse(vacio.errorCarga); assertFalse(vacio.datosGuardados)

        val guardados = listadoDe(Lectura.Guardada(listOf(InformeEnergetico(id = "a"))))
        assertTrue(guardados.datosGuardados); assertFalse(guardados.errorCarga); assertEquals(1, guardados.informes.size)

        val fallo = listadoDe(Lectura.Fallo(RuntimeException("x")))
        assertTrue(fallo.errorCarga); assertTrue(fallo.informes.isEmpty())
    }

    @Test fun mapearConservaElOrigen() {
        assertEquals(Lectura.Guardada(2), Lectura.Guardada(1).mapear { it + 1 })
        assertEquals(Lectura.Fallo(), Lectura.Fallo().mapear { 1 })
    }

    @Test fun laRespuestaDeIASoloValeSiSigueElMismoInformeEIdioma() {
        assertTrue(esRespuestaVigente("a", "es", "a", "es"))
        assertFalse(esRespuestaVigente("a", "es", "b", "es"))
        assertFalse(esRespuestaVigente("a", "es", "a", "en"))
        assertFalse(esRespuestaVigente("", "es", "", "es"))
    }
}
