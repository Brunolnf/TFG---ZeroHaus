package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.ContextoDenuncia
import com.example.zerohaus.Modelos.Denuncia
import com.example.zerohaus.Modelos.Moderacion
import com.example.zerohaus.Modelos.MotivoDenuncia
import com.example.zerohaus.Modelos.ResultadoDenuncia
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source

/**
 * Denuncias y bloqueos entre usuarios.
 *
 * `/bloqueos/{miUid}_{otroUid}`: solo se leen los propios (`autor == yo`); el
 * servidor rechaza chats y mensajes si hay bloqueo en cualquier sentido.
 * `/denuncias/{miUid}_{objetivoUid}`: una por autor y objetivo; el servidor
 * añade `evidencia` y `estado`, y solo el administrador las lee todas.
 */
class RepositorioModeracion {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    /** Documento de Firestore visto por los flujos de moderación. */
    private fun remoto(ref: DocumentReference) = object : DocumentoRemoto {
        override fun existeEnServidor(callback: (Boolean?) -> Unit) {
            ref.get(Source.SERVER)
                .addOnSuccessListener { callback(it.exists()) }
                .addOnFailureListener { callback(null) }
        }

        override fun crear(datos: Map<String, Any>, callback: (ResultadoEscritura) -> Unit) {
            ref.set(datos)
                .addOnSuccessListener { callback(ResultadoEscritura.OK) }
                .addOnFailureListener {
                    callback(if (Moderacion.esPermisoDenegado(it)) ResultadoEscritura.DENEGADO else ResultadoEscritura.FALLO)
                }
        }
    }

    fun bloquear(otroUid: String, callback: (Boolean) -> Unit) {
        val miUid = uid()
        if (miUid.isBlank() || otroUid.isBlank() || miUid == otroUid) { callback(false); return }
        val ref = db.collection("bloqueos").document(Moderacion.idBloqueo(miUid, otroUid))
        FlujosModeracion.bloquear(remoto(ref), Moderacion.datosBloqueo(miUid, otroUid), callback)
    }

    fun desbloquear(otroUid: String, callback: (Boolean) -> Unit) {
        val miUid = uid()
        if (miUid.isBlank() || otroUid.isBlank()) { callback(false); return }
        db.collection("bloqueos").document(Moderacion.idBloqueo(miUid, otroUid))
            .delete()
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    /** Uids que he bloqueado, en tiempo real. [alFallar] avisa de que el listener ha muerto. */
    fun escucharMisBloqueos(alFallar: () -> Unit = {}, callback: (Set<String>) -> Unit): ListenerRegistration {
        return db.collection("bloqueos")
            .whereEqualTo("autor", uid())
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) { alFallar(); return@addSnapshotListener }
                callback(snap.documents.mapNotNull { it.getString("bloqueado") }.toSet())
            }
    }

    /**
     * Denuncia a [objetivoUid]. Solo cabe una por autor y objetivo: si el
     * documento ya existe no se escribe y se devuelve [ResultadoDenuncia.YA_DENUNCIADA].
     */
    fun denunciar(
        objetivoUid: String,
        contexto: ContextoDenuncia,
        referencia: String,
        motivo: MotivoDenuncia,
        comentario: String,
        callback: (ResultadoDenuncia) -> Unit
    ) {
        val miUid = uid()
        if (miUid.isBlank() || objetivoUid.isBlank() || miUid == objetivoUid || referencia.isBlank()) {
            callback(ResultadoDenuncia.ERROR); return
        }
        val ref = db.collection("denuncias").document(Moderacion.idDenuncia(miUid, objetivoUid))
        FlujosModeracion.denunciar(
            remoto(ref),
            Moderacion.datosDenuncia(miUid, objetivoUid, contexto, referencia, motivo, comentario),
            callback
        )
    }

    /** Administrador: denuncias de la más reciente a la más antigua (null si falla la lectura). */
    fun escucharDenuncias(callback: (List<Denuncia>?) -> Unit): ListenerRegistration {
        return db.collection("denuncias")
            .orderBy("fecha", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) { callback(null); return@addSnapshotListener }
                callback(snap.documents.map { d ->
                    @Suppress("UNCHECKED_CAST")
                    Denuncia(
                        id = d.id,
                        autor = d.getString("autor").orEmpty(),
                        objetivo = d.getString("objetivo").orEmpty(),
                        contexto = d.getString("contexto").orEmpty(),
                        referencia = d.getString("referencia").orEmpty(),
                        motivo = d.getString("motivo").orEmpty(),
                        comentario = d.getString("comentario").orEmpty(),
                        evidencia = (d.get("evidencia") as? Map<String, Any?>) ?: emptyMap(),
                        estado = d.getString("estado") ?: Denuncia.ESTADO_PENDIENTE,
                        fecha = d.getLong("fecha") ?: 0L
                    )
                })
            }
    }

    /** Administrador: pasa la denuncia a «revisada» o «descartada». */
    fun marcarEstado(denunciaId: String, estado: String, callback: (Boolean) -> Unit) {
        db.collection("denuncias").document(denunciaId)
            .update("estado", estado)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    /**
     * Nombres de los usuarios [uids]: primero los de mis chats, luego el perfil
     * público de profesional; los que no se resuelven no salen en el mapa.
     * Ver [FlujosModeracion.resolverNombres].
     */
    fun resolverNombres(uids: Set<String>, cancelado: () -> Boolean = { false }, callback: (Map<String, String>, Set<String>) -> Unit) {
        val fuente = object : FuenteNombres {
            override fun deMisChats(callback: (Map<String, String>?) -> Unit) {
                db.collection("chats").whereArrayContains("participantes", uid()).get()
                    .addOnSuccessListener { snap ->
                        val m = mutableMapOf<String, String>()
                        snap.documents.forEach { d ->
                            (d.get("nombresParticipantes") as? Map<*, *>)?.forEach { (k, v) ->
                                if (k is String && v is String) m[k] = v
                            }
                        }
                        callback(m)
                    }
                    .addOnFailureListener { callback(null) }
            }

            override fun dePerfil(uid: String, callback: (Result<String?>) -> Unit) {
                db.collection("tecnicos").whereEqualTo("uid", uid).limit(1).get()
                    .addOnSuccessListener { callback(Result.success(it.documents.firstOrNull()?.getString("nombre"))) }
                    .addOnFailureListener { callback(Result.failure(it)) }
            }
        }
        FlujosModeracion.resolverNombres(uids, fuente, cancelado, callback)
    }
}
