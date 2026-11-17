package com.example.zerohaus

import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.datosPerfilReasignado
import com.example.zerohaus.Modelos.visiblesEnDirectorio
import com.example.zerohaus.ViewModel.competidoresEnCiudad
import com.example.zerohaus.ViewModel.rankingPorValoracion
import com.google.firebase.firestore.util.CustomClassMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DirectorioVisibleTest {

    private val verificado = Tecnico(id = "a", uid = "a", emailVerificado = true)
    private val sinVerificar = Tecnico(id = "b", uid = "b", emailVerificado = false)
    // Perfil anterior al campo emailVerificado: se sigue mostrando
    private val antiguo = Tecnico(id = "c", uid = "c")

    @Test
    fun `se ocultan los perfiles de cuentas sin el email verificado`() {
        assertEquals(listOf(verificado, antiguo),
            visiblesEnDirectorio(listOf(verificado, sinVerificar, antiguo), miUid = "otro"))
    }

    @Test
    fun `el profesional ve su propio perfil aunque aun no haya verificado`() {
        assertEquals(listOf(verificado, sinVerificar, antiguo),
            visiblesEnDirectorio(listOf(verificado, sinVerificar, antiguo), miUid = "b"))
    }

    @Test
    fun `sin sesion o con un uid vacio no se cuela ningun perfil oculto`() {
        val legado = sinVerificar.copy(id = "d", uid = "")
        assertEquals(listOf(antiguo), visiblesEnDirectorio(listOf(sinVerificar, legado, antiguo), miUid = null))
        assertEquals(listOf(antiguo), visiblesEnDirectorio(listOf(sinVerificar, legado, antiguo), miUid = ""))
    }

    @Test
    fun `al guardar el perfil entero no se escribe emailVerificado (solo lo pone el servidor)`() {
        val mapa = CustomClassMapper.convertToPlainJavaTypes(verificado) as Map<*, *>
        assertFalse("emailVerificado" in mapa.keys)
        assertEquals(17, mapa.size)
    }

    @Test
    fun `al reparar o clonar un perfil leido no se reescribe emailVerificado`() {
        val leido = mapOf("id" to "viejo", "uid" to "", "nombre" to "Ana", "emailVerificado" to false)
        assertEquals(mapOf("id" to "yo", "uid" to "yo", "nombre" to "Ana"), datosPerfilReasignado(leido, "yo"))
    }

    @Test
    fun `el ranking no muestra los perfiles ocultos salvo el propio`() {
        val mejor = sinVerificar.copy(rating = 5.0)
        val segundo = verificado.copy(rating = 4.0)
        assertEquals(listOf(segundo, antiguo), rankingPorValoracion(listOf(antiguo, mejor, segundo), miUid = "otro"))
        assertEquals(listOf(mejor, segundo, antiguo), rankingPorValoracion(listOf(antiguo, mejor, segundo), miUid = "b"))
    }

    @Test
    fun `la posicion en la ciudad no cuenta los perfiles ocultos de otros`() {
        val yo = verificado.copy(ciudad = "Málaga")
        val oculto = sinVerificar.copy(ciudad = "malaga", rating = 5.0)
        val otro = antiguo.copy(ciudad = "Malaga ")
        val deFuera = Tecnico(id = "d", uid = "d", ciudad = "Sevilla")
        assertEquals(listOf(yo, otro), competidoresEnCiudad(listOf(yo, oculto, otro, deFuera), yo))
        // El propio perfil cuenta aunque esté oculto o aún no aparezca en la lista
        val yoOculto = oculto
        assertEquals(listOf(otro, yoOculto), competidoresEnCiudad(listOf(otro), yoOculto))
        assertEquals(listOf(yoOculto, otro), competidoresEnCiudad(listOf(yoOculto, otro), yoOculto))
    }
}
