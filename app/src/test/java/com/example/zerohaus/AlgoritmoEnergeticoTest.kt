package com.example.zerohaus

import com.example.zerohaus.Modelos.Vivienda
import com.example.zerohaus.Repositorios.AlgoritmoEnergetico
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgoritmoEnergeticoTest {

    private fun viviendaBase() = Vivienda(
        nombre = "Test",
        superficie = 100,
        anioConstruccion = 2010,
        tipoVentanas = "Doble acristalamiento",
        aislamiento = "Aislamiento parcial",
        calefaccion = "Caldera de gas",
        acs = "Gas",
        orientacion = "Sur"
    )

    // ── Etiquetas ──

    @Test
    fun `vivienda moderna y eficiente obtiene etiqueta A`() {
        val v = Vivienda(
            nombre = "Test", superficie = 80, anioConstruccion = 2022,
            tipoVentanas = "Triple", aislamiento = "Aislamiento completo",
            calefaccion = "Aerotermia", acs = "Aerotermia", orientacion = "Sur"
        )
        assertEquals("A", AlgoritmoEnergetico.calcular(v).etiqueta)
    }

    @Test
    fun `vivienda antigua sin mejoras obtiene etiqueta G`() {
        val v = Vivienda(
            nombre = "Test", superficie = 200, anioConstruccion = 1960,
            tipoVentanas = "Vidrio simple", aislamiento = "Sin aislamiento",
            calefaccion = "Eléctrica", acs = "Eléctrico", orientacion = "Norte"
        )
        assertEquals("G", AlgoritmoEnergetico.calcular(v).etiqueta)
    }

    @Test
    fun `vivienda media obtiene etiqueta D o E`() {
        val resultado = AlgoritmoEnergetico.calcular(viviendaBase())
        assertTrue(resultado.etiqueta in listOf("D", "E"))
    }

    // ── Orientación ──

    @Test
    fun `orientacion sur consume menos que norte`() {
        val sur   = AlgoritmoEnergetico.calcular(viviendaBase().copy(orientacion = "Sur"))
        val norte = AlgoritmoEnergetico.calcular(viviendaBase().copy(orientacion = "Norte"))
        assertTrue(sur.consumoEstimado < norte.consumoEstimado)
    }

    @Test
    fun `orientacion este y oeste producen el mismo consumo`() {
        val este  = AlgoritmoEnergetico.calcular(viviendaBase().copy(orientacion = "Este"))
        val oeste = AlgoritmoEnergetico.calcular(viviendaBase().copy(orientacion = "Oeste"))
        assertEquals(este.consumoEstimado, oeste.consumoEstimado, 0.01)
    }

    // ── Superficie ──

    @Test
    fun `mayor superficie implica mayor consumo`() {
        val pequenya = AlgoritmoEnergetico.calcular(viviendaBase().copy(superficie = 50))
        val grande   = AlgoritmoEnergetico.calcular(viviendaBase().copy(superficie = 200))
        assertTrue(grande.consumoEstimado > pequenya.consumoEstimado)
    }

    @Test
    fun `consumo escala linealmente con superficie`() {
        val v50  = AlgoritmoEnergetico.calcular(viviendaBase().copy(superficie = 50))
        val v100 = AlgoritmoEnergetico.calcular(viviendaBase().copy(superficie = 100))
        assertEquals(v100.consumoEstimado, v50.consumoEstimado * 2, 0.5)
    }

    // ── Año de construcción ──

    @Test
    fun `edificio nuevo consume menos que edificio antiguo`() {
        val nuevo   = AlgoritmoEnergetico.calcular(viviendaBase().copy(anioConstruccion = 2022))
        val antiguo = AlgoritmoEnergetico.calcular(viviendaBase().copy(anioConstruccion = 1960))
        assertTrue(nuevo.consumoEstimado < antiguo.consumoEstimado)
    }

    // ── Emisiones y coste ──

    @Test
    fun `emisiones son 22 por ciento del consumo`() {
        val r = AlgoritmoEnergetico.calcular(viviendaBase())
        assertEquals(r.consumoEstimado * 0.22, r.emisiones, 1.0)
    }

    @Test
    fun `coste anual es 15 por ciento del consumo`() {
        val r = AlgoritmoEnergetico.calcular(viviendaBase())
        assertEquals(r.consumoEstimado * 0.15, r.costeAnual, 1.0)
    }

    // ── Recomendaciones ──

    @Test
    fun `siempre recomienda LED`() {
        val r = AlgoritmoEnergetico.calcular(viviendaBase())
        assertTrue(r.recomendaciones.any { "LED" in it.titulo })
    }

    @Test
    fun `vidrio simple genera recomendacion de ventanas`() {
        val r = AlgoritmoEnergetico.calcular(viviendaBase().copy(tipoVentanas = "Vidrio simple"))
        assertTrue(r.recomendaciones.any { "ventana" in it.titulo.lowercase() })
    }

    @Test
    fun `aerotermia no genera recomendacion de calefaccion`() {
        val r = AlgoritmoEnergetico.calcular(viviendaBase().copy(calefaccion = "Aerotermia"))
        assertTrue(r.recomendaciones.none { "aerotermia" in it.titulo.lowercase() && "calefacción" in it.titulo.lowercase() })
    }

    @Test
    fun `solar termica no genera recomendacion de ACS`() {
        val r = AlgoritmoEnergetico.calcular(viviendaBase().copy(acs = "Solar térmica"))
        assertTrue(r.recomendaciones.none { "solar" in it.titulo.lowercase() && "ACS" in it.titulo })
    }

    // ── Valores de retorno ──

    @Test
    fun `consumo es positivo para cualquier vivienda valida`() {
        val r = AlgoritmoEnergetico.calcular(viviendaBase())
        assertTrue(r.consumoEstimado > 0)
    }

    @Test
    fun `etiqueta esta entre A y G`() {
        val r = AlgoritmoEnergetico.calcular(viviendaBase())
        assertTrue(r.etiqueta in listOf("A", "B", "C", "D", "E", "F", "G"))
    }
}
