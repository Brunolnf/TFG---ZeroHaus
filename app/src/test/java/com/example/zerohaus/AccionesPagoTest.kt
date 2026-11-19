package com.example.zerohaus

import com.example.zerohaus.Modelos.AccionPago
import com.example.zerohaus.Modelos.AccionPago.ACEPTAR
import com.example.zerohaus.Modelos.AccionPago.CANCELAR
import com.example.zerohaus.Modelos.AccionPago.CONFIRMAR
import com.example.zerohaus.Modelos.AccionPago.DISPUTAR
import com.example.zerohaus.Modelos.AccionPago.REINTENTAR
import com.example.zerohaus.Modelos.AccionPago.TERMINAR
import com.example.zerohaus.Modelos.AccionesPago
import com.example.zerohaus.Modelos.EstadoPago
import com.example.zerohaus.Modelos.Pago
import com.example.zerohaus.Modelos.PapelPago
import com.example.zerohaus.Modelos.PapelPago.CLIENTE
import com.example.zerohaus.Modelos.PapelPago.PROFESIONAL
import com.example.zerohaus.ViewModel.PagosEstado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class AccionesPagoTest {

    /**
     * La tabla completa, escrita a mano a partir de TRANSICIONES de
     * functions/pagos.py, más reanudar (aceptar en pendiente_pago).
     */
    private val esperado: Map<Pair<PapelPago, EstadoPago>, Set<AccionPago>> = mapOf(
        (CLIENTE to EstadoPago.SOLICITADO) to setOf(ACEPTAR, CANCELAR),
        (PROFESIONAL to EstadoPago.SOLICITADO) to setOf(CANCELAR),
        (CLIENTE to EstadoPago.PENDIENTE_PAGO) to setOf(ACEPTAR, CANCELAR),
        (PROFESIONAL to EstadoPago.PENDIENTE_PAGO) to setOf(CANCELAR),
        (CLIENTE to EstadoPago.FALLIDO) to setOf(REINTENTAR, CANCELAR),
        (PROFESIONAL to EstadoPago.FALLIDO) to setOf(CANCELAR),
        (CLIENTE to EstadoPago.RETENIDO) to setOf(DISPUTAR),
        (PROFESIONAL to EstadoPago.RETENIDO) to setOf(TERMINAR),
        (CLIENTE to EstadoPago.TERMINADO) to setOf(CONFIRMAR, DISPUTAR),
        (PROFESIONAL to EstadoPago.TERMINADO) to emptySet(),
        // En disputa decide el administrador; los finales no tienen salida para las partes
        (CLIENTE to EstadoPago.EN_DISPUTA) to emptySet(),
        (PROFESIONAL to EstadoPago.EN_DISPUTA) to emptySet(),
        (CLIENTE to EstadoPago.LIBERADO) to emptySet(),
        (PROFESIONAL to EstadoPago.LIBERADO) to emptySet(),
        (CLIENTE to EstadoPago.CANCELADO) to emptySet(),
        (PROFESIONAL to EstadoPago.CANCELADO) to emptySet(),
        (CLIENTE to EstadoPago.REEMBOLSADO) to emptySet(),
        (PROFESIONAL to EstadoPago.REEMBOLSADO) to emptySet(),
        (CLIENTE to EstadoPago.CONTRACARGO) to emptySet(),
        (PROFESIONAL to EstadoPago.CONTRACARGO) to emptySet(),
        (CLIENTE to EstadoPago.DESCONOCIDO) to emptySet(),
        (PROFESIONAL to EstadoPago.DESCONOCIDO) to emptySet(),
    )

    @Test fun laTablaEsperadaCubreTodosLosPapelesYEstados() {
        assertEquals(PapelPago.entries.size * EstadoPago.entries.size, esperado.size)
    }

    @Test fun accionesPorPapelYEstadoTablaCompleta() {
        for (papel in PapelPago.entries) for (estado in EstadoPago.entries) {
            assertEquals("$papel en $estado", esperado.getValue(papel to estado), AccionesPago.disponibles(papel, estado))
        }
    }

    @Test fun sinPapelNoHayAcciones() {
        for (estado in EstadoPago.entries) assertEquals(emptySet<AccionPago>(), AccionesPago.disponibles(null, estado))
    }

    @Test fun lasAccionesSalenEnElOrdenDelEnum() {
        assertEquals(listOf(ACEPTAR, CANCELAR), AccionesPago.disponibles(CLIENTE, EstadoPago.SOLICITADO).toList())
        assertEquals(listOf(REINTENTAR, CANCELAR), AccionesPago.disponibles(CLIENTE, EstadoPago.FALLIDO).toList())
    }

    @Test fun papelSegunElUid() {
        val p = pago(EstadoPago.SOLICITADO)
        assertEquals(CLIENTE, AccionesPago.papel(p, "cli"))
        assertEquals(PROFESIONAL, AccionesPago.papel(p, "pro"))
        assertNull(AccionesPago.papel(p, "otro"))
        assertNull(AccionesPago.papel(p, ""))
        // Un pago con la misma cuenta en los dos lados no existe; si llegara, sin acciones
        assertNull(AccionesPago.papel(p.copy(profesional = "cli"), "cli"))
    }

    @Test fun accionesDeUnPagoSegunMiUid() {
        val p = pago(EstadoPago.RETENIDO)
        assertEquals(setOf(DISPUTAR), AccionesPago.disponibles(p, "cli"))
        assertEquals(setOf(TERMINAR), AccionesPago.disponibles(p, "pro"))
        assertEquals(emptySet<AccionPago>(), AccionesPago.disponibles(p, "admin"))
    }

    @Test fun sinPagosDisponiblesNoSeOfrecePagarPeroSiLoDemas() {
        val solicitado = pago(EstadoPago.SOLICITADO)
        val fallido = pago(EstadoPago.FALLIDO)
        val apagado = PagosEstado(disponibles = false, miUid = "cli")
        assertEquals(setOf(CANCELAR), apagado.acciones(solicitado))
        assertEquals(setOf(CANCELAR), apagado.acciones(fallido))
        assertEquals(setOf(CANCELAR), apagado.acciones(pago(EstadoPago.PENDIENTE_PAGO)))
        assertEquals(setOf(DISPUTAR), apagado.acciones(pago(EstadoPago.RETENIDO)))
        val encendido = apagado.copy(disponibles = true)
        assertEquals(setOf(ACEPTAR, CANCELAR), encendido.acciones(solicitado))
        assertEquals(setOf(REINTENTAR, CANCELAR), encendido.acciones(fallido))
    }

    @Test fun estadoDesdeTexto() {
        for (e in EstadoPago.entries.filter { it != EstadoPago.DESCONOCIDO }) assertEquals(e, EstadoPago.desde(e.id))
        assertEquals(EstadoPago.DESCONOCIDO, EstadoPago.desde("otro"))
        assertEquals(EstadoPago.DESCONOCIDO, EstadoPago.desde(""))
        assertEquals(EstadoPago.DESCONOCIDO, EstadoPago.desde(null))
    }

    @Test fun estadosFinales() {
        val finales = EstadoPago.entries.filter { it.esFinal }.toSet()
        assertEquals(setOf(EstadoPago.LIBERADO, EstadoPago.CANCELADO, EstadoPago.REEMBOLSADO, EstadoPago.CONTRACARGO), finales)
    }

    @Test fun cadaAccionLlamaASuFuncion() {
        assertEquals(
            listOf("pagos_aceptar", "pagos_reintentar", "pagos_cancelar", "pagos_terminar", "pagos_confirmar", "pagos_disputar"),
            AccionPago.entries.map { it.funcion }
        )
        assertEquals(setOf(ACEPTAR, REINTENTAR), AccionPago.entries.filter { it.abreHojaDePago }.toSet())
    }

    // ══ Comparación con la tabla del servidor ══

    /** functions/pagos.py visto desde el módulo app (directorio de trabajo de los tests de Gradle). */
    private fun pagosPy(): String? =
        listOf(File("../functions/pagos.py"), File("functions/pagos.py")).firstOrNull { it.isFile }?.readText()

    @Test fun coincideConTransicionesDelServidor() {
        val py = pagosPy()
        assumeTrue("No se encuentra functions/pagos.py", py != null)
        val regla = Regex("""\("(\w+)",\s*"(\w+)"\):\s*\("(\w+)",\s*frozenset\(\{([A-Z_,\s]+)\}\)\)""")
        val transiciones = regla.findAll(py!!).associate { m ->
            (m.groupValues[1] to m.groupValues[2]) to m.groupValues[4].split(',').map { it.trim() }.toSet()
        }
        assertTrue("No se ha podido leer TRANSICIONES", transiciones.size >= 10)
        val actores = mapOf(CLIENTE to "CLIENTE", PROFESIONAL to "PROFESIONAL")
        val reanudar = AccionesPago.REANUDAR.map { (k, papeles) -> k to papeles }
        // Reanudar no es una transición del servidor: si lo fuera, sobraría aquí
        for ((k, _) in reanudar) assertTrue("${k.first.id}/${k.second.evento} ya es transición", (k.first.id to k.second.evento) !in transiciones)
        for (papel in PapelPago.entries) {
            val delServidor = transiciones.filterValues { actores.getValue(papel) in it }.keys +
                reanudar.filter { papel in it.second }.map { it.first.first.id to it.first.second.evento }
            val enLaApp = EstadoPago.entries.flatMap { e -> AccionesPago.disponibles(papel, e).map { e.id to it.evento } }.toSet()
            assertEquals("Acciones de $papel", delServidor, enLaApp)
        }
    }

    @Test fun losEstadosSonLosDelServidor() {
        val py = pagosPy()
        assumeTrue("No se encuentra functions/pagos.py", py != null)
        val bloque = Regex("""ESTADOS = frozenset\(\{([^}]*)\}\)""").find(py!!)?.groupValues?.get(1)
        assertTrue("No se ha podido leer ESTADOS", bloque != null)
        val delServidor = Regex("\"(\\w+)\"").findAll(bloque!!).map { it.groupValues[1] }.toSet()
        assertEquals(delServidor, EstadoPago.entries.filter { it != EstadoPago.DESCONOCIDO }.map { it.id }.toSet())
    }

    private fun pago(estado: EstadoPago) = Pago(
        id = "p1", cliente = "cli", profesional = "pro", chatId = "c1", descripcion = "Obra",
        importeCent = 5000, comisionCent = 500, netoCent = 4500, moneda = "eur",
        comisionPct = 10.0, liberacionDias = 7, estado = estado
    )
}
