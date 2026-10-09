package com.example.zerohaus

import com.example.zerohaus.Modelos.Especialidades
import com.example.zerohaus.Modelos.Vivienda
import com.example.zerohaus.Repositorios.AlgoritmoEnergetico
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EspecialidadesTest {

    @Test
    fun `los valores del catalogo se reconocen tal cual y sin tildes ni mayusculas`() {
        Especialidades.TODAS.forEach { assertEquals(it, Especialidades.normalizar(it)) }
        assertEquals(Especialidades.CALEFACCION, Especialidades.normalizar("CALEFACCION"))
        assertEquals(Especialidades.CERTIFICACION, Especialidades.normalizar("  certificación "))
    }

    @Test
    fun `los perfiles antiguos escritos a mano se interpretan`() {
        assertEquals(Especialidades.FOTOVOLTAICA, Especialidades.normalizar("placas solares"))
        assertEquals(Especialidades.AEROTERMIA, Especialidades.normalizar("Bomba de calor"))
        assertEquals(Especialidades.AISLAMIENTO, Especialidades.normalizar("aislamientos SATE"))
        assertEquals(Especialidades.VENTANAS, Especialidades.normalizar("carpintería de aluminio"))
        assertEquals(Especialidades.REHABILITACION, Especialidades.normalizar("reformas integrales"))
        assertEquals(Especialidades.CERTIFICACION, Especialidades.normalizar("certificados energéticos"))
    }

    @Test
    fun `la solar termica no se confunde con la fotovoltaica`() {
        assertEquals(Especialidades.CALEFACCION, Especialidades.normalizar("solar térmica"))
        assertEquals(Especialidades.FOTOVOLTAICA, Especialidades.normalizar("solar"))
    }

    @Test
    fun `un texto desconocido o vacio no inventa especialidad`() {
        assertNull(Especialidades.normalizar("fontanería"))
        assertNull(Especialidades.normalizar("   "))
    }

    @Test
    fun `canonicas quita duplicados y respeta el orden del catalogo`() {
        val resultado = Especialidades.canonicas(listOf("placas solares", "Aislamiento", "fotovoltaica", "xyz"))
        assertEquals(listOf(Especialidades.AISLAMIENTO, Especialidades.FOTOVOLTAICA), resultado)
    }

    @Test
    fun `al mostrarlas no se repiten y las desconocidas se quedan como estan`() {
        val resultado = Especialidades.paraMostrar(listOf("Calefacción", "Biomasa", "suelo radiante", " Ventilacion mecanica ", ""))
        assertEquals(listOf(Especialidades.CALEFACCION, Especialidades.BIOMASA, "Ventilacion mecanica"), resultado)
    }

    @Test
    fun `cada mejora que hace un profesional apunta a su especialidad`() {
        val v = Vivienda(
            superficie = 90, tipoVentanas = "Vidrio simple", aislamiento = "Sin aislamiento",
            calefaccion = "Caldera de gas", acs = "Gas", fotovoltaica = "Sin fotovoltaica",
            refrigeracion = "A/A convencional o antiguo", iluminacion = "Mayoría halógenas o incandescentes",
            electrodomesticos = "Clase D o antiguos"
        )
        val porTitulo = AlgoritmoEnergetico.mejorasAplicables(v).associate { it.titulo to Especialidades.paraRecomendacion(it.titulo) }
        assertEquals(Especialidades.VENTANAS, porTitulo["Mejorar ventanas a doble acristalamiento"])
        assertEquals(Especialidades.AISLAMIENTO, porTitulo["Completar el aislamiento térmico de la vivienda"])
        assertEquals(Especialidades.AEROTERMIA, porTitulo["Instalar aerotermia como sistema de calefacción"])
        assertEquals(Especialidades.CALEFACCION, porTitulo["Instalar solar térmica para ACS"])
        assertEquals(Especialidades.FOTOVOLTAICA, porTitulo["Instalar autoconsumo fotovoltaico"])
        // Cambiar bombillas o electrodomésticos no necesita un profesional
        assertNull(porTitulo["Sustituir halógenas e incandescentes por LED"])
        assertNull(porTitulo["Renovar electrodomésticos a etiqueta A o superior"])
    }
}
