package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Proyecto
import com.example.zerohaus.Modelos.Tarea
import com.example.zerohaus.Modelos.Vivienda
import com.example.zerohaus.Repositorios.RepositorioProyectos
import com.example.zerohaus.Repositorios.RepositorioViviendas

class ProyectosViewModel : ViewModel() {

    var proyectos by mutableStateOf<List<Proyecto>>(emptyList())
        private set
    var viviendas by mutableStateOf<List<Vivienda>>(emptyList())
        private set
    var cargando by mutableStateOf(true)
        private set
    var guardando by mutableStateOf(false)
        private set
    var mensajeError by mutableStateOf<String?>(null)
        private set

    private val repo = RepositorioProyectos()
    private val repoViviendas = RepositorioViviendas()

    fun cargarProyectos() {
        cargando = true
        repo.obtenerProyectos { lista ->
            proyectos = lista
            cargando = false
        }
        repoViviendas.obtenerViviendas { lista -> viviendas = lista }
    }

    fun crearProyecto(
        titulo: String,
        descripcion: String,
        viviendaNombre: String,
        tareas: List<Tarea>,
        fechaFinEstimada: Long = 0L,
        onResult: (Boolean) -> Unit
    ) {
        guardando = true
        val proyecto = Proyecto(
            titulo = titulo,
            descripcion = descripcion,
            viviendaNombre = viviendaNombre,
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