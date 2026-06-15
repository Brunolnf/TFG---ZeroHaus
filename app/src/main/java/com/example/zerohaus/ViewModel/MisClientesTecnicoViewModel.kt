package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.SolicitudPresupuesto
import com.example.zerohaus.Repositorios.RepositorioTecnicos
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class ClienteResumen(
    val uid: String,
    val nombre: String,
    val ultimoMensaje: String = "",
    val fechaUltima: Long = 0L,
    val solicitudes: Int = 0,
    val activas: Int = 0,
    val chatId: String = ""
)

data class MisClientesEstado(
    val clientes: List<ClienteResumen> = emptyList(),
    val cargando: Boolean = false
)

class MisClientesTecnicoViewModel : ViewModel() {

    var estado by mutableStateOf(MisClientesEstado())
        private set

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val repoTecnicos = RepositorioTecnicos()

    private var listenerChats: ListenerRegistration? = null
    private var listenerSolicitudes: ListenerRegistration? = null
    private var ultimosChats: Map<String, ClienteResumen> = emptyMap()
    private var ultimasSolicitudes: List<SolicitudPresupuesto> = emptyList()

    init { if (auth.currentUser != null) cargar() }

    /**
     * Suscribe chats y solicitudes en tiempo real y recompone la lista en cada
     * cambio. Antes era one-shot con caché (`if (clientes.isNotEmpty()) return`):
     * si llegaba un mensaje o solicitud nueva mientras la pantalla estaba abierta,
     * no se actualizaba hasta reabrir la app.
     */
    fun cargar(forzar: Boolean = false) {
        if (listenerChats != null && listenerSolicitudes != null) return
        estado = estado.copy(cargando = true)
        val miUid = auth.currentUser?.uid ?: run {
            estado = estado.copy(cargando = false); return
        }

        listenerChats = db.collection("chats")
            .whereArrayContains("participantes", miUid)
            .addSnapshotListener { snap, _ ->
                val mapa = mutableMapOf<String, ClienteResumen>()
                snap?.documents?.forEach { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val participantes = (doc.get("participantes") as? List<String>) ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val nombres = (doc.get("nombresParticipantes") as? Map<String, Any>) ?: emptyMap()
                    val ultimo = doc.getString("ultimoMensaje") ?: ""
                    val fecha = doc.getLong("fechaUltimoMensaje") ?: 0L
                    val otro = participantes.firstOrNull { it != miUid } ?: return@forEach
                    val nombreOtro = (nombres[otro] as? String) ?: "Cliente"
                    mapa[otro] = ClienteResumen(
                        uid = otro,
                        nombre = nombreOtro.replace(Regex(" \\(Técnico\\)"), "").trim(),
                        ultimoMensaje = ultimo,
                        fechaUltima = fecha,
                        chatId = doc.id
                    )
                }
                ultimosChats = mapa
                recomponer()
            }

        listenerSolicitudes = repoTecnicos.escucharSolicitudesRecibidas { lista ->
            ultimasSolicitudes = lista
            recomponer()
        }
    }

    private fun recomponer() {
        val mapa = ultimosChats
        val solicitudes = ultimasSolicitudes
        val porCliente = solicitudes.groupBy { it.uidCliente }
        val activosEstados = setOf("Pendiente", "Presupuestado", "Aceptado")
        val conChat = mapa.values.map { c ->
            val sols = porCliente[c.uid].orEmpty()
            c.copy(
                solicitudes = sols.size,
                activas = sols.count { it.estado in activosEstados }
            )
        }
        val sinChat = solicitudes
            .filter { it.uidCliente !in mapa.keys && it.uidCliente.isNotBlank() }
            .groupBy { it.uidCliente }
            .map { (uid, sols) ->
                ClienteResumen(
                    uid = uid,
                    nombre = sols.firstOrNull()?.nombreCliente ?: "Cliente",
                    solicitudes = sols.size,
                    activas = sols.count { it.estado in activosEstados }
                )
            }
        estado = estado.copy(
            clientes = (conChat + sinChat).sortedByDescending { it.fechaUltima },
            cargando = false
        )
    }

    override fun onCleared() {
        super.onCleared()
        listenerChats?.remove()
        listenerSolicitudes?.remove()
        listenerChats = null
        listenerSolicitudes = null
    }
}
