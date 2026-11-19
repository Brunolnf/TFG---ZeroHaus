package com.example.zerohaus

import com.example.zerohaus.Modelos.ClaveStripe
import com.example.zerohaus.Modelos.DisponibilidadPagos
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaveStripeTest {

    private val test = "pk_test_51AbCdEfGhIjKlMnOpQrStUvWxYz0123456789"

    @Test fun claveDeTestValida() {
        assertTrue(ClaveStripe.esValida(test))
        assertTrue(ClaveStripe.esValida("pk_test_0123456789"))
    }

    @Test fun nuncaUnaClaveLiveNiSecreta() {
        assertFalse(ClaveStripe.esValida("pk_live_51AbCdEfGhIjKlMnOpQrStUvWxYz0123456789"))
        assertFalse(ClaveStripe.esValida("sk_test_51AbCdEfGhIjKlMnOpQrStUvWxYz0123456789"))
        assertFalse(ClaveStripe.esValida("rk_test_51AbCdEfGhIjKlMnOpQrStUvWxYz0123456789"))
        assertFalse(ClaveStripe.esValida("PK_TEST_51AbCdEfGhIjKlMnOpQrStUvWxYz0123456789"))
    }

    @Test fun vaciaOMalFormada() {
        for (c in listOf(null, "", " ", "pk_test_", "pk_test_corta", " $test", "$test ", "$test\"", "pk_test_abc-def0123456", "x$test")) {
            assertFalse("«$c»", ClaveStripe.esValida(c))
        }
    }

    @Test fun disponiblesSoloConInterruptorYClave() {
        assertTrue(DisponibilidadPagos.calcular(test, pagosHabilitados = true))
        assertFalse(DisponibilidadPagos.calcular(test, pagosHabilitados = false))
        assertFalse(DisponibilidadPagos.calcular("", pagosHabilitados = true))
        assertFalse(DisponibilidadPagos.calcular("pk_live_51AbCdEfGhIjKlMnOpQrStUvWxYz0123456789", pagosHabilitados = true))
        assertFalse(DisponibilidadPagos.calcular(null, pagosHabilitados = true))
    }

    /** La build solo lleva una clave de test o ninguna (sin local.properties, vacía). */
    @Test fun laBuildNoLlevaNuncaUnaClaveQueNoSeaDeTest() {
        val clave = BuildConfig.STRIPE_PUBLISHABLE_KEY
        assertTrue("BuildConfig.STRIPE_PUBLISHABLE_KEY no es de test", clave.isEmpty() || ClaveStripe.esValida(clave))
    }
}
