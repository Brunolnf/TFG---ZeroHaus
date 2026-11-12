package com.example.zerohaus

import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.visiblesEnDirectorio
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
}
