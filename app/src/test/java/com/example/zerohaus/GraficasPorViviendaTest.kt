package com.example.zerohaus

import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Util.agruparPorVivienda
import com.example.zerohaus.Util.datosGrafica
import com.example.zerohaus.Util.grupoElegido
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GraficasPorViviendaTest {

    private fun inf(id: String, vivienda: String, nombre: String, fecha: Long, consumo: Double = 1000.0, primaria: Double = 50.0) =
        InformeEnergetico(id = id, viviendaId = vivienda, nombreVivienda = nombre, fechaGeneracion = fecha,
            consumoEstimado = consumo, energiaPrimariaM2 = primaria)

    private val piso1 = inf("p1", "piso", "Piso", 100, 5000.0)
    private val piso2 = inf("p2", "piso", "Piso", 300, 4000.0)
    private val chalet = inf("c1", "chalet", "Chalet", 200, 9000.0)

    @Test fun dosViviendasSeAgrupanYLaMasRecienteVaPrimero() {
        val grupos = agruparPorVivienda(listOf(piso1, chalet, piso2))
        assertEquals(listOf("piso", "chalet"), grupos.map { it.viviendaId })
        assertEquals(listOf("p1", "p2"), grupos[0].informes.map { it.id })
        assertEquals("Piso", grupos[0].nombre)
    }

    @Test fun elPisoMuestraEvolucionYElChaletSoloSuConsumo() {
        val grupos = agruparPorVivienda(listOf(piso1, chalet, piso2))
        val piso = datosGrafica(grupos.first { it.viviendaId == "piso" }.informes)
        val chal = datosGrafica(grupos.first { it.viviendaId == "chalet" }.informes)
        assertTrue(piso.hayEvolucion)
        // la mejora se calcula solo con los informes del piso, no con el chalet
        assertEquals(1000.0, piso.datos.first().consumoEstimado - piso.datos.last().consumoEstimado, 0.0)
        assertFalse(chal.hayEvolucion)
        assertEquals(listOf(9000.0), chal.datos.map { it.consumoEstimado })
    }

    @Test fun informesSinViviendaIdVanEnUnGrupoPropio() {
        val a = inf("a", "", "Antigua", 10)
        val b = inf("b", "", "Antigua", 20)
        val grupos = agruparPorVivienda(listOf(a, piso1, b))
        val sin = grupos.single { it.sinVivienda }
        assertEquals(listOf("a", "b"), sin.informes.map { it.id })
        assertEquals(2, grupos.size)
    }

    @Test fun laViviendaElegidaPorDefectoEsLaDelInformeMasReciente() {
        val grupos = agruparPorVivienda(listOf(piso1, chalet, piso2))
        assertEquals("piso", grupoElegido(grupos, null)?.viviendaId)
        assertEquals("chalet", grupoElegido(grupos, "chalet")?.viviendaId)
        assertEquals("piso", grupoElegido(grupos, "borrada")?.viviendaId)
        assertNull(grupoElegido(emptyList(), null))
    }

    @Test fun noMezclaMetodoAnteriorDentroDeLaVivienda() {
        val viejo = inf("v", "piso", "Piso", 50, primaria = 0.0)
        val d = datosGrafica(listOf(viejo, piso1, piso2))
        assertEquals(1, d.excluidos)
        assertEquals(listOf("p1", "p2"), d.datos.map { it.id })
    }
}
