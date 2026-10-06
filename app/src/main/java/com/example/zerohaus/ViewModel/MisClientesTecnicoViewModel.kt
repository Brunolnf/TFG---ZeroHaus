package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Repositorios.RepositorioChat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Cliente que ha escrito al profesional, con su última conversación.
 */
data class ClienteResumen(
    val uid: String,
    val nombre: String,
    val ultimoMensaje: String = "",
    val fechaUltima: Long = 0L,
    val chatId: String = ""
)

/**
 * Estado de la pantalla de clientes del profesional.
 */
data class MisClientesEstado(
    val clientes: List<ClienteResumen> = emptyList(),
    val cargando: Boolean = false,
    val abriendoChat: Boolean = false
)

/**
 * Clientes del profesional, a partir de sus conversaciones en tiempo real.
 */
class MisClientesTecnicoViewModel : ViewModel() {

    var estado by mutableStateOf(MisClientesEstado())
        private set

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val repoChat = RepositorioChat()

    private var listenerChats: ListenerRegistration? = null

    init { if (auth.currentUser != null) cargar() }

    fun cargar(forzar: Boolean = false) {
        if (listenerChats != null && !forzar) return
        estado = estado.copy(cargando = true)
        val miUid = auth.currentUser?.uid ?: run {
            estado = estado.copy(cargando = false); return
        }

        listenerChats?.remove()
        listenerChats = db.collection("chats")
            .whereArrayContains("participantes", miUid)
            .addSnapshotListener { snap, _ ->
                val clientes = mutableListOf<ClienteResumen>()
                snap?.documents?.forEach { doc ->
                    // Igual que la lista de chats: una conversación sin ningún
                    // mensaje (el cliente pulsó «Chatear» y no escribió) no es un cliente
                    val ultimo = doc.getString("ultimoMensaje").orEmpty()
                    if ((doc.getLong("fechaUltimoMensaje") ?: 0L) == 0L && ultimo.isBlank()) return@forEach
                    @Suppress("UNCHECKED_CAST")
                    val participantes = (doc.get("participantes") as? List<String>) ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val nombres = (doc.get("nombresParticipantes") as? Map<String, Any>) ?: emptyMap()
                    val otro = participantes.firstOrNull { it != miUid } ?: return@forEach
                    clientes.add(
                        ClienteResumen(
                            uid = otro,
                            nombre = ((nombres[otro] as? String) ?: "Cliente").trim(),
                            ultimoMensaje = doc.getString("ultimoMensaje") ?: "",
                            fechaUltima = doc.getLong("fechaUltimoMensaje") ?: 0L,
                            chatId = doc.id
                        )
                    )
                }
                estado = estado.copy(
                    clientes = clientes.sortedByDescending { it.fechaUltima },
                    cargando = false
                )
            }
    }

    fun abrirChatConCliente(cliente: ClienteResumen, onChatListo: (String) -> Unit) {
        if (cliente.chatId.isNotBlank()) { onChatListo(cliente.chatId); return }
        if (estado.abriendoChat) return
        val miUid = auth.currentUser?.uid ?: return
        estado = estado.copy(abriendoChat = true)
        db.collection("usuarios").document(miUid).get()
            .addOnSuccessListener { doc ->
                val miNombre = doc.getString("nombre") ?: "Profesional"
                repoChat.obtenerOCrearChat(cliente.uid, cliente.nombre, miNombre) { chatId ->
                    estado = estado.copy(abriendoChat = false)
                    if (chatId.isNotBlank()) onChatListo(chatId)
                }
            }
            .addOnFailureListener {
                repoChat.obtenerOCrearChat(cliente.uid, cliente.nombre, "Profesional") { chatId ->
                    estado = estado.copy(abriendoChat = false)
                    if (chatId.isNotBlank()) onChatListo(chatId)
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        listenerChats?.remove()
        listenerChats = null
    }
}
