package com.example.zerohaus

import com.example.zerohaus.Modelos.Vivienda
import com.example.zerohaus.Repositorios.AlgoritmoEnergetico
import com.example.zerohaus.Repositorios.PreciosEnergia
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreciosEnergiaTest {

    // Todo eléctrico: el coste depende solo del precio de la luz
    private fun viviendaElectrica() = Vivienda(
        nombre = "Test", superficie = 90, anioConstruccion = 2000,
        tipoVentanas = "Doble acristalamiento", aislamiento = "Aislamiento parcial",
        calefaccion = "Eléctrica", acs = "Eléctrico", orientacion = "Sur"
    )

    @After
    fun restablecer() = PreciosEnergia.restablecer()

    @Test
    fun `sin factura se usa el precio medio de la luz`() {
        val r = AlgoritmoEnergetico.calcular(viviendaElectrica())
        assertEquals(PreciosEnergia.ELECTRICIDAD_DEFECTO, r.precioLuz, 1e-9)
    }

    @Test
    fun `con factura el coste usa el precio real`() {
        val sin = AlgoritmoEnergetico.calcular(viviendaElectrica())
        val con = AlgoritmoEnergetico.calcular(viviendaElectrica().copy(precioLuzFactura = 0.34))
        assertEquals(0.34, con.precioLuz, 1e-9)
        // El doble de precio, el doble de coste (redondeos aparte)
        assertEquals(sin.costeAnual * 2, con.costeAnual, 1.0)
        // El consumo y la etiqueta no dependen del precio
        assertEquals(sin.consumoEstimado, con.consumoEstimado, 1e-9)
        assertEquals(sin.etiqueta, con.etiqueta)
    }

    @Test
    fun `un precio de factura absurdo se ignora`() {
        val r = AlgoritmoEnergetico.calcular(viviendaElectrica().copy(precioLuzFactura = 25.0))
        assertEquals(PreciosEnergia.ELECTRICIDAD_DEFECTO, r.precioLuz, 1e-9)
    }

    @Test
    fun `los precios remotos cambian el coste y los invalidos se ignoran`() {
        val antes = AlgoritmoEnergetico.calcular(viviendaElectrica()).costeAnual
        PreciosEnergia.actualizar(electricidad = 0.20, gas = -1.0, biomasa = 0.0)
        assertEquals(0.20, PreciosEnergia.electricidad, 1e-9)
        assertEquals(PreciosEnergia.GAS_DEFECTO, PreciosEnergia.gas, 1e-9)
        assertEquals(PreciosEnergia.BIOMASA_DEFECTO, PreciosEnergia.biomasa, 1e-9)
        assertTrue(AlgoritmoEnergetico.calcular(viviendaElectrica()).costeAnual > antes)
    }

    @Test
    fun `el consumo de luz estimado es parte del consumo total`() {
        val r = AlgoritmoEnergetico.calcular(viviendaElectrica().copy(calefaccion = "Caldera de gas", acs = "Gas"))
        assertTrue(r.consumoLuzEstimado > 0)
        assertTrue(r.consumoLuzEstimado < r.consumoEstimado)
    }
}
