package com.example.zerohaus

import com.example.zerohaus.Modelos.FormatoPago
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatoPagoTest {

    private val nbsp = ' '

    @Test fun centimosAEuros() {
        assertEquals("12,34${nbsp}€", FormatoPago.importe(1234))
        assertEquals("0,00${nbsp}€", FormatoPago.importe(0))
        assertEquals("0,05${nbsp}€", FormatoPago.importe(5))
        assertEquals("10,00${nbsp}€", FormatoPago.importe(1000))
        assertEquals("999,99${nbsp}€", FormatoPago.importe(99_999))
    }

    @Test fun millaresConPunto() {
        assertEquals("1.234,56${nbsp}€", FormatoPago.importe(123_456))
        assertEquals("3.000,00${nbsp}€", FormatoPago.importe(300_000))
        assertEquals("10.000,00${nbsp}€", FormatoPago.importe(1_000_000))
    }

    @Test fun sinErroresDeComaFlotante() {
        // 0,1 + 0,2 en double no es 0,3; aquí no pasa por double
        assertEquals("0,30${nbsp}€", FormatoPago.importe(30))
        assertEquals("92.233.720.368.547.758,07${nbsp}€", FormatoPago.importe(Long.MAX_VALUE))
    }

    @Test fun monedaEnMayusculasOMinusculas() {
        assertEquals("12,34${nbsp}€", FormatoPago.importe(1234, "EUR"))
        assertEquals("12,34${nbsp}USD", FormatoPago.importe(1234, "usd"))
    }

    @Test fun negativo() {
        assertEquals("-12,34${nbsp}€", FormatoPago.importe(-1234))
    }

    @Test fun porcentajeDeComision() {
        assertEquals("10${nbsp}%", FormatoPago.porcentaje(10.0))
        assertEquals("1,13${nbsp}%", FormatoPago.porcentaje(1.13))
        assertEquals("0${nbsp}%", FormatoPago.porcentaje(0.0))
        assertEquals("12,5${nbsp}%", FormatoPago.porcentaje(12.5))
    }

    @Test fun textoACentimos() {
        assertEquals(1200L, FormatoPago.centimosDesdeTexto("12"))
        assertEquals(1250L, FormatoPago.centimosDesdeTexto("12,5"))
        assertEquals(1234L, FormatoPago.centimosDesdeTexto("12,34"))
        assertEquals(1234L, FormatoPago.centimosDesdeTexto(" 12.34 "))
        assertEquals(1L, FormatoPago.centimosDesdeTexto("0,01"))
        assertEquals(999_999_999L, FormatoPago.centimosDesdeTexto("9999999,99"))
    }

    @Test fun textoQueNoEsUnImporte() {
        for (t in listOf("", " ", "abc", "12,345", "1.234,56", "-5", "+5", "1e3", "12,", ",5", "12 34", "99999999")) {
            assertNull("«$t»", FormatoPago.centimosDesdeTexto(t))
        }
    }

    @Test fun centimosQueSeMandanComoInt() {
        assertEquals(1234, FormatoPago.centimosParaEnviar(1234))
        assertEquals(Int.MAX_VALUE, FormatoPago.centimosParaEnviar(Int.MAX_VALUE.toLong()))
        assertNull(FormatoPago.centimosParaEnviar(0))
        assertNull(FormatoPago.centimosParaEnviar(-1))
        assertNull(FormatoPago.centimosParaEnviar(Int.MAX_VALUE.toLong() + 1))
    }
}
