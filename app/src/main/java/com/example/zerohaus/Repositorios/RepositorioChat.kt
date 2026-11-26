package com.example.zerohaus.Repositorios

import android.net.Uri
import com.example.zerohaus.Modelos.Chat
import com.example.zerohaus.Modelos.MensajeChat
import com.example.zerohaus.Modelos.Moderacion
import com.example.zerohaus.Util.getOrTimeout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata

/**
 * Campos del chat que cambian al enviar un mensaje: último mensaje, su fecha y
 * un no leído más para el receptor. Son los únicos que las reglas dejan tocar
 * (`cambioDeChatValido`).
 */
internal fun cambiosDeChat(ultimoMensaje: String, receptorUid: String, ahora: Long): Map<String, Any> = mapOf(
    "ultimoMensaje" to ultimoMensaje,
    "fechaUltimoMensaje" to ahora,
    "noLeidosPor.$receptorUid" to FieldValue.increment(1)
)

/**
 * Chat entre clientes y profesionales: crear o recuperar conversaciones,
 * escuchar mensajes en tiempo real (paginados), enviar texto, imágenes y
 * archivos (Cloud Storage) y marcar como leídos.
 */
class RepositorioChat {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

  

    companion object {
        @Volatile private var nombreCacheado: String? = null
        @Volatile private var uidCacheado: String? = null

        /** Invalida la caché del nombre — llamar en logout. */
        fun limpiarCacheNombre() {
            nombreCacheado = null
            uidCacheado = null
        }
    }

    private fun obtenerNombre(callback: (String) -> Unit) {
        val miUid = uid()
        if (uidCacheado == miUid && nombreCacheado != null) {
            callback(nombreCacheado!!); return
        }
        db.collection("usuarios").document(miUid).get()
            .addOnSuccessListener { userDoc ->
                val nombre = userDoc.getString("nombre") ?: "Usuario"
                uidCacheado = miUid
                nombreCacheado = nombre
                callback(nombre)
            }
            .addOnFailureListener { callback("Usuario") }
    }

    /** El receptor es el otro participante: lo que ya sabe el ViewModel, o si no, una lectura del chat. */
    private fun resolverReceptor(chatId: String, otroUid: String?, callback: (String?) -> Unit) {
        if (!otroUid.isNullOrBlank()) { callback(otroUid); return }
        val miUid = uid()
        db.collection("chats").document(chatId).get()
            .addOnSuccessListener { chatDoc ->
                val participantes = (chatDoc.get("participantes") as? List<*>)
                    ?.filterIsInstance<String>().orEmpty()
                callback(participantes.firstOrNull { it != miUid })
            }
            .addOnFailureListener { callback(null) }
    }

    /**
     * Escribe el mensaje y la actualización del chat (último mensaje y no leídos) en
     * un mismo WriteBatch: o van los dos o ninguno (antes, si fallaba la segunda
     * escritura, el mensaje existía pero el chat no lo mostraba ni avisaba al otro).
     * Las reglas evalúan cada escritura por separado; con un bloqueo se rechaza el
     * lote entero y [alDenegar] avisa al usuario. NO crea la notificación ni envía
     * push: de eso se encarga la Cloud Function `on_message_created`.
     */
    private fun enviarEnLote(
        chatId: String,
        mensajeRef: DocumentReference,
        mensaje: MensajeChat,
        resumen: String,
        otroUid: String?,
        alDenegar: () -> Unit,
        callback: (Boolean) -> Unit
    ) {
        resolverReceptor(chatId, otroUid) { receptor ->
            if (receptor == null) { callback(false); return@resolverReceptor }
            db.batch()
                .set(mensajeRef, mensaje)
                .update(db.collection("chats").document(chatId), cambiosDeChat(resumen, receptor, System.currentTimeMillis()))
                .commit()
                .addOnSuccessListener { callback(true) }
                .addOnFailureListener { e ->
                    if (Moderacion.esPermisoDenegado(e)) alDenegar()
                    callback(false)
                }
        }
    }

