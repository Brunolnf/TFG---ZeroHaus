package com.example.zerohaus

import com.example.zerohaus.Modelos.AccionPago
import com.example.zerohaus.Modelos.AvisoFlujo
import com.example.zerohaus.Modelos.ConceptoPago
import com.example.zerohaus.Modelos.CuentaStripe
import com.example.zerohaus.Modelos.DestinoSolicitar
import com.example.zerohaus.Modelos.ErrorPago
import com.example.zerohaus.Modelos.EstadoCobros
import com.example.zerohaus.Modelos.EstadoCuentaStripe
import com.example.zerohaus.Modelos.EstadoPago
import com.example.zerohaus.Modelos.Pago
import com.example.zerohaus.Modelos.PapelPago
import com.example.zerohaus.Modelos.PresentacionPagos
import com.example.zerohaus.Modelos.ValidacionPago
import com.example.zerohaus.Modelos.ValidacionSolicitud
import com.example.zerohaus.ViewModel.FlujoPago
import com.example.zerohaus.ViewModel.PagosEstado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.example.zerohaus.Util.getCadenas
import com.example.zerohaus.Util.textoRetencion
import org.junit.Test

class PresentacionPagosTest {

    private fun pago(estado: EstadoPago, id: String = "p", creado: Long = 0, liberarEn: Long? = null) = Pago(
        id = id, cliente = "cli", profesional = "pro", chatId = "c1", descripcion = "Obra",
        importeCent = 5000, comisionCent = 500, netoCent = 4500, moneda = "eur",
        comisionPct = 10.0, liberacionDias = 7, estado = estado, creado = creado, liberarEn = liberarEn
    )

    private fun cuenta(e: EstadoCuentaStripe) = CuentaStripe("acct_1", e, true, true, true)

    // ── Validación del diálogo ──

    @Test fun solicitudValida() {
        assertEquals(ValidacionSolicitud.Valida(2550, "Revisión"), ValidacionPago.solicitud("25,5", "  Revisión "))
        assertEquals(ValidacionSolicitud.Valida(2500, "x"), ValidacionPago.solicitud("25", "x"))
        assertEquals(ValidacionSolicitud.Valida(2550, "x"), ValidacionPago.solicitud("25.50", "x"))
    }

    @Test fun importesQueNoSonImportes() {
        for (t in listOf("", " ", "abc", "0", "0,00", "-5", "1,234", "12,", "1e3")) {
            assertEquals(t, ValidacionSolicitud.Invalida(ErrorPago.IMPORTE_NO_VALIDO), ValidacionPago.solicitud(t, "x"))
        }
    }

    @Test fun elMinimoYElMaximoLosValidaElServidor() {
        // 0,01 € y 9.999.999 € pasan: la app solo comprueba la forma
        assertTrue(ValidacionPago.solicitud("0,01", "x") is ValidacionSolicitud.Valida)
        assertTrue(ValidacionPago.solicitud("999999", "x") is ValidacionSolicitud.Valida)
    }

    @Test fun descripcionVaciaOLarga() {
        assertEquals(ValidacionSolicitud.Invalida(ErrorPago.DESCRIPCION_NO_VALIDA), ValidacionPago.solicitud("10", "   "))
        assertEquals(ValidacionSolicitud.Invalida(ErrorPago.DESCRIPCION_NO_VALIDA), ValidacionPago.solicitud("10", "a".repeat(501)))
        assertTrue(ValidacionPago.solicitud("10", "a".repeat(500)) is ValidacionSolicitud.Valida)
    }

    @Test fun motivoDeDisputa() {
        assertNull(ValidacionPago.motivoDisputa("No vino"))
        assertEquals(ErrorPago.MOTIVO_NO_VALIDO, ValidacionPago.motivoDisputa("  "))
        assertEquals(ErrorPago.MOTIVO_NO_VALIDO, ValidacionPago.motivoDisputa("a".repeat(501)))
    }

    // ── Qué se ofrece ──

