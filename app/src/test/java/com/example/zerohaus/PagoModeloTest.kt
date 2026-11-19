package com.example.zerohaus

import com.example.zerohaus.Modelos.CuentaStripe
import com.example.zerohaus.Modelos.DatosPaymentSheet
import com.example.zerohaus.Modelos.EnlaceStripe
import com.example.zerohaus.Modelos.EstadoCuentaStripe
import com.example.zerohaus.Modelos.EstadoPago
import com.example.zerohaus.Modelos.Pago
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagoModeloTest {

    /** Como lo deja `nuevo_pago` del servidor tras aceptar y cobrar (Firestore da los enteros como Long). */
    private fun documento(): MutableMap<String, Any?> = mutableMapOf(
        "id" to "p1", "cliente" to "cli", "profesional" to "pro", "chatId" to "c1",
        "descripcion" to "Aislamiento", "importeCent" to 150_000L, "comisionCent" to 15_000L,
        "netoCent" to 135_000L, "moneda" to "eur", "comisionPct" to 10L, "liberacionDias" to 7L,
        "estado" to "retenido",
        "historial" to listOf(
            mapOf("estado" to "solicitado", "evento" to "solicitar", "actor" to "profesional", "uid" to "pro", "fecha" to 1L),
            mapOf("estado" to "pendiente_pago", "evento" to "aceptar", "actor" to "cliente", "uid" to "cli", "fecha" to 2L),
            mapOf("estado" to "retenido", "evento" to "cobro_ok", "actor" to "sistema", "uid" to null, "fecha" to 3L),
        ),
        "creado" to 1L, "actualizado" to 3L,
        "stripePaymentIntentId" to "pi_123", "stripeTransferId" to null, "stripeRefundId" to null,
        "terminadoEn" to null, "liberarEn" to null, "disputa" to null,
    )

    @Test fun leeElDocumentoDelServidor() {
        val p = Pago.desdeMapa("p1", documento())!!
        assertEquals("cli", p.cliente)
        assertEquals("pro", p.profesional)
        assertEquals("c1", p.chatId)
        assertEquals(150_000L, p.importeCent)
        assertEquals(15_000L, p.comisionCent)
        assertEquals(135_000L, p.netoCent)
        assertEquals(10.0, p.comisionPct, 0.0)
        assertEquals(7, p.liberacionDias)
        assertEquals(EstadoPago.RETENIDO, p.estado)
        assertEquals(3, p.historial.size)
        assertEquals(EstadoPago.PENDIENTE_PAGO, p.historial[1].estado)
        assertNull(p.historial[2].uid)
        assertEquals("pi_123", p.stripePaymentIntentId)
        assertNull(p.stripeTransferId)
        assertNull(p.liberarEn)
        assertNull(p.disputa)
    }

    @Test fun losImportesSonLosDelServidorSinRecalcular() {
        // Aunque no cuadren, se muestran tal cual: la app no hace cuentas
        val p = Pago.desdeMapa("p1", documento().apply { this["netoCent"] = 1L })!!
        assertEquals(1L, p.netoCent)
        assertEquals(150_000L, p.importeCent)
    }

    @Test fun elIdEsElDelDocumento() {
        assertEquals("doc", Pago.desdeMapa("doc", documento())!!.id)
    }

    @Test fun terminadoYEnDisputa() {
        val p = Pago.desdeMapa("p1", documento().apply {
            this["estado"] = "en_disputa"
            this["terminadoEn"] = 10L
            this["liberarEn"] = 20L
            this["disputa"] = mapOf("motivo" to "No terminó", "fecha" to 15L)
            this["comisionPct"] = 1.13
        })!!
        assertEquals(EstadoPago.EN_DISPUTA, p.estado)
        assertEquals(10L, p.terminadoEn)
        assertEquals(20L, p.liberarEn)
        assertEquals("No terminó", p.disputa!!.motivo)
        assertEquals(15L, p.disputa.fecha)
        assertEquals(1.13, p.comisionPct, 0.0)
    }

    @Test fun estadoNuevoNoRompeLaLectura() {
        assertEquals(EstadoPago.DESCONOCIDO, Pago.desdeMapa("p1", documento().apply { this["estado"] = "otro" })!!.estado)
    }

    @Test fun sinCamposImprescindiblesNoSeMuestra() {
        for (campo in listOf("cliente", "profesional", "chatId", "estado", "importeCent", "comisionCent", "netoCent")) {
            assertNull(campo, Pago.desdeMapa("p1", documento().apply { remove(campo) }))
        }
        assertNull(Pago.desdeMapa("p1", documento().apply { this["importeCent"] = 12.5 }))
        assertNull(Pago.desdeMapa("p1", documento().apply { this["importeCent"] = -1L }))
        assertNull(Pago.desdeMapa("p1", documento().apply { this["importeCent"] = "1000" }))
        assertNull(Pago.desdeMapa("p1", documento().apply { this["cliente"] = "" }))
        assertNull(Pago.desdeMapa("p1", null))
        assertNull(Pago.desdeMapa("", documento()))
    }

    @Test fun unEnteroQueLlegaComoDoubleSinDecimalesVale() {
        assertEquals(1000L, Pago.desdeMapa("p1", documento().apply { this["importeCent"] = 1000.0 })!!.importeCent)
    }

    @Test fun ordenarDelMasRecienteAlMasAntiguoSinRepetidos() {
        val base = Pago.desdeMapa("a", documento())!!
        val lista = listOf(base.copy(id = "a", creado = 1), base.copy(id = "b", creado = 3), base.copy(id = "c", creado = 2), base.copy(id = "b", creado = 3))
        assertEquals(listOf("b", "c", "a"), Pago.ordenar(lista).map { it.id })
    }

    @Test fun cuentaStripe() {
        val c = CuentaStripe.desdeMapa(mapOf(
            "accountId" to "acct_1", "estado" to "activa", "chargesEnabled" to true,
            "payoutsEnabled" to true, "detailsSubmitted" to true
        ))!!
        assertEquals("acct_1", c.accountId)
        assertTrue(c.activa)
        assertTrue(c.chargesEnabled && c.payoutsEnabled && c.detailsSubmitted)

        val pendiente = CuentaStripe.desdeMapa(mapOf("estado" to "pendiente", "chargesEnabled" to "true"))!!
        assertEquals(EstadoCuentaStripe.PENDIENTE, pendiente.estado)
        assertFalse(pendiente.activa)
        assertFalse(pendiente.chargesEnabled)   // solo un booleano de verdad cuenta
        assertEquals(EstadoCuentaStripe.RESTRINGIDA, CuentaStripe.desdeMapa(mapOf("estado" to "restringida"))!!.estado)
        assertEquals(EstadoCuentaStripe.DESCONOCIDO, CuentaStripe.desdeMapa(mapOf("estado" to "rara"))!!.estado)
        assertNull(CuentaStripe.desdeMapa(null))
    }

    @Test fun datosDePaymentSheet() {
        val d = DatosPaymentSheet.desdeRespuesta(mapOf(
            "paymentIntentClientSecret" to "pi_1_secret_abc", "customerId" to "cus_1", "ephemeralKeySecret" to "ek_test_xyz"
        ))
        assertNotNull(d)
        assertEquals("pi_1_secret_abc", d!!.paymentIntentClientSecret)
        assertEquals("cus_1", d.customerId)
        assertEquals("ek_test_xyz", d.ephemeralKeySecret)
        // Los secretos no salen en un log
        assertFalse(d.toString().contains("secret_abc"))
        assertFalse(d.toString().contains("ek_test"))
    }

    @Test fun respuestaIncompletaNoAbreLaHoja() {
        assertNull(DatosPaymentSheet.desdeRespuesta(mapOf("customerId" to "cus_1", "ephemeralKeySecret" to "ek")))
        assertNull(DatosPaymentSheet.desdeRespuesta(mapOf("paymentIntentClientSecret" to "s", "customerId" to "", "ephemeralKeySecret" to "ek")))
        assertNull(DatosPaymentSheet.desdeRespuesta(mapOf("paymentIntentClientSecret" to "s", "customerId" to "c")))
        assertNull(DatosPaymentSheet.desdeRespuesta(null))
    }

    @Test fun enlaceDeAltaSoloHttpsDeStripe() {
        assertTrue(EnlaceStripe.esValido("https://connect.stripe.com/setup/e/acct_1/abc"))
        assertTrue(EnlaceStripe.esValido("https://stripe.com/x"))
        for (u in listOf(
            null, "", "http://connect.stripe.com/setup", "https://connect.stripe.com.evil.com/x",
            "https://evilstripe.com/x", "https://user@connect.stripe.com/x", "javascript:alert(1)",
            "intent://connect.stripe.com#Intent;end", "https://"
        )) assertFalse("«$u»", EnlaceStripe.esValido(u))
    }
}
