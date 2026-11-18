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

    @Test fun bloquearNuevoOk() = assertEquals(true, bloquear(Doc(mutableListOf())))

    @Test fun bloquearYaBloqueadoCuentaComoExito() {
        assertEquals(true, bloquear(Doc(mutableListOf(true), ResultadoEscritura.DENEGADO)))
    }

    @Test fun bloquearDenegadoSinDocumentoOFalloEsError() {
        assertEquals(false, bloquear(Doc(mutableListOf(false), ResultadoEscritura.DENEGADO)))
        assertEquals(false, bloquear(Doc(mutableListOf(), ResultadoEscritura.FALLO)))
    }

    // ---- resolverNombres

    private class Fuente(
        val chats: Map<String, String>?,
        val perfiles: Map<String, String?>
    ) : FuenteNombres {
        val consultasPerfil = mutableListOf<String>()
        override fun deMisChats(callback: (Map<String, String>?) -> Unit) = callback(chats)
        override fun dePerfil(uid: String, callback: (String?) -> Unit) {
            consultasPerfil += uid; callback(perfiles[uid])
        }
    }

    @Test fun nombresDeChatsYPerfiles() {
        var r: Map<String, String>? = null
        FlujosModeracion.resolverNombres(setOf("a", "b", "c"), Fuente(mapOf("a" to "Ana", "z" to "Otro"), mapOf("b" to "Berta"))) { r = it }
        assertEquals(mapOf("a" to "Ana", "b" to "Berta"), r)
    }

    @Test fun tareaDeChatsFallidaNoRompeYSigueConPerfiles() {
        var r: Map<String, String>? = null
        val f = Fuente(null, mapOf("a" to "Ana"))
        FlujosModeracion.resolverNombres(setOf("a", "b"), f) { r = it }
        assertEquals(mapOf("a" to "Ana"), r)
        assertEquals(setOf("a", "b"), f.consultasPerfil.toSet())
    }

    @Test fun perfilesFallidosTambienBajanElContador() {
        var llamadas = 0
        var r: Map<String, String>? = null
        FlujosModeracion.resolverNombres(setOf("a", "b"), Fuente(emptyMap(), mapOf("a" to null, "b" to null))) { llamadas++; r = it }
        assertEquals(1, llamadas)
        assertEquals(emptyMap<String, String>(), r)
    }

    @Test fun canceladoNoLlamaAlCallback() {
        var llamadas = 0
        FlujosModeracion.resolverNombres(setOf("a"), Fuente(emptyMap(), emptyMap()), cancelado = { true }) { llamadas++ }
        assertEquals(0, llamadas)
    }
}
