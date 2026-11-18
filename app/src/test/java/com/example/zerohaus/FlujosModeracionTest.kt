package com.example.zerohaus

import com.example.zerohaus.Modelos.ResultadoDenuncia
import com.example.zerohaus.Repositorios.DocumentoRemoto
import com.example.zerohaus.Repositorios.FlujosModeracion
import com.example.zerohaus.Repositorios.FuenteNombres
import com.example.zerohaus.Repositorios.ResultadoEscritura
import org.junit.Assert.assertEquals
import org.junit.Test

class FlujosModeracionTest {

    /** Documento falso: lecturas y escritura programadas; cuenta cuántas veces se usa cada una. */
    private class Doc(
        val lecturas: MutableList<Boolean?>,
        val escritura: ResultadoEscritura = ResultadoEscritura.OK
    ) : DocumentoRemoto {
        var escrituras = 0
        override fun existeEnServidor(callback: (Boolean?) -> Unit) { callback(lecturas.removeAt(0)) }
        override fun crear(datos: Map<String, Any>, callback: (ResultadoEscritura) -> Unit) {
            escrituras++; callback(escritura)
        }
    }

    private fun denunciar(d: Doc): ResultadoDenuncia {
        var r: ResultadoDenuncia? = null
        FlujosModeracion.denunciar(d, mapOf("a" to 1)) { r = it }
        return r!!
    }

    @Test fun denunciaNuevaSeEnvia() {
        val d = Doc(mutableListOf(false))
        assertEquals(ResultadoDenuncia.ENVIADA, denunciar(d))
        assertEquals(1, d.escrituras)
    }

    @Test fun denunciaExistenteNoEscribe() {
        val d = Doc(mutableListOf(true))
        assertEquals(ResultadoDenuncia.YA_DENUNCIADA, denunciar(d))
        assertEquals(0, d.escrituras)
    }

    @Test fun sinConexionLaLecturaFallaYNoSeDenuncia() {
        val d = Doc(mutableListOf(null))
        assertEquals(ResultadoDenuncia.ERROR, denunciar(d))
        assertEquals(0, d.escrituras)
    }

    @Test fun escrituraDenegadaConDocumentoExistenteEsYaDenunciada() {
        val d = Doc(mutableListOf(false, true), ResultadoEscritura.DENEGADO)
        assertEquals(ResultadoDenuncia.YA_DENUNCIADA, denunciar(d))
    }

    @Test fun escrituraDenegadaSinDocumentoOSinConexionEsError() {
        assertEquals(ResultadoDenuncia.ERROR, denunciar(Doc(mutableListOf(false, false), ResultadoEscritura.DENEGADO)))
        assertEquals(ResultadoDenuncia.ERROR, denunciar(Doc(mutableListOf(false, null), ResultadoEscritura.DENEGADO)))
    }

    @Test fun escrituraFallidaEsError() {
        assertEquals(ResultadoDenuncia.ERROR, denunciar(Doc(mutableListOf(false), ResultadoEscritura.FALLO)))
    }

    private fun bloquear(d: Doc): Boolean {
        var r: Boolean? = null
        FlujosModeracion.bloquear(d, mapOf("a" to 1)) { r = it }
        return r!!
    }

    @Test fun bloquearNuevoOk() {
        val d = Doc(mutableListOf(false))
        assertEquals(true, bloquear(d))
        assertEquals(1, d.escrituras)
    }

    @Test fun bloquearYaBloqueadoEsExitoSinEscribir() {
        val d = Doc(mutableListOf(true))
        assertEquals(true, bloquear(d))
        assertEquals(0, d.escrituras)
    }

    @Test fun bloquearSinConexionFallaEnseguidaSinEscribir() {
        val d = Doc(mutableListOf(null))
        assertEquals(false, bloquear(d))
        assertEquals(0, d.escrituras)
    }

    @Test fun bloquearDenegadoSegunExistaOno() {
        assertEquals(true, bloquear(Doc(mutableListOf(false, true), ResultadoEscritura.DENEGADO)))
        assertEquals(false, bloquear(Doc(mutableListOf(false, false), ResultadoEscritura.DENEGADO)))
        assertEquals(false, bloquear(Doc(mutableListOf(false), ResultadoEscritura.FALLO)))
    }

    // ---- resolverNombres

    private class Fuente(
        val chats: Map<String, String>?,
        val perfiles: Map<String, String?>,
        val errores: Set<String> = emptySet()
    ) : FuenteNombres {
        val consultasPerfil = mutableListOf<String>()
        override fun deMisChats(callback: (Map<String, String>?) -> Unit) = callback(chats)
        override fun dePerfil(uid: String, callback: (Result<String?>) -> Unit) {
            consultasPerfil += uid
            callback(if (uid in errores) Result.failure(RuntimeException("x")) else Result.success(perfiles[uid]))
        }
    }

    private fun resolver(uids: Set<String>, f: Fuente): Pair<Map<String, String>, Set<String>> {
        var r: Pair<Map<String, String>, Set<String>>? = null
        FlujosModeracion.resolverNombres(uids, f) { n, fallidos -> r = n to fallidos }
        return r!!
    }

    @Test fun nombresDeChatsYPerfiles() {
        val (n, fallidos) = resolver(setOf("a", "b", "c"), Fuente(mapOf("a" to "Ana", "z" to "Otro"), mapOf("b" to "Berta")))
        assertEquals(mapOf("a" to "Ana", "b" to "Berta"), n)
        assertEquals(emptySet<String>(), fallidos)
    }

    @Test fun tareaDeChatsFallidaNoRompeYSigueConPerfiles() {
        val f = Fuente(null, mapOf("a" to "Ana"))
        val (n, _) = resolver(setOf("a", "b"), f)
        assertEquals(mapOf("a" to "Ana"), n)
        assertEquals(setOf("a", "b"), f.consultasPerfil.toSet())
    }

    @Test fun perfilesFallidosBajanElContadorYSeDevuelvenComoFallidos() {
        val (n, fallidos) = resolver(setOf("a", "b", "c"), Fuente(emptyMap(), mapOf("c" to null), errores = setOf("a", "b")))
        assertEquals(emptyMap<String, String>(), n)
        assertEquals(setOf("a", "b"), fallidos)   // «c» se leyó y no tiene nombre: no es un fallo
    }

    @Test fun canceladoNoLlamaAlCallback() {
        var llamadas = 0
        FlujosModeracion.resolverNombres(setOf("a"), Fuente(emptyMap(), emptyMap()), cancelado = { true }) { _, _ -> llamadas++ }
        assertEquals(0, llamadas)
    }
}
