package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Proyecto
import com.example.zerohaus.Repositorios.RepositorioProyectos
import com.example.zerohaus.Repositorios.RepositorioTecnicos
import com.google.firebase.firestore.ListenerRegistration

data class ProyectosAsignadosEstado(
    val proyectos: List<Proyecto> = emptyList(),
    val cargando: Boolean = false,
    val marcandoTerminado: Boolean = false,
    val mensaje: String? = null
)

class ProyectosAsignadosViewModel : ViewModel() {

    var estado by mutableStateOf(ProyectosAsignadosEstado())
        private set

    private val repoProyectos = RepositorioProyectos()
    private val repoTecnicos = RepositorioTecnicos()
    private var listenerProyectos: ListenerRegistration? = null

    init { cargar() }

    /**
     * Suscribe los proyectos asignados en tiempo real. Antes era un one-shot get
     * que se cacheaba con `estado.proyectos.isNotEmpty()`: si el cliente aceptaba
     * la ficha y se creaba un proyecto nuevo, el técnico no lo veía hasta cerrar
     * y abrir la app.
     */
    fun cargar(forzar: Boolean = false) {
        if (listenerProyectos != null) return  // ya está en tiempo real
        estado = estado.copy(cargando = true)
        listenerProyectos = repoProyectos.escucharProyectosAsignados { lista ->
            estado = estado.copy(proyectos = lista, cargando = false)
        }
    }

    fun toggleTarea(proyectoId: String, tareaIndex: Int, completada: Boolean) {
        // Actualización optimista local: el checkbox se ve marcado al instante,
        // sin tener que esperar a Firestore + recarga. Si el servidor rechaza,
        // recargamos para volver a la verdad.
        val nuevosProyectos = estado.proyectos.map { p ->
            if (p.id != proyectoId) return@map p
            val tareas = p.tareas.toMutableList()
            if (tareaIndex !in tareas.indices) return@map p
            tareas[tareaIndex] = tareas[tareaIndex].copy(completada = completada)
            val total = tareas.size
            val hechas = tareas.count { it.completada }
            val nuevoProgreso = if (total > 0) (hechas * 100) / total else 0
            val nuevoEstado = when {
                nuevoProgreso == 100 -> "Finalizado"
                nuevoProgreso > 0 -> "En curso"
                else -> "Pendiente"
            }
            p.copy(tareas = tareas, progreso = nuevoProgreso, estado = nuevoEstado)
        }
        estado = estado.copy(proyectos = nuevosProyectos)

        repoProyectos.actualizarTarea(proyectoId, tareaIndex, completada) { result ->
            result.onFailure { e ->
                estado = estado.copy(mensaje = "Error: ${e.message}")
                cargar(forzar = true)
            }
        }
    }

    /** Marca el trabajo del proyecto como terminado (estado: PendientePago en la solicitud). */
    fun marcarTrabajoTerminado(solicitudId: String) {
        if (estado.marcandoTerminado) return
        estado = estado.copy(marcandoTerminado = true)
        repoTecnicos.marcarTrabajoTerminado(solicitudId) { result ->
            result.onSuccess {
                estado = estado.copy(marcandoTerminado = false, mensaje = "Trabajo marcado como terminado. El cliente puede pagar.")
                cargar(forzar = true)
            }
            result.onFailure { e -> estado = estado.copy(marcandoTerminado = false, mensaje = "Error: ${e.message}") }
        }
    }

    fun limpiarMensaje() { estado = estado.copy(mensaje = null) }

    override fun onCleared() {
        super.onCleared()
        listenerProyectos?.remove()
        listenerProyectos = null
    }
}
