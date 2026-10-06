package com.example.zerohaus

import com.example.zerohaus.Repositorios.AlgoritmoEnergetico
import com.example.zerohaus.Repositorios.RepositorioTecnicos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UbicacionesTest {

    @Test
    fun `todas las provincias del formulario de vivienda tienen coordenadas en España`() {
        AlgoritmoEnergetico.provinciasOrdenadas.forEach { provincia ->
            val coords = RepositorioTecnicos.coordenadasDeProvincia(provincia)
            assertNotNull("Sin coordenadas: $provincia", coords)
            val (lat, lng) = coords!!
            assertTrue("Fuera de España: $provincia", lat in 27.0..44.0 && lng in -19.0..5.0)
        }
    }

    @Test
    fun `las provincias con nombre distinto al de su capital usan la capital`() {
        assertEquals(RepositorioTecnicos.coordenadasDeCiudad("oviedo"), RepositorioTecnicos.coordenadasDeProvincia("Asturias"))
        assertEquals(RepositorioTecnicos.coordenadasDeCiudad("palma"), RepositorioTecnicos.coordenadasDeProvincia("Illes Balears"))
        assertEquals(RepositorioTecnicos.coordenadasDeCiudad("bilbao"), RepositorioTecnicos.coordenadasDeProvincia("Bizkaia"))
    }

    @Test
    fun `una ciudad se reconoce sin tildes ni mayusculas y una desconocida no se inventa`() {
        assertEquals(RepositorioTecnicos.coordenadasDeCiudad("Cádiz"), RepositorioTecnicos.coordenadasDeCiudad("CADIZ"))
        assertNull(RepositorioTecnicos.coordenadasDeCiudad(""))
        assertNull(RepositorioTecnicos.coordenadasDeCiudad("Springfield"))
    }

    @Test
    fun `una ciudad dentro de un texto se reconoce por palabras completas`() {
        val madrid = RepositorioTecnicos.coordenadasDeCiudad("madrid")
        assertEquals(madrid, RepositorioTecnicos.coordenadasDeCiudad("Pza. Mayor, Madrid"))
        assertEquals(RepositorioTecnicos.coordenadasDeCiudad("palencia"), RepositorioTecnicos.coordenadasDeCiudad("Palencia capital"))
        assertEquals(RepositorioTecnicos.coordenadasDeCiudad("hospitalet"), RepositorioTecnicos.coordenadasDeCiudad("L'Hospitalet de Llobregat"))
        assertEquals(RepositorioTecnicos.coordenadasDeCiudad("velez malaga"), RepositorioTecnicos.coordenadasDeCiudad("Vélez-Málaga"))
        // La más larga: San Sebastián de los Reyes está en Madrid, no es Donostia
        assertEquals(
            RepositorioTecnicos.coordenadasDeCiudad("san sebastián de los reyes"),
            RepositorioTecnicos.coordenadasDeCiudad("San Sebastián de los Reyes (Madrid)")
        )
    }

    @Test
    fun `un trozo de palabra no es una ciudad`() {
        // Antes "Villaviciosa de Odón" acababa en Vic (Barcelona) y "Lugones" en Lugo
        assertNull(RepositorioTecnicos.coordenadasDeCiudad("Villaviciosa de Odón"))
        assertNull(RepositorioTecnicos.coordenadasDeCiudad("Lugones"))
        assertNull(RepositorioTecnicos.coordenadasDeCiudad("a"))
    }
}
