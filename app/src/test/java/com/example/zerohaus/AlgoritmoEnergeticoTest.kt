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
    fun `vivienda media obtiene etiqueta C o D`() {
        // Piso de 2010 (ya con el CTE), gas y aislamiento parcial, sin provincia
        // (clima neutro): queda en la frontera C/D de la escala de energía primaria
        val resultado = AlgoritmoEnergetico.calcular(viviendaBase())
        assertTrue(resultado.etiqueta, resultado.etiqueta in listOf("C", "D"))
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
    fun `consumo crece con la superficie pero menos que proporcionalmente`() {
        // La calefacción va por m², pero el ACS y los aparatos dependen de las
        // personas: el doble de superficie no es el doble de consumo
        val v50  = AlgoritmoEnergetico.calcular(viviendaBase().copy(superficie = 50))
        val v100 = AlgoritmoEnergetico.calcular(viviendaBase().copy(superficie = 100))
        assertTrue(v100.consumoEstimado > v50.consumoEstimado)
        assertTrue(v100.consumoEstimado < v50.consumoEstimado * 2)
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
        assertEquals(pequenya.energiaPrimariaM2, grande.energiaPrimariaM2, 0.2)
    }

    // ── Cada factor afecta solo a su uso ─────────────────────────────────

    @Test
    fun `la envolvente no cambia el consumo de luz de una casa de gas`() {
        val mala  = AlgoritmoEnergetico.calcular(viviendaBase().copy(tipoVentanas = "Vidrio simple", aislamiento = "Sin aislamiento"))
        val buena = AlgoritmoEnergetico.calcular(viviendaBase().copy(tipoVentanas = "Triple", aislamiento = "Aislamiento completo"))
        assertEquals(mala.consumoLuzEstimado, buena.consumoLuzEstimado, 0.1)
        assertTrue(mala.consumoEstimado > buena.consumoEstimado)
    }

    @Test
    fun `un hogar medio de gas gasta en luz lo de un hogar espanol`() {
        // ~3.000-4.000 kWh/año de electricidad (IDAE, SPAHOUSEC)
        val r = AlgoritmoEnergetico.calcular(viviendaBase().copy(provincia = "Madrid", ocupantes = 3))
        assertTrue("luz ${r.consumoLuzEstimado}", r.consumoLuzEstimado in 2_500.0..4_500.0)
    }

    @Test
    fun `la solar termica solo ahorra en el agua caliente`() {
        // Antes los factores se multiplicaban sobre todo el consumo y la solar
        // térmica aparecía ahorrando un 45 % de la factura; el ACS es ~20 %
        val r = AlgoritmoEnergetico.calcular(viviendaBase().copy(acs = "Eléctrico"))
        val solar = r.recomendaciones.first { "solar" in it.titulo.lowercase() }
        assertTrue("ahorro ${solar.ahorroEstimado} %", solar.ahorroEstimado < 25)
    }

    @Test
    fun `la aerotermia ahorra frente a una caldera de gas`() {
        val r = AlgoritmoEnergetico.calcular(viviendaBase().copy(calefaccion = "Caldera de gas"))
        assertTrue(r.recomendaciones.any { "aerotermia" in it.titulo.lowercase() })
    }

    @Test
    fun `los radiadores electricos tienen peor etiqueta que el gas`() {
        // La etiqueta va en energía primaria: 1 kWh de luz pesa más que 1 de gas
        val gas = AlgoritmoEnergetico.calcular(viviendaBase().copy(calefaccion = "Caldera de gas"))
        val joule = AlgoritmoEnergetico.calcular(viviendaBase().copy(calefaccion = "Eléctrica"))
        assertTrue(joule.energiaPrimariaM2 > gas.energiaPrimariaM2)
        assertTrue(joule.etiqueta >= gas.etiqueta)
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

    @Test
    fun `zonas climaticas de las capitales segun la tabla del CTE DB-HE`() {
        val zonas = AlgoritmoEnergetico.zonaClimaticaPorProvincia
        assertEquals(52, zonas.size)
        // Las que estaban mal antes de comprobarlas con la tabla a-Anejo B
        mapOf(
            "Asturias" to "D", "Badajoz" to "C", "Barcelona" to "C", "Ceuta" to "B",
            "Córdoba" to "B", "Gipuzkoa" to "D", "Lugo" to "D", "Palencia" to "D"
        ).forEach { (provincia, zona) -> assertEquals(provincia, zona, zonas[provincia]) }
    }
}
