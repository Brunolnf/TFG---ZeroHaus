package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Proyecto
import com.example.zerohaus.Repositorios.RepositorioProyectos

class ProyectosViewModel : ViewModel() {

    var proyectos by mutableStateOf<List<Proyecto>>(emptyList())
        private set
    var cargando by mutableStateOf(true)
        private set

    private val repo = RepositorioProyectos()

    fun cargarProyectos() {
        cargando = true
        repo.obtenerProyectos { lista ->
            proyectos = lista
            cargando = false
        }
    }

    fun toggleTarea(proyectoId: String, tareaIndex: Int, completada: Boolean) {
        repo.actualizarTarea(proyectoId, tareaIndex, completada) { result ->
            result.onSuccess { cargarProyectos() } // Recarga para reflejar cambios
        }
    }
}