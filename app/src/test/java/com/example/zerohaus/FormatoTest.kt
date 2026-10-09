package com.example.zerohaus

import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.Formato
import com.example.zerohaus.Util.codigoIdioma
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class FormatoTest {

    @After
    fun restaurar() {
        AppEstado.idioma = "Español"
        AppEstado.unidadEnergia = "kWh"
        AppEstado.unidadMoneda = "EUR"
    }

    @Test
    fun `los numeros usan los separadores del idioma de la app`() {
        AppEstado.idioma = "Español"
        assertEquals("12.345,6", Formato.numero(12345.6))
        AppEstado.idioma = "English"
        assertEquals("12,345.6", Formato.numero(12345.6))
        assertEquals("4.5", Formato.numero(4.5))
    }

    @Test
    fun `en arabe se usan cifras latinas como en las graficas`() {
        AppEstado.idioma = "العربية"
        // isDigit() también es cierto para ١٢٣: solo pasa si son 0-9
        assertEquals("1234", Formato.numero(123.4).filter { it.isDigit() })
    }

    @Test
    fun `las fechas siguen el idioma de la app y no el del sistema`() {
        val fecha = GregorianCalendar(2026, Calendar.OCTOBER, 5).time
        AppEstado.idioma = "English"
        assertEquals("05 Oct 2026", Formato.fechas("dd MMM yyyy").format(fecha))
        AppEstado.idioma = "Español"
        assertTrue(Formato.fechas("dd MMM yyyy").format(fecha).startsWith("05 oct"))
        // En árabe, también con cifras latinas
        AppEstado.idioma = "العربية"
        assertEquals("05/10/2026", Formato.fechas("dd/MM/yyyy").format(fecha))
    }

    @Test
    fun `energia, moneda y emisiones llevan unidad y periodo`() {
        AppEstado.idioma = "Español"
        assertEquals("15.000 kWh", Formato.formatEnergia(15000.0, 0))
        AppEstado.unidadEnergia = "MJ"
        assertEquals("36,0 MJ", Formato.formatEnergia(10.0))
        AppEstado.unidadMoneda = "EUR"
        assertEquals("120 €/año", Formato.formatMonedaAnual(120.0, 0))
        assertEquals("2,5 kg CO₂", Formato.formatEmisiones(2.5))
    }

    @Test
    fun `una sola opinion va en singular`() {
        assertEquals("1 opinión", Formato.cantidad(1, "opinión", "opiniones"))
        assertEquals("0 opiniones", Formato.cantidad(0, "opinión", "opiniones"))
        assertEquals("12 opiniones", Formato.cantidad(12, "opinión", "opiniones"))
    }

    @Test
    fun `cada idioma de Ajustes tiene su codigo ISO`() {
        val codigos = listOf(
            "Español", "English", "Català", "Euskara", "Galego", "Português", "Français",
            "Deutsch", "Italiano", "العربية", "中文", "Română", "Nederlands", "Polski"
        ).map { codigoIdioma(it) }
        assertEquals(14, codigos.toSet().size)
        assertEquals("es", codigoIdioma("desconocido"))
    }
}
