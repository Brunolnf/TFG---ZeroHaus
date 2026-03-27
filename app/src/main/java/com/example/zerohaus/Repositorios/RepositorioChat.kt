package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Chat
import com.example.zerohaus.Modelos.MensajeChat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class RepositorioChat {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    // Obtener o crear chat entre dos usuarios
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
                val chatExistente = snap.documents.firstOrNull { doc ->
                    val participantes = doc.get("participantes") as? List<*>
                    participantes?.contains(otroUid) == true
                }
                if (chatExistente != null) {
                    callback(chatExistente.id)
                } else {
                    val ref = db.collection("chats").document()
                    val chat = Chat(
                        id = ref.id,
                        participantes = listOf(miUid, otroUid),
                        nombresParticipantes = mapOf(miUid to miNombre, otroUid to otroNombre),
                        noLeidosPor = mapOf(miUid to 0, otroUid to 0)
                    )
                    ref.set(chat).addOnSuccessListener { callback(ref.id) }
                }
            }
    }

    // Obtener mis chats
    fun obtenerMisChats(callback: (List<Chat>) -> Unit) {
        db.collection("chats")
            .whereArrayContains("participantes", uid())
            .orderBy("fechaUltimoMensaje", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.mapNotNull { it.toObject(Chat::class.java) })
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    // Escuchar mensajes en tiempo real
    fun escucharMensajes(chatId: String, callback: (List<MensajeChat>) -> Unit): ListenerRegistration {
        return db.collection("chats").document(chatId)
            .collection("mensajes")
            .orderBy("fecha", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val mensajes = snap.documents.mapNotNull { it.toObject(MensajeChat::class.java) }
                    callback(mensajes)
                }
            }
    }

    // Enviar mensaje
    fun enviarMensaje(chatId: String, texto: String, emisorNombre: String, callback: (Result<Unit>) -> Unit) {
        val miUid = uid()
        val ref = db.collection("chats").document(chatId).collection("mensajes").document()
        val mensaje = MensajeChat(
            id = ref.id,
            chatId = chatId,
            emisorUid = miUid,
            emisorNombre = emisorNombre,
            texto = texto
        )
        ref.set(mensaje)
            .addOnSuccessListener {
                // Actualizar último mensaje del chat y contadores
                db.collection("chats").document(chatId).get()
                    .addOnSuccessListener { doc ->
                        val chat = doc.toObject(Chat::class.java) ?: return@addOnSuccessListener
                        val noLeidos = chat.noLeidosPor.toMutableMap()
                        chat.participantes.forEach { uid ->
                            if (uid != miUid) {
                                noLeidos[uid] = (noLeidos[uid] ?: 0) + 1
                            }
                        }
                        db.collection("chats").document(chatId).update(
                            mapOf(
                                "ultimoMensaje" to texto,
                                "fechaUltimoMensaje" to System.currentTimeMillis(),
                                "noLeidosPor" to noLeidos
                            )
                        )
                    }
                callback(Result.success(Unit))
            }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error enviando mensaje")))
            }
    }

    // Marcar chat como leído
    fun marcarLeido(chatId: String) {
        val miUid = uid()
        db.collection("chats").document(chatId).get()
            .addOnSuccessListener { doc ->
                val noLeidos = (doc.get("noLeidosPor") as? Map<*, *>)?.toMutableMap() ?: return@addOnSuccessListener
                noLeidos[miUid] = 0
                db.collection("chats").document(chatId).update("noLeidosPor", noLeidos)
            }
    }
}