    @Test fun soloElProfesionalVeSolicitarPago() {
        assertTrue(PresentacionPagos.esProfesional("Técnico"))
        assertTrue(PresentacionPagos.esProfesional("Empresa"))
        assertFalse(PresentacionPagos.esProfesional("Propietario"))
        assertFalse(PresentacionPagos.esProfesional(null))
        assertTrue(PresentacionPagos.ofreceSolicitar(true, true))
        assertFalse(PresentacionPagos.ofreceSolicitar(true, false))
        assertFalse("sin pagos disponibles no aparece", PresentacionPagos.ofreceSolicitar(false, true))
    }

    @Test fun solicitarPagoSegunLaCuentaDeStripe() {
        val base = PagosEstado(disponibles = true, miUid = "pro")
        assertEquals(DestinoSolicitar.ESPERAR, PresentacionPagos.destinoSolicitar(base))
        assertEquals(DestinoSolicitar.COBROS, PresentacionPagos.destinoSolicitar(base.copy(cuentaCargada = true)))
        assertEquals(DestinoSolicitar.COBROS, PresentacionPagos.destinoSolicitar(
            base.copy(cuentaCargada = true, cuentaStripe = cuenta(EstadoCuentaStripe.PENDIENTE))))
        assertEquals(DestinoSolicitar.COBROS, PresentacionPagos.destinoSolicitar(
            base.copy(cuentaCargada = true, cuentaStripe = cuenta(EstadoCuentaStripe.RESTRINGIDA))))
        assertEquals(DestinoSolicitar.DIALOGO, PresentacionPagos.destinoSolicitar(
            base.copy(cuentaCargada = true, cuentaStripe = cuenta(EstadoCuentaStripe.ACTIVA))))
    }

    @Test fun estadoDeLosCobros() {
        assertEquals(EstadoCobros.DESCONOCIDO, PresentacionPagos.estadoCobros(null, false))
        assertEquals(EstadoCobros.SIN_CUENTA, PresentacionPagos.estadoCobros(null, true))
        assertEquals(EstadoCobros.PENDIENTE, PresentacionPagos.estadoCobros(cuenta(EstadoCuentaStripe.PENDIENTE), true))
        assertEquals(EstadoCobros.ACTIVA, PresentacionPagos.estadoCobros(cuenta(EstadoCuentaStripe.ACTIVA), true))
        assertEquals(EstadoCobros.RESTRINGIDA, PresentacionPagos.estadoCobros(cuenta(EstadoCuentaStripe.RESTRINGIDA), true))
        assertEquals(EstadoCobros.DESCONOCIDO, PresentacionPagos.estadoCobros(cuenta(EstadoCuentaStripe.DESCONOCIDO), true))
    }

    @Test fun laFranjaDelChatSoloEnsenaLosPagosEnMarcha() {
        val f = PresentacionPagos.franjaChat(listOf(
            pago(EstadoPago.LIBERADO, "a", 1), pago(EstadoPago.RETENIDO, "b", 2),
            pago(EstadoPago.CANCELADO, "c", 3), pago(EstadoPago.SOLICITADO, "d", 4)
        ))
        assertEquals(listOf("d", "b"), f.visibles.map { it.id })
        assertEquals(2, f.ocultos)
        assertEquals(PresentacionPagos.Franja(emptyList(), 0), PresentacionPagos.franjaChat(emptyList()))
    }

    @Test fun elAvisoDeRetencionVaConElBotonDePagar() {
        assertTrue(PresentacionPagos.avisoRetencion(setOf(AccionPago.ACEPTAR, AccionPago.CANCELAR)))
        assertTrue(PresentacionPagos.avisoRetencion(setOf(AccionPago.REINTENTAR)))
        assertFalse(PresentacionPagos.avisoRetencion(setOf(AccionPago.CONFIRMAR, AccionPago.DISPUTAR)))
        assertFalse(PresentacionPagos.avisoRetencion(emptySet()))
    }

