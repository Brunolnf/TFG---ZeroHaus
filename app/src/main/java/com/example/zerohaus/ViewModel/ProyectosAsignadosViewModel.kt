package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Proyecto
import com.example.zerohaus.Repositorios.RepositorioProyectos
import com.example.zerohaus.Repositorios.RepositorioTecnicos

data class ProyectosAsignadosEstado(
    val proyectos: List<Proyecto> = emptyList(),
    val cargando: Boolean = true,
    val mensaje: String? = null
)

class ProyectosAsignadosViewModel : ViewModel() {

    var estado by mutableStateOf(ProyectosAsignadosEstado())
        private set

    private val repoProyectos = RepositorioProyectos()
    private val repoTecnicos = RepositorioTecnicos()

    fun cargar() {
        estado = estado.copy(cargando = true)
        repoTecnicos.obtenerMiPerfilTecnico { miPerfil ->
            if (miPerfil == null) {
                estado = ProyectosAsignadosEstado(cargando = false, mensaje = "No tienes un perfil de técnico vinculado.")
                return@obtenerMiPerfilTecnico
            }
            repoProyectos.obtenerProyectosAsignados(miPerfil.nombre) { lista ->
                estado = estado.copy(proyectos = lista, cargando = false)
            }
        }
    }

    fun toggleTarea(proyectoId: String, tareaIndex: Int, completada: Boolean) {
        repoProyectos.actualizarTarea(proyectoId, tareaIndex, completada) { result ->
            result.onSuccess {
                estado = estado.copy(mensaje = "Tarea actualizada")
                cargar()
            }
            result.onFailure { e ->
                estado = estado.copy(mensaje = "Error: ${e.message}")
            }
        }
    }

    /** Marca el trabajo del proyecto como terminado (estado: PendientePago en la solicitud). */
    fun marcarTrabajoTerminado(solicitudId: String) {
        repoTecnicos.marcarTrabajoTerminado(solicitudId) { result ->
            result.onSuccess {
                estado = estado.copy(mensaje = "Trabajo marcado como terminado. El cliente puede pagar.")
                cargar()
            }
            result.onFailure { e -> estado = estado.copy(mensaje = "Error: ${e.message}") }
        }
    }

    fun limpiarMensaje() { estado = estado.copy(mensaje = null) }
}
