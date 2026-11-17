package com.example.zerohaus

import com.example.zerohaus.Util.BorradoCuenta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BorradoCuentaTest {
    @Test fun respuestaConCancelarEnPlayTrue() {
        assertTrue(BorradoCuenta.requiereCancelarEnPlay(mapOf("ok" to true, "cancelarEnPlay" to true)))
    }

    @Test fun respuestaSinPendientes() {
        assertFalse(BorradoCuenta.requiereCancelarEnPlay(mapOf("ok" to true, "cancelarEnPlay" to false)))
        assertFalse(BorradoCuenta.requiereCancelarEnPlay(mapOf("ok" to true)))
    }

    @Test fun respuestaNulaOExtrana() {
        assertFalse(BorradoCuenta.requiereCancelarEnPlay(null))
        assertFalse(BorradoCuenta.requiereCancelarEnPlay("cancelarEnPlay"))
        assertFalse(BorradoCuenta.requiereCancelarEnPlay(mapOf("cancelarEnPlay" to "true")))
    }

    @Test fun unavailableConMotivoPlay() {
        assertTrue(BorradoCuenta.esFalloTransitorioDePlay("UNAVAILABLE", mapOf("motivo" to "play")))
    }

    @Test fun otrosErroresNoSonDePlay() {
        assertFalse(BorradoCuenta.esFalloTransitorioDePlay("UNAVAILABLE", null))
        assertFalse(BorradoCuenta.esFalloTransitorioDePlay("UNAVAILABLE", mapOf("motivo" to "envios")))
        assertFalse(BorradoCuenta.esFalloTransitorioDePlay("INTERNAL", mapOf("motivo" to "play")))
        assertFalse(BorradoCuenta.esFalloTransitorioDePlay(null, null))
    }

    @Test fun enlaceDeSuscripciones() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions?package=es.zerohaus.app",
            BorradoCuenta.URL_SUSCRIPCIONES_PLAY
        )
    }
}
