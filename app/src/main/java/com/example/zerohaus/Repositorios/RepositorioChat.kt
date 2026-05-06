package com.example.zerohaus.Repositorios

import android.net.Uri
import com.example.zerohaus.Modelos.Chat
import com.example.zerohaus.Modelos.MensajeChat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage

class RepositorioChat {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    // ───────────── HELPER PRIVADO ─────────────

    private fun actualizarChatYNotificar(
        chatId: String,
        ultimoMensaje: String,
        miUid: String,
        emisorNombre: String
    ) {
        db.collection("chats").document(chatId).get()
            .addOnSuccessListener { chatDoc ->
                val participantes = (chatDoc.get("participantes") as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()

                val updates = mutableMapOf<String, Any>(
                    "ultimoMensaje" to ultimoMensaje,
                    "fechaUltimoMensaje" to System.currentTimeMillis()
                )

                participantes.filter { it != miUid }.forEach { receptorUid ->
                    updates["noLeidosPor.$receptorUid"] = FieldValue.increment(1)
                    crearNotif(
                        uid = receptorUid,
                        titulo = "Nuevo mensaje de $emisorNombre",
                        detalle = if (ultimoMensaje.length > 100) ultimoMensaje.take(100) + "…" else ultimoMensaje,
                        tipo = "mensaje"
                    )
                }

                db.collection("chats").document(chatId).update(updates)
            }
    }

    // ───────────── OBTENER / CREAR CHAT ─────────────

    fun obtenerOCrearChat(
        otroUid: String,
        otroNombre: String,
        miNombre: String,
        callback: (String) -> Unit
    ) {
        val miUid = uid()

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

                    ref.set(chat).addOnSuccessListener {
                        callback(ref.id)
                    }
                }
            }
    }

    // ───────────── ESCUCHAR CHATS ─────────────

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

    // ───────────── MENSAJES ─────────────

    fun cargarMensajesDesdeCache(chatId: String, callback: (List<MensajeChat>) -> Unit) {
        db.collection("chats").document(chatId)
            .collection("mensajes")
            .get(Source.CACHE)
            .addOnSuccessListener { snap ->
                val mensajes = snap.documents
                    .mapNotNull { it.toObject(MensajeChat::class.java) }
                    .sortedBy { it.fecha }
                callback(mensajes)
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun escucharMensajes(
        chatId: String,
        callback: (List<MensajeChat>) -> Unit
    ): ListenerRegistration {
        return db.collection("chats").document(chatId)
            .collection("mensajes")
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                val mensajes = snap.documents
                    .mapNotNull { it.toObject(MensajeChat::class.java) }
                    .sortedBy { it.fecha }
                callback(mensajes)
            }
    }

    // ───────────── ENVIAR TEXTO ─────────────

    fun enviarMensaje(
        chatId: String,
        texto: String,
        callback: (Boolean) -> Unit
    ) {
        val miUid = uid()

        db.collection("usuarios").document(miUid).get()
            .addOnSuccessListener { userDoc ->
                val nombre = userDoc.getString("nombre") ?: "Usuario"

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

                ref.set(mensaje)
                    .addOnSuccessListener {
                        actualizarChatYNotificar(chatId, texto, miUid, nombre)
                        callback(true)
                    }
                    .addOnFailureListener { callback(false) }
            }
    }

    // ───────────── ENVIAR IMAGEN ─────────────

    fun enviarImagen(chatId: String, uri: Uri, caption: String = "", callback: (Boolean) -> Unit) {
        val miUid = uid()
        val ref = db.collection("chats").document(chatId).collection("mensajes").document()
        val storageRef = storage.reference.child("chats/$chatId/${ref.id}.jpg")

        storageRef.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception!!
                storageRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                val url = downloadUri.toString()

                db.collection("usuarios").document(miUid).get()
                    .addOnSuccessListener { userDoc ->
                        val nombre = userDoc.getString("nombre") ?: "Usuario"

                        val mensaje = MensajeChat(
                            id = ref.id,
                            chatId = chatId,
                            emisorUid = miUid,
                            emisorNombre = nombre,
                            texto = caption,
                            tipo = "imagen",
                            mediaUrl = url
                        )

                        ref.set(mensaje)
                            .addOnSuccessListener {
                                val resumen = if (caption.isNotEmpty()) "📷 $caption" else "📷 Foto"
                                actualizarChatYNotificar(chatId, resumen, miUid, nombre)
                                callback(true)
                            }
                            .addOnFailureListener { callback(false) }
                    }
                    .addOnFailureListener { callback(false) }
            }
            .addOnFailureListener { callback(false) }
    }

    // ───────────── ENVIAR ARCHIVO ─────────────

    fun enviarArchivo(
        chatId: String,
        uri: Uri,
        nombre: String,
        bytes: Long,
        callback: (Boolean) -> Unit
    ) {
        val miUid = uid()
        val ref = db.collection("chats").document(chatId).collection("mensajes").document()
        val storageRef = storage.reference.child("chats/$chatId/${ref.id}_$nombre")

        storageRef.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception!!
                storageRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                val url = downloadUri.toString()

                db.collection("usuarios").document(miUid).get()
                    .addOnSuccessListener { userDoc ->
                        val emisorNombre = userDoc.getString("nombre") ?: "Usuario"

                        val mensaje = MensajeChat(
                            id = ref.id,
                            chatId = chatId,
                            emisorUid = miUid,
                            emisorNombre = emisorNombre,
                            texto = "",
                            tipo = "archivo",
                            mediaUrl = url,
                            mediaNombre = nombre,
                            mediaBytes = bytes
                        )

                        ref.set(mensaje)
                            .addOnSuccessListener {
                                actualizarChatYNotificar(chatId, "📎 $nombre", miUid, emisorNombre)
                                callback(true)
                            }
                            .addOnFailureListener { callback(false) }
                    }
                    .addOnFailureListener { callback(false) }
            }
            .addOnFailureListener { callback(false) }
    }

    // ───────────── MARCAR LEÍDOS ─────────────

    fun marcarLeidos(chatId: String) {
        val miUid = uid()
        db.collection("chats").document(chatId)
            .update("noLeidosPor.$miUid", 0)
    }

    // ───────────── NOTIFICACIONES ─────────────

    private fun crearNotif(uid: String, titulo: String, detalle: String, tipo: String) {
        val ref = db.collection("notificaciones").document()
        ref.set(
            hashMapOf(
                "id" to ref.id, "uid" to uid,
                "titulo" to titulo, "detalle" to detalle,
                "fecha" to System.currentTimeMillis(),
                "leida" to false, "tipo" to tipo
            )
        )
    }
}
