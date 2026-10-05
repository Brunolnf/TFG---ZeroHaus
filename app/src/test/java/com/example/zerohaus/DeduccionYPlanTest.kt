package com.example.zerohaus

import com.example.zerohaus.Modelos.Vivienda
import com.example.zerohaus.Repositorios.AlgoritmoEnergetico
import com.example.zerohaus.Repositorios.DeduccionIrpf
import com.example.zerohaus.UserInterface.deduccionPorPaso
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DeduccionYPlanTest {

    // Piso de gas de 2000 en Madrid con vidrio simple: el del informe con factura
    private fun piso() = Vivienda(
        nombre = "Test", superficie = 100, anioConstruccion = 2000,
        tipoVentanas = "Vidrio simple", aislamiento = "Aislamiento parcial",
        calefaccion = "Caldera de gas", acs = "Gas", provincia = "Madrid", orientacion = "Sur",
        iluminacion = "Mixta", tipoVivienda = "Piso interior", refrigeracion = "Sin refrigeración",
        fotovoltaica = "Sin fotovoltaica", ocupantes = 3, electrodomesticos = "Clase B-C"
    )

    private fun mejora(v: Vivienda, texto: String) =
        AlgoritmoEnergetico.mejorasAplicables(v).first { texto in it.titulo.lowercase() }

    private fun balanceFicticio(primaria: Double, demanda: Double) =
        AlgoritmoEnergetico.Balance(0.0, 0.0, 0.0, 0.0, 0.0, primaria, demanda)

    @After
    fun restablecerFecha() = DeduccionIrpf.actualizarFechaLimite("2026-12-31")

    // ── Deducción del IRPF ───────────────────────────────────────────────

    @Test
    fun `cambiar las ventanas reduce la demanda y da al menos la del 20`() {
        val v = piso()
        val d = AlgoritmoEnergetico.simular(v, listOf(mejora(v, "ventanas"))).deduccion
        assertNotNull(d)
        assertTrue(d!!.porcentaje >= 20)
    }

    @Test
    fun `la aerotermia no reduce la demanda de calefaccion`() {
        val v = piso()
        val antes = AlgoritmoEnergetico.balance(v)
        val despues = AlgoritmoEnergetico.balance(mejora(v, "aerotermia").aplicar(v))
        assertEquals(antes.demandaClimatizacion, despues.demandaClimatizacion, 0.01)
        // Pero sí la energía primaria: si deduce, es por eso y no por la demanda
        val d = DeduccionIrpf.calcular(antes, despues, 9_000.0)
        if (d != null) assertTrue(d.motivo != DeduccionIrpf.Motivo.DEMANDA)
    }

    @Test
    fun `la deduccion respeta la base maxima`() {
        val v = piso()
        val todas = AlgoritmoEnergetico.simular(v, AlgoritmoEnergetico.mejorasAplicables(v))
        val d = todas.deduccion!!
        assertEquals(40, d.porcentaje)
        assertEquals(3_000.0, d.importe, 0.5)   // 40 % de 7.500 €, aunque se inviertan más de 20.000 €
    }

    @Test
    fun `la del 20 se calcula sobre la inversion si es menor que la base`() {
        val d = DeduccionIrpf.calcular(balanceFicticio(100.0, 1_000.0), balanceFicticio(95.0, 900.0), 2_000.0)!!
        assertEquals(20, d.porcentaje)
        assertEquals(DeduccionIrpf.Motivo.DEMANDA, d.motivo)
        assertEquals(400.0, d.importe, 0.5)
    }

    @Test
    fun `seguir en B no cuenta como mejorar hasta B`() {
        // 60 → 50 kWh/m²: las dos en B y solo un 16 % menos de energía primaria
        assertNull(DeduccionIrpf.calcular(balanceFicticio(60.0, 1_000.0), balanceFicticio(50.0, 1_000.0), 3_000.0))
        // 80 (C) → 60 (B): mejora hasta B aunque baje menos de un 30 %
        val d = DeduccionIrpf.calcular(balanceFicticio(80.0, 1_000.0), balanceFicticio(60.0, 1_000.0), 3_000.0)!!
        assertEquals(DeduccionIrpf.Motivo.ETIQUETA, d.motivo)
        assertEquals(40, d.porcentaje)
    }

    @Test
    fun `sin reduccion suficiente no hay deduccion`() {
        assertNull(DeduccionIrpf.calcular(balanceFicticio(100.0, 1_000.0), balanceFicticio(98.0, 980.0), 500.0))
    }

    @Test
    fun `la deduccion caduca y Remote Config puede prorrogarla`() {
        assertTrue(DeduccionIrpf.vigente(LocalDate.of(2026, 12, 31)))
        assertFalse(DeduccionIrpf.vigente(LocalDate.of(2027, 1, 1)))
        DeduccionIrpf.actualizarFechaLimite("2027-12-31")
        assertTrue(DeduccionIrpf.vigente(LocalDate.of(2027, 6, 1)))
        DeduccionIrpf.actualizarFechaLimite("no es una fecha")
        assertTrue(DeduccionIrpf.vigente(LocalDate.of(2027, 6, 1)))   // se ignora y sigue la anterior
    }

    // ── Plan por etapas ──────────────────────────────────────────────────

    @Test
    fun `el primer paso es la mejora que antes se amortiza por si sola`() {
        val v = piso()
        val plan = AlgoritmoEnergetico.planPorEtapas(v)
        val mejorSola = AlgoritmoEnergetico.mejorasAplicables(v)
            .mapNotNull { m -> AlgoritmoEnergetico.simular(v, listOf(m)).amortizacionAnios?.let { m.titulo to it } }
            .minBy { it.second }.first
        assertEquals(mejorSola, plan.first().mejora.titulo)
    }

    @Test
    fun `la etiqueta nunca empeora de un paso a otro`() {
        val plan = AlgoritmoEnergetico.planPorEtapas(piso())
        assertTrue(plan.size >= 3)
        plan.zipWithNext().forEach { (a, b) -> assertTrue("${a.etiqueta} → ${b.etiqueta}", b.etiqueta <= a.etiqueta) }
    }

    @Test
    fun `el plan completo ahorra lo mismo que simular todos sus pasos`() {
        val v = piso()
        val plan = AlgoritmoEnergetico.planPorEtapas(v)
        val sim = AlgoritmoEnergetico.simular(v, plan.map { it.mejora })
        assertEquals(sim.ahorroEuros, plan.last().ahorroAcumulado, 0.2)
        assertEquals(sim.inversion, plan.last().inversionAcumulada, 0.01)
        assertEquals(sim.etiquetaNueva, plan.last().etiqueta)
    }

    @Test
    fun `cada porcentaje de deduccion se marca solo en el primer paso que lo alcanza`() {
        val plan = AlgoritmoEnergetico.planPorEtapas(piso())
        val marcas = deduccionPorPaso(plan, vigente = true)
        assertEquals(plan.size, marcas.size)
        // El piso de prueba llega primero al 20 % y después al 40 %, una vez cada uno
        assertEquals(listOf(20, 40), marcas.filter { it > 0 })
        val paso20 = marcas.indexOf(20)
        assertEquals(20, plan[paso20].deduccionAcumulada?.porcentaje)
        assertTrue(plan.take(paso20).all { it.deduccionAcumulada == null })
        // Fuera de plazo no se marca ninguno
        assertTrue(deduccionPorPaso(plan, vigente = false).all { it == 0 })
    }

    @Test
    fun `una vivienda ya optimizada no tiene plan`() {
        val v = piso().copy(
            tipoVentanas = "Triple", aislamiento = "Aislamiento completo", calefaccion = "Aerotermia",
            acs = "Aerotermia", iluminacion = "Mayoría LED", fotovoltaica = "Mediana (3-5 kWp)"
        )
        assertTrue(AlgoritmoEnergetico.planPorEtapas(v).isEmpty())
    }
}