    fun obtenerOCrearChat(
        otroUid: String,
        otroNombre: String,
        miNombre: String,
        callback: (String) -> Unit
    ) {
        val miUid = uid()
        if (miUid.isBlank() || otroUid.isBlank()) {
            callback(""); return
        }

        db.collection("chats")
            .whereArrayContains("participantes", miUid)
            .get()
            .addOnSuccessListener { snap ->
                val existente = snap.documents.firstOrNull { doc ->
                    val p = doc.get("participantes") as? List<*>
                    p?.contains(otroUid) == true
                }

                if (existente != null) {
                    callback(existente.id)
                } else {
                    val ref = db.collection("chats").document()
                    val chat = Chat(
                        id = ref.id,
                        participantes = listOf(miUid, otroUid),
                        nombresParticipantes = mapOf(
                            miUid to miNombre,
                            otroUid to otroNombre
                        ),
                        noLeidosPor = mapOf(miUid to 0, otroUid to 0)
                    )
                    ref.set(chat)
                        .addOnSuccessListener { callback(ref.id) }
                        // Si falla el set, devolvemos cadena vacía para que la UI
                        // pueda salir del estado de carga sin colgarse.
                        .addOnFailureListener { callback("") }
                }
            }
            // Si la query inicial falla, hay que devolver algo o la UI se cuelga.
            .addOnFailureListener { callback("") }
    }

    /**
     * Id del chat con [otroUid] en el que han escrito los dos (el profesional
     * ha contestado), o null. Es el requisito para poder valorarle.
     */
    fun buscarConversacionCon(otroUid: String, callback: (String?) -> Unit) {
        val miUid = uid()
        if (miUid.isBlank() || otroUid.isBlank()) { callback(null); return }
        db.collection("chats")
            .whereArrayContains("participantes", miUid)
            .getOrTimeout { snap ->
                val chat = snap?.documents
                    ?.mapNotNull { it.toObject(Chat::class.java)?.copy(id = it.id) }
                    ?.filter { otroUid in it.participantes && miUid in it.escribieron && otroUid in it.escribieron }
                    ?.maxByOrNull { it.fechaUltimoMensaje }
                callback(chat?.id)
            }
    }

    fun escucharChats(callback: (List<Chat>) -> Unit): ListenerRegistration {
        return db.collection("chats")
            .whereArrayContains("participantes", uid())
            .addSnapshotListener { snap, _ ->
                val lista = snap?.documents
                    ?.mapNotNull { it.toObject(Chat::class.java) }
                    ?.sortedByDescending { it.fechaUltimoMensaje }
                    ?: emptyList()
                callback(lista)
            }
    }

    /** Los [limite] mensajes más recientes, ordenados del más antiguo al más nuevo. */
    private fun ultimosMensajes(chatId: String, limite: Long) =
        db.collection("chats").document(chatId)
            .collection("mensajes")
            .orderBy("fecha")
            .limitToLast(limite)

