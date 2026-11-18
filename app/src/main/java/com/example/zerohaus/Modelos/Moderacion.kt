package com.example.zerohaus.Modelos

import com.google.firebase.firestore.FirebaseFirestoreException

/** Motivos de una denuncia; [id] es el valor que acepta el servidor. */
enum class MotivoDenuncia(val id: String) {
    SPAM("spam"),
    ACOSO("acoso"),
    FRAUDE("fraude"),
    INAPROPIADO("inapropiado"),
    SUPLANTACION("suplantacion"),
    OTRO("otro")
}

/** Dónde se hace la denuncia; [id] es el valor que acepta el servidor. */
enum class ContextoDenuncia(val id: String) { CHAT("chat"), PERFIL("perfil"), RESENA("resena") }

/**
 * Denuncia leída de `/denuncias` (panel del administrador). `evidencia` y
 * `estado` los pone el servidor.
 */
data class Denuncia(
    val id: String = "",
    val autor: String = "",
    val objetivo: String = "",
    val contexto: String = "",
    val referencia: String = "",
    val motivo: String = "",
    val comentario: String = "",
    val evidencia: Map<String, Any?> = emptyMap(),
    val estado: String = ESTADO_PENDIENTE,
    val fecha: Long = 0L
) {
    companion object {
        const val ESTADO_PENDIENTE = "pendiente"
        const val ESTADO_REVISADA = "revisada"
        const val ESTADO_DESCARTADA = "descartada"
    }
}

/** Usuario que he bloqueado; [nombre] es null si no se ha podido resolver. */
data class UsuarioBloqueado(val uid: String, val nombre: String? = null)

/** Resultado de intentar denunciar. */
enum class ResultadoDenuncia { ENVIADA, YA_DENUNCIADA, ERROR }

/**
 * Formato de los documentos de `/bloqueos` y `/denuncias` (las reglas de
 * Firestore los validan campo a campo: no se pueden añadir otros).
 */
object Moderacion {
    const val MAX_COMENTARIO = 1000

    fun idBloqueo(miUid: String, otroUid: String) = "${miUid}_$otroUid"
    fun idDenuncia(miUid: String, objetivoUid: String) = "${miUid}_$objetivoUid"

    fun datosBloqueo(miUid: String, otroUid: String, ahora: Long = System.currentTimeMillis()): Map<String, Any> =
        mapOf("autor" to miUid, "bloqueado" to otroUid, "fecha" to ahora)

    /** `comentario` solo se incluye si hay texto, recortado al máximo permitido. */
    fun datosDenuncia(
        miUid: String,
        objetivoUid: String,
        contexto: ContextoDenuncia,
        referencia: String,
        motivo: MotivoDenuncia,
        comentario: String,
        ahora: Long = System.currentTimeMillis()
    ): Map<String, Any> {
        val datos = mutableMapOf<String, Any>(
            "autor" to miUid,
            "objetivo" to objetivoUid,
            "contexto" to contexto.id,
            "referencia" to referencia,
            "motivo" to motivo.id,
            "fecha" to ahora
        )
        val c = comentario.trim().take(MAX_COMENTARIO)
        if (c.isNotEmpty()) datos["comentario"] = c
        return datos
    }

    /** Id de la reseña a denunciar: el guardado o, si falta, el determinista `{uid}_{tecnicoId}`. */
    fun referenciaResena(id: String, uidAutor: String, tecnicoId: String) =
        id.ifBlank { "${uidAutor}_$tecnicoId" }

    /** Lista de bloqueados con su nombre (si se conoce), por nombre y con los desconocidos al final. */
    fun combinarBloqueados(uids: Set<String>, nombres: Map<String, String>): List<UsuarioBloqueado> =
        uids.map { UsuarioBloqueado(it, nombres[it]?.takeIf { n -> n.isNotBlank() }) }
            .sortedWith(
                compareBy<UsuarioBloqueado>({ it.nombre == null }, { it.nombre?.lowercase() }, { it.uid })
            )

    const val MAX_MENSAJES_EVIDENCIA = 10

    /**
     * Resumen de la evidencia que copia el servidor. Chat: `mensajes` (lista de
     * `{texto, tipo, fecha, mediaUrl}`), una línea por mensaje (máx. 10, con
     * «[imagen]» o «[PDF]» si no hay texto). Reseña, perfil y nota: una línea
     * `clave: valor` por campo, recortada.
     */
    fun resumenEvidencia(evidencia: Map<String, Any?>, maxCampos: Int = 6, maxValor: Int = 120): String {
        fun recortar(t: String) = if (t.length > maxValor) t.take(maxValor) + "…" else t
        val lineas = evidencia.entries
            .filter { it.key != "mensajes" && it.value != null && it.value.toString().isNotBlank() }
            .take(maxCampos)
            .map { (k, v) -> "$k: " + recortar(v.toString()) }
        val mensajes = (evidencia["mensajes"] as? List<*>).orEmpty()
            .filterIsInstance<Map<*, *>>()
            .take(MAX_MENSAJES_EVIDENCIA)
            .map { m ->
                val texto = (m["texto"] as? String).orEmpty().trim()
                when {
                    texto.isNotEmpty() -> "- " + recortar(texto)
                    m["tipo"] == "imagen" -> "- [imagen]"
                    m["tipo"] == "archivo" -> "- [PDF]"
                    else -> "- [sin texto]"
                }
            }
        return (lineas + mensajes).joinToString("\n")
    }

    fun etiquetaMotivo(id: String) = when (id) {
        "spam" -> "Spam"
        "acoso" -> "Acoso"
        "fraude" -> "Fraude"
        "inapropiado" -> "Inapropiado"
        "suplantacion" -> "Suplantación"
        "otro" -> "Otro"
        else -> id.ifBlank { "?" }
    }

    fun etiquetaContexto(id: String) = when (id) {
        "chat" -> "Chat"
        "perfil" -> "Perfil"
        "resena" -> "Reseña"
        else -> id.ifBlank { "?" }
    }

    /** Un envío rechazado por las reglas (por ejemplo, hay un bloqueo entre los dos). */
    fun esPermisoDenegado(e: Throwable?): Boolean =
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
}
