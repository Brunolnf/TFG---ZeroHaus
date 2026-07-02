package com.example.zerohaus.ViewModel

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Chat
import com.example.zerohaus.Modelos.MensajeChat
import com.example.zerohaus.Modelos.SolicitudPresupuesto
import com.example.zerohaus.Repositorios.RepositorioAutenticacion
import com.example.zerohaus.Repositorios.RepositorioChat
import com.example.zerohaus.Repositorios.RepositorioTecnicos
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
    private val repoTecnicos = RepositorioTecnicos()
    private val repoAuth = RepositorioAutenticacion()
    private val db = FirebaseFirestore.getInstance()

    val miUid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private var listenerChats: ListenerRegistration? = null
    private var listenerMensajes: ListenerRegistration? = null

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
                chats = chats,
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

    fun eliminarMensaje(chatId: String, mensajeId: String) {
        repo.eliminarMensaje(chatId, mensajeId) { ok ->
            if (!ok) chatEstado = chatEstado.copy(error = "Error eliminando mensaje")
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

    /**
     * Envía una solicitud de presupuesto al técnico con el que estoy chateando.
     * El chat debe ser con un técnico (`otroTecnicoDocId` no vacío).
     */
    fun solicitarPresupuestoAlTecnico(
        descripcion: String,
        callback: (Result<Unit>) -> Unit
    ) {
        val tecnicoDocId = chatEstado.otroTecnicoDocId
        val tecnicoUid = chatEstado.otroUid
        val tecnicoNombre = chatEstado.nombreOtroUsuario

        if (tecnicoDocId.isEmpty()) {
            callback(Result.failure(Exception("Este chat no es con un técnico")))
            return
        }

        // obtenerUsuarioUnaVez (no obtenerUsuario): el callback debe ejecutarse una
        // sola vez. El doble disparo caché+servidor crearía dos solicitudes idénticas.
        repoAuth.obtenerUsuarioUnaVez { u ->
            val solicitud = SolicitudPresupuesto(
                uidCliente = repoAuth.getUid() ?: "",
                nombreCliente = u?.nombre ?: "Usuario",
                tecnicoId = tecnicoDocId,
                tecnicoUid = tecnicoUid,
                tecnicoNombre = tecnicoNombre,
                descripcion = descripcion.ifBlank { "Solicitud de presupuesto" }
            )
            repoTecnicos.solicitarPresupuesto(solicitud, callback)
        }
    }

    override fun onCleared() {
        super.onCleared()
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        listenerChats?.remove()
        listenerMensajes?.remove()
    }
}
