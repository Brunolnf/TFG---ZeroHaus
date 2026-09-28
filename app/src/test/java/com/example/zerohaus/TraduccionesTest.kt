package com.example.zerohaus

import com.example.zerohaus.Modelos.Especialidades
import com.example.zerohaus.Modelos.Vivienda
import com.example.zerohaus.Repositorios.AlgoritmoEnergetico
import com.example.zerohaus.Util.AppCadenas
import com.example.zerohaus.Util.TextosEnergia
import com.example.zerohaus.Util.getCadenas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garantiza que la app está completa en los 14 idiomas y que ningún valor
 * guardado en español (opciones de la vivienda, recomendaciones,
 * especialidades) se queda sin traducir al mostrarlo.
 */
class TraduccionesTest {

    private val idiomas = listOf(
        "Español", "English", "Català", "Euskara", "Galego", "Português", "Français",
        "Deutsch", "Italiano", "العربية", "中文", "Română", "Nederlands", "Polski"
    )
    private val cadenas: List<AppCadenas> = idiomas.map { getCadenas(it) }

    // Todas las opciones que puede tener una vivienda (Preestudio y Mis viviendas)
    private val opcionesVivienda = listOf(
        "Vidrio simple", "Doble acristalamiento", "Triple",
        "Sin aislamiento", "Aislamiento parcial", "Aislamiento completo",
        "Caldera de gas", "Eléctrica", "Aerotermia", "Biomasa", "Sin calefacción",
        "Gas", "Eléctrico", "Solar térmica", "Sin ACS",
        "Norte", "Sur", "Este", "Oeste", "Noreste", "Noroeste", "Sureste", "Suroeste"
    ) + AlgoritmoEnergetico.opcionesIluminacion +
        AlgoritmoEnergetico.opcionesTipoVivienda +
        AlgoritmoEnergetico.opcionesRefrigeracion +
        AlgoritmoEnergetico.opcionesFotovoltaica +
        AlgoritmoEnergetico.opcionesElectrodomesticos

    /** Vivienda con todo mejorable: genera todas las recomendaciones posibles. */
    private val viviendaMejorable = Vivienda(
        superficie = 90, anioConstruccion = 1970, tipoVentanas = "Vidrio simple",
        aislamiento = "Sin aislamiento", calefaccion = "Caldera de gas", acs = "Gas",
        iluminacion = "Mayoría halógenas o incandescentes", fotovoltaica = "Sin fotovoltaica",
        refrigeracion = "A/A convencional o antiguo", electrodomesticos = "Clase D o antiguos"
    )

    @Test
    fun `hay 14 idiomas distintos y el español es el de respaldo`() {
        assertEquals(14, cadenas.distinct().size)
        assertEquals(getCadenas("Español"), getCadenas("idioma desconocido"))
    }

    @Test
    fun `los 14 idiomas tienen exactamente las mismas claves`() {
        val claves = getCadenas("Español").textos.keys
        assertTrue("debería haber cientos de textos", claves.size > 500)
        cadenas.forEachIndexed { i, c ->
            assertEquals("${idiomas[i]}: faltan claves", emptySet<String>(), claves - c.textos.keys)
            assertEquals("${idiomas[i]}: claves de más (¿erratas?)", emptySet<String>(), c.textos.keys - claves)
        }
    }

    @Test
    fun `cada propiedad de AppCadenas tiene su texto y ninguno esta vacio`() {
        val propiedades = AppCadenas::class.java.declaredMethods.filter {
            it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == String::class.java
        }
        assertEquals(getCadenas("Español").textos.size, propiedades.size)
        cadenas.forEachIndexed { i, c ->
            propiedades.forEach { getter ->
                val clave = getter.name.removePrefix("get").replaceFirstChar { it.lowercase() }
                val valor = getter.invoke(c) as String
                assertTrue("${idiomas[i]}: '$clave' está vacío", valor.isNotBlank())
                // Sin texto en ningún idioma, t() devolvería la propia clave
                assertTrue("${idiomas[i]}: '$clave' no tiene texto", valor != clave)
            }
        }
    }

    /** Si una opción cae en el `else` de la traducción, sale igual en los 14 idiomas. */
    private fun assertTraducido(valor: String, traducir: (String, AppCadenas) -> String) {
        val traducciones = cadenas.map { traducir(valor, it) }
        assertTrue("'$valor' tiene alguna traducción vacía", traducciones.all { it.isNotBlank() })
        assertTrue("'$valor' no se traduce (sale igual en todos los idiomas)", traducciones.toSet().size > 1)
    }

    @Test
    fun `todas las opciones de la vivienda se traducen`() {
        opcionesVivienda.forEach { assertTraducido(it, TextosEnergia::opcion) }
    }

    @Test
    fun `todas las recomendaciones del algoritmo se traducen`() {
        val titulos = AlgoritmoEnergetico.mejorasAplicables(viviendaMejorable).map { it.titulo } +
            // títulos de versiones anteriores que siguen en informes guardados
            listOf("Mejorar aislamiento de ventanas a doble acristalamiento", "Sustituir iluminación restante por LED")
        assertTrue(titulos.size >= 9)
        titulos.forEach { assertTraducido(it, TextosEnergia::recomendacion) }
    }

    @Test
    fun `todas las especialidades se traducen`() {
        Especialidades.TODAS.forEach { assertTraducido(it, TextosEnergia::especialidad) }
    }

    @Test
    fun `cada letra de la etiqueta tiene un estado distinto`() {
        cadenas.forEach { c ->
            val estados = "ABCDEFG".map { TextosEnergia.estado(it.toString(), c) }
            assertEquals(7, estados.toSet().size)
        }
    }
}
