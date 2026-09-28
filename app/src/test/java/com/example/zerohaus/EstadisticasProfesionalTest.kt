package com.example.zerohaus

import com.example.zerohaus.Repositorios.ContadoresDia
import com.example.zerohaus.Repositorios.EstadisticasProfesional
import com.example.zerohaus.Repositorios.RepositorioEstadisticas
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class EstadisticasProfesionalTest {

    private fun diaHace(n: Int): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.DAY_OF_YEAR, -n)
        return RepositorioEstadisticas.claveDia(cal)
    }

    @Test
    fun `la clave del dia tiene el formato yyyyMMdd que validan las reglas`() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { set(2026, Calendar.MARCH, 7) }
        assertEquals(20260307, RepositorioEstadisticas.claveDia(cal))
    }

    @Test
    fun `la serie diaria tiene un dia por posicion, termina hoy y rellena con ceros`() {
        val e = EstadisticasProfesional(mapOf(diaHace(0) to ContadoresDia(visitas = 5), diaHace(2) to ContadoresDia(chats = 1)))
        val serie = e.serieDiaria(14)
        assertEquals(14, serie.size)
        assertEquals(diaHace(0), serie.last().first)
        assertEquals(diaHace(13), serie.first().first)
        assertEquals(5, serie.last().second.visitas)
        assertEquals(1, serie[11].second.chats)
        assertEquals(0, serie[5].second.visitas)
    }

    @Test
    fun `los ultimos dias suman solo dentro de la ventana`() {
        val e = EstadisticasProfesional(
            mapOf(
                diaHace(0) to ContadoresDia(visitas = 3, chats = 1),
                diaHace(29) to ContadoresDia(visitas = 2, llamadas = 1),
                diaHace(30) to ContadoresDia(visitas = 100) // fuera de los 30 días
            )
        )
        val mes = e.ultimosDias(30)
        assertEquals(5, mes.visitas)
        assertEquals(2, mes.contactos)
    }
}