    fun cargarMensajesDesdeCache(chatId: String, limite: Long, callback: (List<MensajeChat>) -> Unit) {
        ultimosMensajes(chatId, limite)
            .get(Source.CACHE)
            .addOnSuccessListener { snap ->
                val mensajes = snap.documents
                    .mapNotNull { it.toObject(MensajeChat::class.java) }
                    .sortedBy { it.fecha }
                callback(mensajes)
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    /**
     * Escucha en tiempo real los [limite] mensajes más recientes. `hayMas` indica
     * que la ventana está llena y puede haber mensajes anteriores que cargar.
     */
    fun escucharMensajes(
        chatId: String,
        limite: Long,
        callback: (mensajes: List<MensajeChat>, hayMas: Boolean) -> Unit
    ): ListenerRegistration {
        return ultimosMensajes(chatId, limite)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                val mensajes = snap.documents
                    .mapNotNull { it.toObject(MensajeChat::class.java) }
                    .sortedBy { it.fecha }
                callback(mensajes, snap.size() >= limite)
            }
    }

    fun enviarMensaje(
        chatId: String,
        texto: String,
        otroUid: String? = null,
        alDenegar: () -> Unit = {},
        callback: (Boolean) -> Unit
    ) {
        val miUid = uid()
        obtenerNombre { nombre ->
            val ref = db.collection("chats")
                .document(chatId)
                .collection("mensajes")
                .document()

            val mensaje = MensajeChat(
                id = ref.id,
                chatId = chatId,
                emisorUid = miUid,
                emisorNombre = nombre,
                texto = texto,
                tipo = "texto"
            )

            enviarEnLote(chatId, ref, mensaje, texto, otroUid, alDenegar, callback)
        }
    }

    /** Envía una foto ya comprimida en JPEG ([com.example.zerohaus.Util.Imagenes]). */
    fun enviarImagen(chatId: String, jpeg: ByteArray, caption: String = "", otroUid: String? = null, alDenegar: () -> Unit = {}, callback: (Boolean) -> Unit) {
        val miUid = uid()
        val ref = db.collection("chats").document(chatId).collection("mensajes").document()
        val storageRef = storage.reference.child("chats/$chatId/${ref.id}.jpg")

        storageRef.putBytes(jpeg, StorageMetadata.Builder().setContentType("image/jpeg").build())
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception!!
                storageRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                val url = downloadUri.toString()
                obtenerNombre { nombre ->
                    val mensaje = MensajeChat(
                        id = ref.id,
                        chatId = chatId,
                        emisorUid = miUid,
                        emisorNombre = nombre,
                        texto = caption,
                        tipo = "imagen",
                        mediaUrl = url
                    )

                    val resumen = if (caption.isNotEmpty()) "📷 $caption" else "📷 Foto"
                    enviarEnLote(chatId, ref, mensaje, resumen, otroUid, alDenegar, callback)
                }
            }
            .addOnFailureListener { callback(false) }
    }

    fun enviarArchivo(
        chatId: String,
        uri: Uri,
        nombre: String,
        bytes: Long,
        otroUid: String? = null,
        alDenegar: () -> Unit = {},
        callback: (Boolean) -> Unit
    ) {
        val miUid = uid()
        // Solo PDF (las reglas lo exigen): con la extensión, aunque el sistema
        // dé el nombre sin ella
        val nombrePdf = if (nombre.lowercase().endsWith(".pdf")) nombre else "$nombre.pdf"
        val ref = db.collection("chats").document(chatId).collection("mensajes").document()
        val storageRef = storage.reference.child("chats/$chatId/${ref.id}_$nombrePdf")

        storageRef.putFile(uri, StorageMetadata.Builder().setContentType("application/pdf").build())
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception!!
                storageRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                val url = downloadUri.toString()
                obtenerNombre { emisorNombre ->
                    val mensaje = MensajeChat(
                        id = ref.id,
                        chatId = chatId,
                        emisorUid = miUid,
                        emisorNombre = emisorNombre,
                        texto = "",
                        tipo = "archivo",
                        mediaUrl = url,
                        mediaNombre = nombrePdf,
                        mediaBytes = bytes
                    )

                    enviarEnLote(chatId, ref, mensaje, "📎 $nombrePdf", otroUid, alDenegar, callback)
                }
            }
            .addOnFailureListener { callback(false) }
    }

    fun eliminarMensaje(chatId: String, mensajeId: String, callback: (Boolean) -> Unit) {
        db.collection("chats").document(chatId)
            .collection("mensajes").document(mensajeId)
            .delete()
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    fun marcarLeidos(chatId: String) {
        val miUid = uid()
        db.collection("chats").document(chatId)
            .update("noLeidosPor.$miUid", 0)
    }

}