    @Test fun aceptarEnPendientePagoEsContinuar() {
        assertTrue(PresentacionPagos.esReanudar(pago(EstadoPago.PENDIENTE_PAGO), AccionPago.ACEPTAR))
        assertFalse(PresentacionPagos.esReanudar(pago(EstadoPago.SOLICITADO), AccionPago.ACEPTAR))
        assertFalse(PresentacionPagos.esReanudar(pago(EstadoPago.PENDIENTE_PAGO), AccionPago.CANCELAR))
    }

    @Test fun confirmacionesDeAcciones() {
        assertTrue(PresentacionPagos.pideConfirmacion(AccionPago.CANCELAR))
        assertTrue(PresentacionPagos.pideConfirmacion(AccionPago.TERMINAR))
        assertTrue(PresentacionPagos.pideConfirmacion(AccionPago.CONFIRMAR))
        assertFalse(PresentacionPagos.pideConfirmacion(AccionPago.ACEPTAR))
        assertFalse(PresentacionPagos.pideConfirmacion(AccionPago.DISPUTAR))
    }

    @Test fun desgloseSegunElPapel() {
        val p = pago(EstadoPago.RETENIDO)
        assertEquals(
            listOf(ConceptoPago.IMPORTE to 5000L, ConceptoPago.COMISION to 500L, ConceptoPago.NETO to 4500L),
            PresentacionPagos.desglose(p, PapelPago.PROFESIONAL).map { it.concepto to it.cent }
        )
        assertEquals(listOf(ConceptoPago.IMPORTE to 5000L), PresentacionPagos.desglose(p, PapelPago.CLIENTE).map { it.concepto to it.cent })
        // Quien no es parte (el administrador) tampoco ve la comisión de esta vista
        assertEquals(1, PresentacionPagos.desglose(p, null).size)
    }

    @Test fun laLiberacionPrevistaSoloConElTrabajoTerminado() {
        assertEquals(1000L, PresentacionPagos.liberacionPrevista(pago(EstadoPago.TERMINADO, liberarEn = 1000)))
        assertNull(PresentacionPagos.liberacionPrevista(pago(EstadoPago.RETENIDO, liberarEn = 1000)))
        assertNull(PresentacionPagos.liberacionPrevista(pago(EstadoPago.TERMINADO)))
    }

    /** Con pagos_habilitados apagado solo se corta lo que abre dinero nuevo; lo demás sigue como el servidor. */
    @Test fun conLosPagosApagadosSoloSeCortaLoQueAbreDineroNuevo() {
        val apagado = PagosEstado(disponibles = false, miUid = "cli")
        val encendido = apagado.copy(disponibles = true)
        val pro = apagado.copy(miUid = "pro")
        // Cliente: pagar y reintentar desaparecen; cancelar, confirmar y disputar siguen
        assertEquals(setOf(AccionPago.CANCELAR), apagado.acciones(pago(EstadoPago.SOLICITADO)))
        assertEquals(setOf(AccionPago.CANCELAR), apagado.acciones(pago(EstadoPago.PENDIENTE_PAGO)))
        assertEquals(setOf(AccionPago.CANCELAR), apagado.acciones(pago(EstadoPago.FALLIDO)))
        assertEquals(setOf(AccionPago.DISPUTAR), apagado.acciones(pago(EstadoPago.RETENIDO)))
        assertEquals(setOf(AccionPago.CONFIRMAR, AccionPago.DISPUTAR), apagado.acciones(pago(EstadoPago.TERMINADO)))
        assertEquals(setOf(AccionPago.ACEPTAR, AccionPago.CANCELAR), encendido.acciones(pago(EstadoPago.SOLICITADO)))
        // Profesional: terminar y cancelar siguen
        assertEquals(setOf(AccionPago.TERMINAR), pro.acciones(pago(EstadoPago.RETENIDO)))
        assertEquals(setOf(AccionPago.CANCELAR), pro.acciones(pago(EstadoPago.SOLICITADO)))
        // Solicitar pago y configurar cobros dependen del interruptor
        assertFalse(PresentacionPagos.ofreceSolicitar(false, true))
        assertFalse(PresentacionPagos.mostrarCobros(false, true))
        assertTrue(PresentacionPagos.mostrarCobros(true, true))
        assertFalse(PresentacionPagos.mostrarCobros(true, false))
        assertFalse(apagado.puedeSolicitar)
    }

