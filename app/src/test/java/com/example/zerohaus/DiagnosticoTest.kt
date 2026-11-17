package com.example.zerohaus

import com.example.zerohaus.Util.Diagnostico
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DiagnosticoTest {

    @Test
    fun `sin sesion el identificador esta vacio`() {
        assertEquals("", Diagnostico.identificadorAnonimo(null))
        assertEquals("", Diagnostico.identificadorAnonimo(""))
    }

    @Test
    fun `el identificador es el sha256 hexadecimal del uid`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Diagnostico.identificadorAnonimo("abc")
        )
    }

    @Test
    fun `es estable, distinto por uid y no contiene el uid`() {
        val uid = "uidDeEjemplo123"
        val id = Diagnostico.identificadorAnonimo(uid)
        assertEquals(64, id.length)
        assertEquals(id, Diagnostico.identificadorAnonimo(uid))
        assertNotEquals(id, Diagnostico.identificadorAnonimo("otroUid"))
        assertFalse(id.contains(uid))
    }
}
