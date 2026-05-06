package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Proyecto
import com.example.zerohaus.Modelos.Tarea
import com.example.zerohaus.Repositorios.RepositorioProyectos

class ProyectosViewModel : ViewModel() {

    var proyectos by mutableStateOf<List<Proyecto>>(emptyList())
        private set
    var cargando by mutableStateOf(true)
        private set
    var guardando by mutableStateOf(false)
        private set
    var mensajeError by mutableStateOf<String?>(null)
        private set

    private val repo = RepositorioProyectos()

    fun cargarProyectos() {
        cargando = true
        repo.obtenerProyectos { lista ->
            proyectos = lista
            cargando = false
        }
    }

    fun crearProyecto(
        titulo: String,
        descripcion: String,
        viviendaNombre: String,
        tecnicoNombre: String,
        tareas: List<Tarea>,
        fechaFinEstimada: Long = 0L,
        onResult: (Boolean) -> Unit
    ) {
        guardando = true
        val proyecto = Proyecto(
            titulo = titulo,
            descripcion = descripcion,
            viviendaNombre = viviendaNombre,
            tecnicoNombre = tecnicoNombre,
            tareas = tareas,
            fechaFinEstimada = fechaFinEstimada
        )
        repo.crearProyecto(proyecto) { result ->
            guardando = false
            result.onSuccess {
                cargarProyectos()
                onResult(true)
            }.onFailure {
                mensajeError = it.message
                onResult(false)
            }
        }
    }

    fun toggleTarea(proyectoId: String, tareaIndex: Int, completada: Boolean) {
        repo.actualizarTarea(proyectoId, tareaIndex, completada) { result ->
            result.onSuccess { cargarProyectos() }
        }
    }

    fun eliminarProyecto(proyectoId: String) {
        repo.eliminarProyecto(proyectoId) { ok ->
            if (ok) cargarProyectos()
        }
    }

    fun limpiarError() { mensajeError = null }
}