    @Test fun laFilaDePagosDeAjustesSeVeConPagosDisponiblesOSiYaTengoAlguno() {
        assertTrue(PresentacionPagos.mostrarEntradaAjustes(true, false))
        assertTrue(PresentacionPagos.mostrarEntradaAjustes(false, true))
        assertTrue(PresentacionPagos.mostrarEntradaAjustes(true, true))
        assertFalse(PresentacionPagos.mostrarEntradaAjustes(false, false))
    }

    @Test fun losDiasDeLiberacionEnSingularYPlural() {
        val es = getCadenas("Español")
        assertTrue(es.textoRetencion(1).contains("1 día"))
        assertFalse(es.textoRetencion(1).contains("1 días"))
        assertTrue(es.textoRetencion(7).contains("7 días"))
        assertFalse(es.textoRetencion(7).contains("{n}"))
    }

    // ── Aviso del flujo: nunca «pagado» antes de que el servidor lo diga ──

    @Test fun completedNoEsPagado() {
        assertEquals(AvisoFlujo.EnMarcha, PresentacionPagos.avisoFlujo(FlujoPago.Procesando("p", 1)))
        assertEquals(AvisoFlujo.EnMarcha, PresentacionPagos.avisoFlujo(FlujoPago.Presentado("p", 1)))
        assertEquals(AvisoFlujo.Retenido, PresentacionPagos.avisoFlujo(FlujoPago.Resuelto("p", EstadoPago.RETENIDO)))
    }

    @Test fun finalesDelFlujo() {
        assertEquals(AvisoFlujo.CobroFallido, PresentacionPagos.avisoFlujo(FlujoPago.Resuelto("p", EstadoPago.FALLIDO)))
        assertEquals(AvisoFlujo.Caducado, PresentacionPagos.avisoFlujo(FlujoPago.Resuelto("p", EstadoPago.CANCELADO, "sistema")))
        assertEquals(AvisoFlujo.Ninguno, PresentacionPagos.avisoFlujo(FlujoPago.Resuelto("p", EstadoPago.TERMINADO)))
        assertEquals(AvisoFlujo.HojaCerrada, PresentacionPagos.avisoFlujo(FlujoPago.Cancelado("p")))
        assertEquals(AvisoFlujo.ErrorHoja, PresentacionPagos.avisoFlujo(FlujoPago.Fallido("p", null, "x")))
        assertEquals(AvisoFlujo.Error(ErrorPago.RED), PresentacionPagos.avisoFlujo(FlujoPago.Fallido("p", ErrorPago.RED, null)))
        assertEquals(AvisoFlujo.Ninguno, PresentacionPagos.avisoFlujo(FlujoPago.Inactivo))
    }

    @Test fun cuandoSeCierraElFlujo() {
        assertFalse(PresentacionPagos.esFinalDeFlujo(FlujoPago.Inactivo))
        assertFalse(PresentacionPagos.esFinalDeFlujo(FlujoPago.Procesando("p", 1)))
        assertTrue(PresentacionPagos.esFinalDeFlujo(FlujoPago.Resuelto("p", EstadoPago.RETENIDO)))
        assertTrue(PresentacionPagos.esFinalDeFlujo(FlujoPago.Cancelado("p")))
        assertTrue(PresentacionPagos.esFinalDeFlujo(FlujoPago.Fallido("p", null, null)))
    }

    // ── Administrador ──

    @Test fun disputasDelAdministrador() {
        val d = PresentacionPagos.disputas(listOf(
            pago(EstadoPago.EN_DISPUTA, "a", 1), pago(EstadoPago.LIBERADO, "b", 5), pago(EstadoPago.EN_DISPUTA, "c", 3)
        ))
        assertEquals(listOf("c", "a"), d.map { it.id })
        assertEquals("liberar", com.example.zerohaus.Modelos.DecisionDisputa.LIBERAR.id)
        assertEquals("reembolsar", com.example.zerohaus.Modelos.DecisionDisputa.REEMBOLSAR.id)
    }
}
