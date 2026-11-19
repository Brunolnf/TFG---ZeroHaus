package com.example.zerohaus

import com.example.zerohaus.Modelos.AccionPago
import com.example.zerohaus.Modelos.DatosPaymentSheet
import com.example.zerohaus.Modelos.EntradaHistorialPago
import com.example.zerohaus.Modelos.ErrorPago
import com.example.zerohaus.Modelos.EstadoPago
import com.example.zerohaus.Modelos.Pago
import com.example.zerohaus.Modelos.ResultadoHojaPago
import com.example.zerohaus.ViewModel.FlujoPago
import com.example.zerohaus.ViewModel.FlujosPago
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FlujoPagoTest {

    private val datos = DatosPaymentSheet("pi_1_secret_x", "cus_1", "ek_test_1")

    private fun pago(estado: EstadoPago, vararg estados: EstadoPago, id: String = "p1") = Pago(
        id = id, cliente = "cli", profesional = "pro", chatId = "c1", descripcion = "Obra",
        importeCent = 5000, comisionCent = 500, netoCent = 4500, moneda = "eur",
        comisionPct = 10.0, liberacionDias = 7, estado = estado,
        historial = estados.mapIndexed { i, e -> EntradaHistorialPago(e, "", "", null, i.toLong()) }
    )

    /** Solicitado con su única entrada de historial, como lo ve el cliente al pulsar «Pagar». */
    private val solicitado = pago(EstadoPago.SOLICITADO, EstadoPago.SOLICITADO)

    private fun hastaPresentado(): FlujoPago {
        var f: FlujoPago = FlujosPago.iniciar(FlujoPago.Inactivo, solicitado, AccionPago.ACEPTAR)
        f = FlujosPago.datosRecibidos(f, "p1", datos)
        return FlujosPago.hojaPresentada(f)
    }

    @Test fun caminoCompleto() {
        var f: FlujoPago = FlujosPago.iniciar(FlujoPago.Inactivo, solicitado, AccionPago.ACEPTAR)
        assertEquals(FlujoPago.Preparando("p1", AccionPago.ACEPTAR, 1), f)
        assertTrue(FlujosPago.enCurso(f))
        f = FlujosPago.datosRecibidos(f, "p1", datos)
        assertEquals(FlujoPago.Listo("p1", datos, 1), f)
        f = FlujosPago.hojaPresentada(f)
        assertEquals(FlujoPago.Presentado("p1", 1), f)
        f = FlujosPago.resultadoHoja(f, ResultadoHojaPago.Completado)
        // Completed no es «pagado»: se espera al webhook
        assertEquals(FlujoPago.Procesando("p1", 1), f)
        assertTrue(FlujosPago.enCurso(f))
        f = FlujosPago.snapshot(f, pago(EstadoPago.PENDIENTE_PAGO, EstadoPago.SOLICITADO, EstadoPago.PENDIENTE_PAGO))
        assertEquals(FlujoPago.Procesando("p1", 1), f)
        f = FlujosPago.snapshot(f, pago(EstadoPago.RETENIDO, EstadoPago.SOLICITADO, EstadoPago.PENDIENTE_PAGO, EstadoPago.RETENIDO))
        assertEquals(FlujoPago.Resuelto("p1", EstadoPago.RETENIDO), f)
        assertFalse(FlujosPago.enCurso(f))
    }

    @Test fun elWebhookPuedeLlegarAntesQueElResultadoDeLaHoja() {
        var f = FlujosPago.snapshot(hastaPresentado(), pago(EstadoPago.RETENIDO, EstadoPago.SOLICITADO, EstadoPago.PENDIENTE_PAGO, EstadoPago.RETENIDO))
        assertEquals(FlujoPago.Resuelto("p1", EstadoPago.RETENIDO), f)
        f = FlujosPago.resultadoHoja(f, ResultadoHojaPago.Completado)
        assertEquals(FlujoPago.Resuelto("p1", EstadoPago.RETENIDO), f)
    }

    @Test fun cobroFallidoPorWebhook() {
        val f = FlujosPago.resultadoHoja(hastaPresentado(), ResultadoHojaPago.Completado)
        assertEquals(
            FlujoPago.Resuelto("p1", EstadoPago.FALLIDO),
            FlujosPago.snapshot(f, pago(EstadoPago.FALLIDO, EstadoPago.SOLICITADO, EstadoPago.PENDIENTE_PAGO, EstadoPago.FALLIDO))
        )
    }

    @Test fun unSnapshotAtrasadoNoResuelve() {
        // Reintentar desde fallido (3 entradas): el «fallido» de antes no es el resultado
        val fallido = pago(EstadoPago.FALLIDO, EstadoPago.SOLICITADO, EstadoPago.PENDIENTE_PAGO, EstadoPago.FALLIDO)
        var f: FlujoPago = FlujosPago.iniciar(FlujoPago.Inactivo, fallido, AccionPago.REINTENTAR)
        f = FlujosPago.hojaPresentada(FlujosPago.datosRecibidos(f, "p1", datos))
        f = FlujosPago.resultadoHoja(f, ResultadoHojaPago.Completado)
        assertEquals(FlujoPago.Procesando("p1", 3), f)
        assertEquals(FlujoPago.Procesando("p1", 3), FlujosPago.snapshot(f, fallido))
        val reintentado = pago(EstadoPago.RETENIDO, EstadoPago.SOLICITADO, EstadoPago.PENDIENTE_PAGO, EstadoPago.FALLIDO, EstadoPago.PENDIENTE_PAGO, EstadoPago.RETENIDO)
        assertEquals(FlujoPago.Resuelto("p1", EstadoPago.RETENIDO), FlujosPago.snapshot(f, reintentado))
    }

    @Test fun reanudarUnPagoPendienteTrasCerrarLaHoja() {
        // Ya en pendiente_pago: aceptar devuelve el mismo cobro y no añade entrada
        val pendiente = pago(EstadoPago.PENDIENTE_PAGO, EstadoPago.SOLICITADO, EstadoPago.PENDIENTE_PAGO)
        var f: FlujoPago = FlujosPago.iniciar(FlujoPago.Cancelado("p1"), pendiente, AccionPago.ACEPTAR)
        assertEquals(FlujoPago.Preparando("p1", AccionPago.ACEPTAR, 1), f)
        f = FlujosPago.resultadoHoja(FlujosPago.hojaPresentada(FlujosPago.datosRecibidos(f, "p1", datos)), ResultadoHojaPago.Completado)
        assertEquals(FlujoPago.Procesando("p1", 1), FlujosPago.snapshot(f, pendiente))
        assertEquals(
            FlujoPago.Resuelto("p1", EstadoPago.RETENIDO),
            FlujosPago.snapshot(f, pago(EstadoPago.RETENIDO, EstadoPago.SOLICITADO, EstadoPago.PENDIENTE_PAGO, EstadoPago.RETENIDO))
        )
    }

    @Test fun snapshotDeOtroPagoNoCuenta() {
        val f = FlujosPago.resultadoHoja(hastaPresentado(), ResultadoHojaPago.Completado)
        val otro = pago(EstadoPago.RETENIDO, EstadoPago.SOLICITADO, EstadoPago.PENDIENTE_PAGO, EstadoPago.RETENIDO, id = "p2")
        assertSame(f, FlujosPago.snapshot(f, otro))
    }

    @Test fun cerrarLaHoja() {
        assertEquals(FlujoPago.Cancelado("p1"), FlujosPago.resultadoHoja(hastaPresentado(), ResultadoHojaPago.Cancelado))
    }

    @Test fun fallaLaHoja() {
        assertEquals(
            FlujoPago.Fallido("p1", null, "tarjeta"),
            FlujosPago.resultadoHoja(hastaPresentado(), ResultadoHojaPago.Fallido("tarjeta"))
        )
    }

    @Test fun fallaElServidorAlPreparar() {
        val f = FlujosPago.iniciar(FlujoPago.Inactivo, solicitado, AccionPago.ACEPTAR)
        assertEquals(
            FlujoPago.Fallido("p1", ErrorPago.TRANSICION_NO_PERMITIDA, null),
            FlujosPago.errorAlPreparar(f, "p1", ErrorPago.TRANSICION_NO_PERMITIDA)
        )
    }

    @Test fun noSeEmpiezaOtroPagoConUnoEnMarcha() {
        val f = hastaPresentado()
        assertSame(f, FlujosPago.iniciar(f, pago(EstadoPago.SOLICITADO, EstadoPago.SOLICITADO, id = "p2"), AccionPago.ACEPTAR))
        val procesando = FlujosPago.resultadoHoja(f, ResultadoHojaPago.Completado)
        assertSame(procesando, FlujosPago.iniciar(procesando, solicitado, AccionPago.ACEPTAR))
    }

    @Test fun trasCancelarOFallarSePuedeVolverAEmpezar() {
        val cancelado = FlujosPago.resultadoHoja(hastaPresentado(), ResultadoHojaPago.Cancelado)
        assertEquals(FlujoPago.Preparando("p1", AccionPago.ACEPTAR, 1), FlujosPago.iniciar(cancelado, solicitado, AccionPago.ACEPTAR))
    }

    @Test fun soloAceptarYReintentarAbrenLaHoja() {
        for (a in listOf(AccionPago.CANCELAR, AccionPago.TERMINAR, AccionPago.CONFIRMAR, AccionPago.DISPUTAR)) {
            assertSame(FlujoPago.Inactivo, FlujosPago.iniciar(FlujoPago.Inactivo, solicitado, a))
        }
    }

    @Test fun eventosAtrasadosORepetidosNoCambianNada() {
        val preparando = FlujosPago.iniciar(FlujoPago.Inactivo, solicitado, AccionPago.ACEPTAR)
        // Datos de otro pago
        assertSame(preparando, FlujosPago.datosRecibidos(preparando, "p2", datos))
        assertSame(preparando, FlujosPago.errorAlPreparar(preparando, "p2", ErrorPago.RED))
        // Sin hoja abierta, un resultado (p. ej. tras morir el proceso) no inventa nada
        assertSame(FlujoPago.Inactivo, FlujosPago.resultadoHoja(FlujoPago.Inactivo, ResultadoHojaPago.Completado))
        assertSame(preparando, FlujosPago.resultadoHoja(preparando, ResultadoHojaPago.Completado))
        // Presentar dos veces
        val presentado = hastaPresentado()
        assertSame(presentado, FlujosPago.hojaPresentada(presentado))
        // Datos que llegan cuando ya no se esperan
        assertSame(presentado, FlujosPago.datosRecibidos(presentado, "p1", datos))
        // Un snapshot sin hoja abierta
        assertSame(FlujoPago.Inactivo, FlujosPago.snapshot(FlujoPago.Inactivo, solicitado))
    }

    @Test fun cancelacionPorCaducidadDelCobro() {
        val f = FlujosPago.resultadoHoja(hastaPresentado(), ResultadoHojaPago.Completado)
        assertEquals(
            FlujoPago.Resuelto("p1", EstadoPago.CANCELADO),
            FlujosPago.snapshot(f, pago(EstadoPago.CANCELADO, EstadoPago.SOLICITADO, EstadoPago.PENDIENTE_PAGO, EstadoPago.CANCELADO))
        )
    }
}
