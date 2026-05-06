package com.example.zerohaus.ViewModel

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Chat
import com.example.zerohaus.Modelos.MensajeChat
import com.example.zerohaus.Repositorios.RepositorioChat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class ChatListEstado(
    val chats: List<Chat> = emptyList(),
    val cargando: Boolean = true
)

data class ChatEstado(
    val mensajes: List<MensajeChat> = emptyList(),
    val texto: String = "",
    val enviando: Boolean = false,
    val error: String? = null,
    val nombreOtroUsuario: String = "",
    val subiendoMedia: Boolean = false,
    val otroUid: String = "",
    val otroTecnicoDocId: String = "",
    val imagenPendiente: android.net.Uri? = null,
    val captionImagen: String = ""
)

class ChatViewModel : ViewModel() {

    var listaEstado by mutableStateOf(ChatListEstado())
        private set

    var chatEstado by mutableStateOf(ChatEstado())
        private set

    private val repo = RepositorioChat()
    private val db = FirebaseFirestore.getInstance()

    val miUid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private var listenerChats: ListenerRegistration? = null
    private var listenerMensajes: ListenerRegistration? = null

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        if (auth.currentUser == null) {
            listenerChats?.remove(); listenerChats = null
            listenerMensajes?.remove(); listenerMensajes = null
            listaEstado = ChatListEstado()
            chatEstado = ChatEstado()
        }
    }

    init {
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
    }

    // ───────────── LISTA CHATS ─────────────

    fun cargarChats() {
        listenerChats?.remove()
        listaEstado = listaEstado.copy(cargando = true)

        listenerChats = repo.escucharChats { chats ->
            listaEstado = ChatListEstado(
                chats = chats,
                cargando = false
            )
        }
    }

    fun contarNoLeidos(): Int {
        return listaEstado.chats.count { it.tieneNoLeidos(miUid) }
    }

    // ───────────── CHAT ─────────────

    fun abrirChat(chatId: String) {
        listenerMensajes?.remove()
        chatEstado = ChatEstado()

        // Nombre del otro participante
        val chatCacheado = listaEstado.chats.firstOrNull { it.id == chatId }
        if (chatCacheado != null) {
            val entry = chatCacheado.nombresParticipantes.entries
                .firstOrNull { it.key != miUid }
            val nombreOtro = entry?.value ?: ""
            val otroUid = entry?.key ?: ""
            chatEstado = chatEstado.copy(nombreOtroUsuario = nombreOtro, otroUid = otroUid)
            if (otroUid.isNotEmpty()) buscarTecnicoDocId(otroUid)
        } else {
            db.collection("chats").document(chatId)
                .get()
                .addOnSuccessListener { doc ->
                    val chat = doc.toObject(Chat::class.java)
                    val entry = chat?.nombresParticipantes?.entries
                        ?.firstOrNull { it.key != miUid }
                    val nombreOtro = entry?.value ?: ""
                    val otroUid = entry?.key ?: ""
                    chatEstado = chatEstado.copy(nombreOtroUsuario = nombreOtro, otroUid = otroUid)
                    if (otroUid.isNotEmpty()) buscarTecnicoDocId(otroUid)
                }
        }

        // Carga inmediata desde caché local
        repo.cargarMensajesDesdeCache(chatId) { mensajes ->
            if (mensajes.isNotEmpty()) {
                chatEstado = chatEstado.copy(mensajes = mensajes)
            }
        }

        // Listener en tiempo real
        listenerMensajes = repo.escucharMensajes(chatId) { mensajes ->
            if (mensajes.isNotEmpty()) {
                chatEstado = chatEstado.copy(mensajes = mensajes)
            }
            repo.marcarLeidos(chatId)
        }
    }

    private fun buscarTecnicoDocId(otroUid: String) {
        db.collection("tecnicos")
            .whereEqualTo("uid", otroUid)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val docId = snap.documents.firstOrNull()?.id ?: ""
                chatEstado = chatEstado.copy(otroTecnicoDocId = docId)
            }
    }

    fun cambiarTexto(v: String) {
        chatEstado = chatEstado.copy(texto = v)
    }

    fun enviarMensaje(chatId: String) {
        val texto = chatEstado.texto.trim()
        if (texto.isBlank()) return

        chatEstado = chatEstado.copy(enviando = true, texto = "")

        repo.enviarMensaje(chatId, texto) { ok ->
            chatEstado = chatEstado.copy(
                enviando = false,
                error = if (!ok) "Error enviando mensaje" else null
            )
        }
    }

    fun seleccionarImagen(uri: android.net.Uri) {
        chatEstado = chatEstado.copy(imagenPendiente = uri, captionImagen = "")
    }

    fun cambiarCaptionImagen(v: String) {
        chatEstado = chatEstado.copy(captionImagen = v)
    }

    fun cancelarImagenPendiente() {
        chatEstado = chatEstado.copy(imagenPendiente = null, captionImagen = "")
    }

    fun enviarImagenPendiente(chatId: String) {
        val uri = chatEstado.imagenPendiente ?: return
        val caption = chatEstado.captionImagen.trim()
        chatEstado = chatEstado.copy(subiendoMedia = true, imagenPendiente = null, captionImagen = "")
        repo.enviarImagen(chatId, uri, caption) { ok ->
            chatEstado = chatEstado.copy(subiendoMedia = false, error = if (!ok) "Error enviando imagen" else null)
        }
    }

    fun enviarArchivo(chatId: String, uri: android.net.Uri, nombre: String, bytes: Long) {
        chatEstado = chatEstado.copy(subiendoMedia = true)
        repo.enviarArchivo(chatId, uri, nombre, bytes) { ok ->
            chatEstado = chatEstado.copy(subiendoMedia = false, error = if (!ok) "Error enviando archivo" else null)
        }
    }

    // ───────────── CREAR CHAT ─────────────

    fun iniciarChatConTecnico(
        tecnicoUid: String,
        tecnicoNombre: String,
        onChatListo: (String) -> Unit
    ) {
        db.collection("usuarios").document(miUid).get()
            .addOnSuccessListener { doc ->
                val miNombre = doc.getString("nombre") ?: "Usuario"
                repo.obtenerOCrearChat(tecnicoUid, tecnicoNombre, miNombre, onChatListo)
            }
            .addOnFailureListener {
                repo.obtenerOCrearChat(tecnicoUid, tecnicoNombre, "Usuario", onChatListo)
            }
    }

    fun cerrarChat() {
        listenerMensajes?.remove()
    }

    override fun onCleared() {
        super.onCleared()
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        listenerChats?.remove()
        listenerMensajes?.remove()
    }
}
