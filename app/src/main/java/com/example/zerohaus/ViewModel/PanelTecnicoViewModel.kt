package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Repositorios.RepositorioTecnicos
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class PanelTecnicoEstado(
    val tecnico: Tecnico? = null,
    val solicitudesPendientes: Int = 0,
    val solicitudesPresupuestadas: Int = 0,
    val solicitudesAceptadas: Int = 0,
    val cargando: Boolean = true
)

class PanelTecnicoViewModel : ViewModel() {

    var estado by mutableStateOf(PanelTecnicoEstado())
        private set

    private val repo = RepositorioTecnicos()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun cargar() {
        estado = estado.copy(cargando = true)
        val miUid = auth.currentUser?.uid ?: run {
            estado = estado.copy(cargando = false)
            return
        }

        // 1. Buscar el perfil de técnico vinculado a este Auth uid
        db.collection("tecnicos").whereEqualTo("uid", miUid).limit(1).get()
            .addOnSuccessListener { snap ->
                val doc = snap.documents.firstOrNull()
                val tec = doc?.toObject(Tecnico::class.java)?.let { t ->
                    if (t.id.isBlank()) t.copy(id = doc.id) else t
                }

                // 2. Cargar las solicitudes recibidas y agruparlas por estado
                repo.obtenerSolicitudesRecibidas { solicitudes ->
                    estado = PanelTecnicoEstado(
                        tecnico = tec,
                        solicitudesPendientes = solicitudes.count { it.estado == "Pendiente" },
                        solicitudesPresupuestadas = solicitudes.count { it.estado == "Presupuestado" },
                        solicitudesAceptadas = solicitudes.count { it.estado == "Aceptado" },
                        cargando = false
                    )
                }
            }
            .addOnFailureListener {
                estado = estado.copy(cargando = false)
            }
    }
}
