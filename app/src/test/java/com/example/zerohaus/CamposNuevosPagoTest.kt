package com.example.zerohaus

import com.example.zerohaus.Modelos.ContracargoPago
import com.example.zerohaus.Modelos.LiberacionPago
import com.example.zerohaus.Modelos.Pago
import com.example.zerohaus.Modelos.PapelPago
import com.example.zerohaus.Modelos.PresentacionPagos
import com.example.zerohaus.Modelos.ResultadoContracargo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Campos de /pagos que añadió el servidor (requiereAdmin, liberacion, contracargo) y su presentación. */
class CamposNuevosPagoTest {

    private val base = mapOf<String, Any?>(
        "cliente" to "cli", "profesional" to "pro", "chatId" to "c1", "estado" to "liberado",
        "importeCent" to 5000L, "comisionCent" to 500L, "netoCent" to 4500L
    )

    @Test fun sinLosCamposNuevosHayValoresPorDefecto() {
        val p = Pago.desdeMapa("p1", base)!!
        assertFalse(p.requiereAdmin)
        assertNull(p.liberacion)
        assertNull(p.contracargo)
    }

    @Test fun leeLosCamposNuevos() {
        val p = Pago.desdeMapa("p1", base + mapOf(
            "requiereAdmin" to true,
            "liberacion" to mapOf("fallos" to 3L, "proximoIntento" to 1700000000000L),
            "contracargo" to mapOf("resultado" to "lost", "fecha" to 1700000001000L)
        ))!!
        assertTrue(p.requiereAdmin)
        assertEquals(LiberacionPago(3, 1700000000000L), p.liberacion)
        assertEquals(ContracargoPago(ResultadoContracargo.PERDIDO, 1700000001000L), p.contracargo)
    }

    @Test fun camposRarosNoRompenElPago() {
        val p = Pago.desdeMapa("p1", base + mapOf(
            "requiereAdmin" to "si", "liberacion" to mapOf("fallos" to "x"), "contracargo" to mapOf("resultado" to "otro")
        ))!!
        assertFalse(p.requiereAdmin)
        assertEquals(LiberacionPago(0, null), p.liberacion)
        assertEquals(ResultadoContracargo.DESCONOCIDO, p.contracargo?.resultado)
        assertEquals(ResultadoContracargo.GANADO, ResultadoContracargo.desde("won"))
    }

    @Test fun elContracargoSeEnsenaSoloALasPartes() {
        val p = Pago.desdeMapa("p1", base + mapOf("contracargo" to mapOf("resultado" to "won", "fecha" to 1L)))!!
        assertTrue(PresentacionPagos.avisoContracargo(p, PapelPago.CLIENTE))
        assertTrue(PresentacionPagos.avisoContracargo(p, PapelPago.PROFESIONAL))
        assertFalse(PresentacionPagos.avisoContracargo(p, null))
        assertFalse(PresentacionPagos.avisoContracargo(Pago.desdeMapa("p2", base)!!, PapelPago.CLIENTE))
    }

    @Test fun requierenAtencionSoloLosMarcados() {
        val a = Pago.desdeMapa("a", base + mapOf("requiereAdmin" to true, "creado" to 1L))!!
        val b = Pago.desdeMapa("b", base)!!
        val c = Pago.desdeMapa("c", base + mapOf("requiereAdmin" to true, "creado" to 5L))!!
        assertEquals(listOf("c", "a"), PresentacionPagos.requierenAtencion(listOf(a, b, c)).map { it.id })
    }
}
