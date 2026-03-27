package com.example.zerohaus.ViewModel



import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Chat
import com.example.zerohaus.Modelos.MensajeChat
import com.example.zerohaus.Repositorios.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

data class ChatEstado(
    val chats: List<Chat> = emptyList(),
    val mensajes: List<MensajeChat> = emptyList(),
    val chatActualId: String = "",
    val nombreOtro: String = "",
    val cargando: Boolean = true,
    val error: String? = null
)

class ChatViewModel : ViewModel() {

    var estado by mutableStateOf(ChatEstado())
        private set

    private val repoChat = RepositorioChat()
    private val repoAuth = RepositorioAutenticacion()
    private val miUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var listener: ListenerRegistration? = null

    fun cargarChats() {
        estado = estado.copy(cargando = true)
        repoChat.obtenerMisChats { chats ->
            estado = estado.copy(chats = chats, cargando = false)
        }
    }

    fun abrirChat(chatId: String) {
        listener?.remove()
        estado = estado.copy(chatActualId = chatId, mensajes = emptyList())
        repoChat.marcarLeido(chatId)

        // Obtener nombre del otro participante
        val chat = estado.chats.find { it.id == chatId }
        val nombreOtro = chat?.nombresParticipantes?.entries?.firstOrNull { it.key != miUid }?.value ?: "Chat"
        estado = estado.copy(nombreOtro = nombreOtro)

        // Escuchar mensajes en tiempo real
        listener = repoChat.escucharMensajes(chatId) { mensajes ->
            estado = estado.copy(mensajes = mensajes)
            repoChat.marcarLeido(chatId)
        }
    }

    fun iniciarChatConTecnico(tecnicoUid: String, tecnicoNombre: String) {
        estado = estado.copy(cargando = true)
        repoAuth.obtenerUsuario { usuario ->
            val miNombre = usuario?.nombre ?: "Usuario"
            repoChat.obtenerOCrearChat(tecnicoUid, tecnicoNombre, miNombre) { chatId ->
                estado = estado.copy(cargando = false)
                abrirChat(chatId)
            }
        }
    }

    fun enviarMensaje(texto: String) {
        if (texto.isBlank() || estado.chatActualId.isEmpty()) return
        repoAuth.obtenerUsuario { usuario ->
            repoChat.enviarMensaje(estado.chatActualId, texto, usuario?.nombre ?: "Usuario") { result ->
                result.onFailure { estado = estado.copy(error = it.message) }
            }
        }
    }

    fun cerrarChat() {
        listener?.remove()
        listener = null
        estado = estado.copy(chatActualId = "", mensajes = emptyList(), nombreOtro = "")
    }

    fun contarNoLeidos(): Int {
        return estado.chats.sumOf { chat ->
            (chat.noLeidosPor[miUid] ?: 0)
        }
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}
