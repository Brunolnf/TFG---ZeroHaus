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

    @Test
    fun `edificio nuevo consume menos que edificio antiguo`() {
        val nuevo   = AlgoritmoEnergetico.calcular(viviendaBase().copy(anioConstruccion = 2022))
        val antiguo = AlgoritmoEnergetico.calcular(viviendaBase().copy(anioConstruccion = 1960))
        assertTrue(nuevo.consumoEstimado < antiguo.consumoEstimado)
    }

    @Test
    fun `la etiqueta no depende del tamano de la vivienda`() {
        val pequenya = AlgoritmoEnergetico.calcular(viviendaBase().copy(superficie = 40))
        val grande   = AlgoritmoEnergetico.calcular(viviendaBase().copy(superficie = 300))
        assertEquals(pequenya.etiqueta, grande.etiqueta)
        assertEquals(pequenya.consumoPorM2, grande.consumoPorM2, 0.2)
    }

    @Test
    fun `vivienda media de 100 m2 tiene cifras realistas`() {
        // Un hogar español medio consume del orden de 8.000–15.000 kWh/año
        // y gasta entre ~800 y ~2.500 €/año en energía.
        val r = AlgoritmoEnergetico.calcular(viviendaBase())
        assertTrue("consumo ${r.consumoEstimado}", r.consumoEstimado in 8_000.0..20_000.0)
        assertTrue("coste ${r.costeAnual}", r.costeAnual in 800.0..2_500.0)
        assertTrue("emisiones ${r.emisiones}", r.emisiones in 1_500.0..6_000.0)
    }

    @Test
    fun `calefaccion de gas emite y cuesta distinto que electrica`() {
        val gas = AlgoritmoEnergetico.calcular(viviendaBase().copy(calefaccion = "Caldera de gas", acs = "Gas"))
        val bio = AlgoritmoEnergetico.calcular(viviendaBase().copy(calefaccion = "Biomasa", acs = "Gas"))
        assertTrue(bio.emisiones < gas.emisiones)
        assertTrue(bio.costeAnual < gas.costeAnual)
    }

    @Test
    fun `fotovoltaica reduce consumo de red y coste`() {
        val sin = AlgoritmoEnergetico.calcular(viviendaBase().copy(fotovoltaica = "Sin fotovoltaica"))
        val con = AlgoritmoEnergetico.calcular(viviendaBase().copy(fotovoltaica = "Mediana (3-5 kWp)"))
        assertTrue(con.consumoEstimado < sin.consumoEstimado)
        assertTrue(con.costeAnual < sin.costeAnual)
    }

    @Test
    fun `ahorro de cada recomendacion es coherente con el recalculo`() {
        val v = viviendaBase().copy(tipoVentanas = "Vidrio simple")
        val r = AlgoritmoEnergetico.calcular(v)
        val ventanas = r.recomendaciones.first { "ventanas" in it.titulo.lowercase() }
        val mejorada = AlgoritmoEnergetico.calcular(v.copy(tipoVentanas = "Doble acristalamiento"))
        assertEquals(r.costeAnual - mejorada.costeAnual, ventanas.ahorroEuros, 0.5)
        assertTrue(ventanas.ahorroEstimado in 1..99)
    }

    @Test
    fun `simular sin mejoras no cambia nada`() {
        val s = AlgoritmoEnergetico.simular(viviendaBase(), emptyList())
        assertEquals(s.etiquetaActual, s.etiquetaNueva)
        assertEquals(0.0, s.ahorroEuros, 0.01)
        assertEquals(null, s.amortizacionAnios)
    }

    @Test
    fun `combinar mejoras ahorra mas que la mejor por separado`() {
        val v = viviendaBase().copy(tipoVentanas = "Vidrio simple")
        val mejoras = AlgoritmoEnergetico.mejorasAplicables(v)
        val todas = AlgoritmoEnergetico.simular(v, mejoras)
        val mejorSola = mejoras.maxOf { AlgoritmoEnergetico.simular(v, listOf(it)).ahorroEuros }
        assertTrue(todas.ahorroEuros > mejorSola)
        assertTrue(todas.etiquetaNueva < todas.etiquetaActual) // "A" < "D" alfabéticamente
    }

    @Test
    fun `amortizacion es inversion entre ahorro anual`() {
        val v = viviendaBase()
        val led = AlgoritmoEnergetico.mejorasAplicables(v).first { "LED" in it.titulo }
        val s = AlgoritmoEnergetico.simular(v, listOf(led))
        assertEquals(s.inversion / s.ahorroEuros, s.amortizacionAnios!!, 0.2)
    }

    @Test
    fun `recomendaciones ordenadas de mayor a menor ahorro`() {
        val r = AlgoritmoEnergetico.calcular(viviendaBase().copy(tipoVentanas = "Vidrio simple"))
        val ahorros = r.recomendaciones.map { it.ahorroEuros }
        assertEquals(ahorros.sortedDescending(), ahorros)
    }

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
