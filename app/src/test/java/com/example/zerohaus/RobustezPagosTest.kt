package com.example.zerohaus

import com.example.zerohaus.Modelos.AvisoFlujo
import com.example.zerohaus.Modelos.EntradaHistorialPago
import com.example.zerohaus.Modelos.ErrorPago
import com.example.zerohaus.Modelos.EstadoPago
import com.example.zerohaus.Modelos.FormatoPago
import com.example.zerohaus.Modelos.Pago
import com.example.zerohaus.Modelos.PresentacionPagos
import com.example.zerohaus.Modelos.ResultadoHojaPago
import com.example.zerohaus.Modelos.ResultadoPago
import com.example.zerohaus.Modelos.hastaElPrimerError
import com.example.zerohaus.ViewModel.FlujoPago
import com.example.zerohaus.ViewModel.FlujosPago
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Espera acotada a Procesando, listeners que mueren, avisos de cancelación y formato por idioma. */
class RobustezPagosTest {

    private fun pago(estado: EstadoPago, vararg h: Pair<EstadoPago, String>) = Pago(
        id = "p1", cliente = "cli", profesional = "pro", chatId = "c1", descripcion = "Obra",
        importeCent = 5000, comisionCent = 500, netoCent = 4500, moneda = "eur",
        comisionPct = 10.0, liberacionDias = 7, estado = estado,
        historial = h.mapIndexed { i, (e, a) -> EntradaHistorialPago(e, "", a, null, i.toLong()) }
    )

    // ── Procesando con límite (reloj inyectado) ──

    @Test fun completedGuardaCuandoOcurrio() {
        val listo = FlujoPago.Presentado("p1", 1)
        assertEquals(FlujoPago.Procesando("p1", 1, 5_000L), FlujosPago.resultadoHoja(listo, ResultadoHojaPago.Completado, 5_000L))
    }

    @Test fun antesDelLimiteSigueProcesando() {
        val f = FlujoPago.Procesando("p1", 1, 1_000L)
        assertEquals(f, FlujosPago.vencido(f, 1_000L + FlujosPago.PROCESANDO_MAX_MS - 1))
        assertTrue(FlujosPago.enCurso(f))
    }

    @Test fun pasadoElLimiteDejaDeBloquear() {
        val f = FlujoPago.Procesando("p1", 1, 1_000L)
        val v = FlujosPago.vencido(f, 1_000L + FlujosPago.PROCESANDO_MAX_MS)
        assertEquals(FlujoPago.Confirmando("p1", 1), v)
        assertFalse("ya no bloquea los botones", FlujosPago.enCurso(v))
        assertEquals(AvisoFlujo.Confirmando, PresentacionPagos.avisoFlujo(v))
        assertTrue(PresentacionPagos.esFinalDeFlujo(v))
    }

    @Test fun elLimiteSoloAfectaAProcesando() {
        val otros = listOf(FlujoPago.Inactivo, FlujoPago.Presentado("p1", 1), FlujoPago.Cancelado("p1"), FlujoPago.Resuelto("p1", EstadoPago.RETENIDO))
        otros.forEach { assertEquals(it, FlujosPago.vencido(it, Long.MAX_VALUE / 2)) }
    }

    @Test fun unSnapshotTardioTodaviaResuelveDesdeConfirmando() {
        val f = FlujoPago.Confirmando("p1", 1)
        val retenido = pago(EstadoPago.RETENIDO, EstadoPago.SOLICITADO to "cliente", EstadoPago.PENDIENTE_PAGO to "cliente", EstadoPago.RETENIDO to "sistema")
        assertEquals(FlujoPago.Resuelto("p1", EstadoPago.RETENIDO, "sistema"), FlujosPago.snapshot(f, retenido))
    }

    // ── Cancelado: caducado o cancelado por la otra parte ──

    @Test fun caducadoSoloSiLoCancelaElSistema() {
        val caduca = pago(EstadoPago.CANCELADO, EstadoPago.SOLICITADO to "cliente", EstadoPago.PENDIENTE_PAGO to "cliente", EstadoPago.CANCELADO to "sistema")
        val otra = pago(EstadoPago.CANCELADO, EstadoPago.SOLICITADO to "cliente", EstadoPago.PENDIENTE_PAGO to "cliente", EstadoPago.CANCELADO to "profesional")
        val base = FlujoPago.Procesando("p1", 1)
        assertEquals(AvisoFlujo.Caducado, PresentacionPagos.avisoFlujo(FlujosPago.snapshot(base, caduca)))
        assertEquals(AvisoFlujo.PagoCancelado, PresentacionPagos.avisoFlujo(FlujosPago.snapshot(base, otra)))
        assertEquals(AvisoFlujo.PagoCancelado, PresentacionPagos.avisoFlujo(FlujoPago.Resuelto("p1", EstadoPago.CANCELADO)))
    }

    // ── Un listener que falla cierra el flujo ──

    @Test fun elFlujoSeCortaTrasElPrimerError() = runBlocking {
        val r = flowOf<ResultadoPago<Int>>(ResultadoPago.Ok(1), ResultadoPago.Error(ErrorPago.RED), ResultadoPago.Ok(2))
            .hastaElPrimerError().toList()
        assertEquals(listOf(ResultadoPago.Ok(1), ResultadoPago.Error(ErrorPago.RED)), r)
    }

    @Test fun sinErroresPasaTodo() = runBlocking {
        val r = flowOf<ResultadoPago<Int>>(ResultadoPago.Ok(1), ResultadoPago.Ok(2)).hastaElPrimerError().toList()
        assertEquals(2, r.size)
    }

    // ── Formato por idioma y dígitos Unicode ──

    @Test fun elSimboloYLaMonedaNoCambianConElIdioma() {
        val en = FormatoPago.importe(123456, "eur", Locale.forLanguageTag("en-u-nu-latn"))
        val es = FormatoPago.importe(123456, "eur", Locale.forLanguageTag("es-ES"))
        val ar = FormatoPago.importe(123456, "eur", Locale.forLanguageTag("ar-u-nu-latn"))
        assertEquals("1,234.56\u00A0€", en)
        assertEquals("1.234,56\u00A0€", es)
        assertTrue(ar, ar.endsWith("\u00A0€"))
        assertTrue(ar, ar.any { it in '0'..'9' })
        assertEquals("12,34\u00A0€", FormatoPago.importe(1234))
        assertEquals("12.34\u00A0USD", FormatoPago.importe(1234, "usd", Locale.forLanguageTag("en")))
    }

    @Test fun losDigitosArabesSeNormalizan() {
        assertEquals(2550L, FormatoPago.centimosDesdeTexto("\u0662\u0665\u066B\u0665"))
        assertEquals(2550L, FormatoPago.centimosDesdeTexto("\u0662\u0665.\u0665"))
        assertEquals(1234L, FormatoPago.centimosDesdeTexto("\u0661\u0662,\u0663\u0664"))
        assertEquals(1200L, FormatoPago.centimosDesdeTexto("\u0967\u0968"))
        assertEquals(2550L, FormatoPago.centimosDesdeTexto("25,5"))
        assertNull(FormatoPago.centimosDesdeTexto("\u0661\u0662\u0663\u0664"+"\u0665\u0666\u0667\u0668,1"))
        assertNull(FormatoPago.centimosDesdeTexto("\u0662a"))
    }
}
