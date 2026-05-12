package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Repositorios.RepositorioTecnicos
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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
    val cargando: Boolean = true
)

class MisClientesTecnicoViewModel : ViewModel() {

    var estado by mutableStateOf(MisClientesEstado())
        private set

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val repoTecnicos = RepositorioTecnicos()

    fun cargar() {
        estado = estado.copy(cargando = true)
        val miUid = auth.currentUser?.uid ?: run {
            estado = estado.copy(cargando = false); return
        }

        repoTecnicos.obtenerMiPerfilTecnico { miPerfil ->
            // Cargo los chats donde participo
            db.collection("chats")
                .whereArrayContains("participantes", miUid)
                .get()
                .addOnSuccessListener { chats ->
                    // Por cada chat, identifico al "otro" participante (cliente)
                    val mapa = mutableMapOf<String, ClienteResumen>()
                    chats.documents.forEach { doc ->
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

                    // Cargo solicitudes recibidas para complementar el contador por cliente
                    if (miPerfil == null) {
                        estado = estado.copy(
                            clientes = mapa.values.sortedByDescending { it.fechaUltima },
                            cargando = false
                        )
                        return@addOnSuccessListener
                    }
                    repoTecnicos.obtenerSolicitudesRecibidas { solicitudes ->
                        val porCliente = solicitudes.groupBy { it.uidCliente }
                        val final = mapa.values.map { c ->
                            val sols = porCliente[c.uid].orEmpty()
                            val activas = sols.count { it.estado in setOf("Pendiente", "Presupuestado", "Aceptado") }
                            c.copy(solicitudes = sols.size, activas = activas)
                        }
                        // También incluye clientes que tengan solicitud pero aún no chat
                        val sinChat = solicitudes
                            .filter { it.uidCliente !in mapa.keys && it.uidCliente.isNotBlank() }
                            .groupBy { it.uidCliente }
                            .map { (uid, sols) ->
                                ClienteResumen(
                                    uid = uid,
                                    nombre = sols.firstOrNull()?.nombreCliente ?: "Cliente",
                                    solicitudes = sols.size,
                                    activas = sols.count { it.estado in setOf("Pendiente", "Presupuestado", "Aceptado") }
                                )
                            }
                        estado = estado.copy(
                            clientes = (final + sinChat).sortedByDescending { it.fechaUltima },
                            cargando = false
                        )
                    }
                }
                .addOnFailureListener {
                    estado = estado.copy(cargando = false)
                }
        }
    }
}
