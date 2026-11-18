package com.example.zerohaus

import com.example.zerohaus.Modelos.ContextoDenuncia
import com.example.zerohaus.Modelos.Moderacion
import com.example.zerohaus.Modelos.MotivoDenuncia
import com.example.zerohaus.Modelos.UsuarioBloqueado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModeracionTest {

    @Test fun bloqueoTieneExactamenteLosCamposDelContrato() {
        val d = Moderacion.datosBloqueo("yo", "otro", 123L)
        assertEquals(setOf("autor", "bloqueado", "fecha"), d.keys)
        assertEquals("yo", d["autor"])
        assertEquals("otro", d["bloqueado"])
        assertEquals(123L, d["fecha"])
        assertEquals("yo_otro", Moderacion.idBloqueo("yo", "otro"))
    }

    @Test fun denunciaSinComentarioNoLlevaElCampo() {
        val d = Moderacion.datosDenuncia("yo", "otro", ContextoDenuncia.CHAT, "chat1", MotivoDenuncia.SPAM, "   ", 5L)
        assertEquals(setOf("autor", "objetivo", "contexto", "referencia", "motivo", "fecha"), d.keys)
        assertEquals("chat", d["contexto"])
        assertEquals("spam", d["motivo"])
        assertEquals("chat1", d["referencia"])
        assertEquals(5L, d["fecha"])
        assertEquals("yo_otro", Moderacion.idDenuncia("yo", "otro"))
    }

    @Test fun denunciaConComentarioLoRecortaAlMaximo() {
        val largo = "a".repeat(1500)
        val d = Moderacion.datosDenuncia("yo", "otro", ContextoDenuncia.RESENA, "r1", MotivoDenuncia.SUPLANTACION, " $largo ", 5L)
        assertEquals(1000, (d["comentario"] as String).length)
        assertEquals("resena", d["contexto"])
        assertEquals("suplantacion", d["motivo"])
    }

    @Test fun motivosYContextosCoincidenConElServidor() {
        assertEquals(
            listOf("spam", "acoso", "fraude", "inapropiado", "suplantacion", "otro"),
            MotivoDenuncia.values().map { it.id }
        )
        assertEquals(listOf("chat", "perfil", "resena"), ContextoDenuncia.values().map { it.id })
    }

    @Test fun uidDePerfilUsaElCampoUidYSoloSiFaltaElIdDelDocumento() {
        assertEquals("uidReal", Moderacion.uidDePerfil("uidReal", "docAntiguo"))
        assertEquals("docId", Moderacion.uidDePerfil("", "docId"))
    }

    @Test fun referenciaDeResenaUsaSuIdOElDeterminista() {
        assertEquals("abc", Moderacion.referenciaResena("abc", "autor", "tec"))
        assertEquals("autor_tec", Moderacion.referenciaResena("", "autor", "tec"))
    }

    @Test fun combinarBloqueadosOrdenaPorNombreConLosDesconocidosAlFinal() {
        val l = Moderacion.combinarBloqueados(
            setOf("u3", "u1", "u2"),
            mapOf("u1" to "Zoe", "u2" to "ana", "u3" to " ")
        )
        assertEquals(listOf("u2", "u1", "u3"), l.map { it.uid })
        assertNull(l.last().nombre)
        assertEquals(UsuarioBloqueado("u2", "ana"), l.first())
    }

    @Test fun resumenDeEvidenciaRecortaYOmiteVacios() {
        val r = Moderacion.resumenEvidencia(
            mapOf("texto" to "x".repeat(200), "vacio" to "", "nulo" to null, "autor" to "Pepe"),
            maxCampos = 6, maxValor = 10
        )
        assertEquals("texto: xxxxxxxxxx…\nautor: Pepe", r)
        assertEquals("", Moderacion.resumenEvidencia(emptyMap()))
    }
}
