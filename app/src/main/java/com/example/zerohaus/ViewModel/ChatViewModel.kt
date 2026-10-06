package com.example.zerohaus.ViewModel

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Chat
import com.example.zerohaus.Modelos.MensajeChat
import com.example.zerohaus.Repositorios.RepositorioChat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.getCadenas

/**
 * Estado de la lista de conversaciones.
 */
data class ChatListEstado(
    val chats: List<Chat> = emptyList(),
    val cargando: Boolean = true
)

/**
 * Estado de una conversación abierta.
 */
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
    val captionImagen: String = "",
    // Paginación: solo se escuchan los últimos N mensajes
    val hayMasMensajes: Boolean = false,
    val cargandoAnteriores: Boolean = false
)

/**
 * Lista de conversaciones y conversación abierta: escucha en tiempo real,
 * paginación de mensajes y envío de texto, imágenes y archivos.
 */
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
    private var chatAbierto: String? = null
    private var limiteMensajes = PAGINA_MENSAJES

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        if (auth.currentUser == null) {
            listenerChats?.remove(); listenerChats = null
            listenerMensajes?.remove(); listenerMensajes = null
            // cargando = false: evita spinner huérfano durante el logout.
            listaEstado = ChatListEstado(cargando = false)
            chatEstado = ChatEstado()
        }
    }

    init {
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        // Carga inmediata desde caché de Firestore si hay sesión activa.
        if (FirebaseAuth.getInstance().currentUser != null) cargarChats()
    }

    fun cargarChats() {
        if (listenerChats != null) return  // ya escuchando, no duplicar
        listaEstado = listaEstado.copy(cargando = true)

        listenerChats = repo.escucharChats { chats ->
            listaEstado = ChatListEstado(
                // Los chats recién creados (sin ningún mensaje) no se muestran:
                // la conversación aparece en la lista cuando llega el primer
                // mensaje real. Evita chats fantasma al pulsar "Chatear" en un
                // perfil y salir sin escribir nada.
                chats = chats.filter { it.fechaUltimoMensaje > 0 || it.ultimoMensaje.isNotBlank() },
                cargando = false
            )
        }
    }

    fun contarNoLeidos(): Int {
        return listaEstado.chats.count { it.tieneNoLeidos(miUid) }
    }

    fun abrirChat(chatId: String) {
        listenerMensajes?.remove()
        chatEstado = ChatEstado()
        chatAbierto = chatId
        limiteMensajes = PAGINA_MENSAJES

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
        repo.cargarMensajesDesdeCache(chatId, limiteMensajes) { mensajes ->
            if (mensajes.isNotEmpty()) {
                chatEstado = chatEstado.copy(mensajes = mensajes)
            }
        }

        escucharMensajes(chatId)
    }

    /** Amplía la ventana de mensajes escuchados con la página anterior. */
    fun cargarMensajesAnteriores() {
        val chatId = chatAbierto ?: return
        if (!chatEstado.hayMasMensajes || chatEstado.cargandoAnteriores) return
        limiteMensajes += PAGINA_MENSAJES
        chatEstado = chatEstado.copy(cargandoAnteriores = true)
        escucharMensajes(chatId)
    }

    private fun escucharMensajes(chatId: String) {
        listenerMensajes?.remove()
        listenerMensajes = repo.escucharMensajes(chatId, limiteMensajes) { mensajes, hayMas ->
            chatEstado = if (mensajes.isNotEmpty())
                chatEstado.copy(mensajes = mensajes, hayMasMensajes = hayMas, cargandoAnteriores = false)
            else
                chatEstado.copy(hayMasMensajes = false, cargandoAnteriores = false)
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
            chatEstado = if (ok) chatEstado.copy(enviando = false, error = null)
            else chatEstado.copy(
                enviando = false,
                error = getCadenas(AppEstado.idioma).chatErrorEnviar,
                // Se le devuelve lo que había escrito para que no lo pierda
                texto = chatEstado.texto.ifEmpty { texto }
            )
        }
    }

    fun limpiarError() {
        chatEstado = chatEstado.copy(error = null)
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
            chatEstado = chatEstado.copy(subiendoMedia = false, error = if (!ok) getCadenas(AppEstado.idioma).chatErrorImagen else null)
        }
    }

    fun enviarArchivo(chatId: String, uri: android.net.Uri, nombre: String, bytes: Long) {
        chatEstado = chatEstado.copy(subiendoMedia = true)
        repo.enviarArchivo(chatId, uri, nombre, bytes) { ok ->
            chatEstado = chatEstado.copy(subiendoMedia = false, error = if (!ok) getCadenas(AppEstado.idioma).chatErrorArchivo else null)
        }
    }

    fun eliminarMensaje(chatId: String, mensajeId: String) {
        repo.eliminarMensaje(chatId, mensajeId) { ok ->
            if (!ok) chatEstado = chatEstado.copy(error = getCadenas(AppEstado.idioma).chatErrorEliminar)
        }
    }

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

    companion object {
        private const val PAGINA_MENSAJES = 50L
    }
}
