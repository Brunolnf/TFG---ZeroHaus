package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Vivienda
import com.example.zerohaus.Repositorios.RepositorioViviendas
import com.example.zerohaus.Util.AppEstado


data class ViviendasEstado(
    val viviendas: List<Vivienda> = emptyList(),
    val viviendaSeleccionada: Vivienda? = null,
    val cargando: Boolean = false,
    val eliminando: Boolean = false,
    val mensaje: String? = null,
    val error: String? = null
)

class ViviendasViewModel : ViewModel() {

    var estado by mutableStateOf(ViviendasEstado())
        private set

    private val repo = RepositorioViviendas()

    init { cargarViviendas() }

    fun cargarViviendas(forzar: Boolean = false) {
        if (!forzar && estado.viviendas.isNotEmpty()) return
        // Solo mostramos el spinner si NO hay datos previos. Si forzamos un refresh
        // tras un mutado (eliminar/editar), la pantalla ya tiene contenido — un
        // spinner full-screen sería un parpadeo molesto.
        if (estado.viviendas.isEmpty()) estado = estado.copy(cargando = true)
        repo.obtenerViviendas { lista ->
            // Preserva la selección activa: primero la que está en memoria (sesión actual),
            // si no hay ninguna (primera carga tras abrir la app), restaura desde SharedPrefs.
            val seleccionPrevia = estado.viviendaSeleccionada
            val idABuscar = seleccionPrevia?.id ?: AppEstado.getViviendaSeleccionadaId()
            val nuevaSeleccion = lista.firstOrNull { it.id == idABuscar }
                ?: lista.firstOrNull()
            // Guardar el ID resultante para que persista en la próxima apertura
            nuevaSeleccion?.id?.let { AppEstado.setViviendaSeleccionadaId(it) }
            estado = estado.copy(
                viviendas = lista,
                viviendaSeleccionada = nuevaSeleccion,
                cargando = false
            )
        }
    }

    fun seleccionarVivienda(vivienda: Vivienda) {
        AppEstado.setViviendaSeleccionadaId(vivienda.id)
        estado = estado.copy(viviendaSeleccionada = vivienda)
    }

    fun eliminarVivienda(viviendaId: String) {
        // Borrado optimista: la quitamos de la lista YA, sin esperar al servidor.
        // Si el servidor falla, la recarga forzada de abajo la traerá de vuelta.
        val previas = estado.viviendas
        val restantes = previas.filterNot { it.id == viviendaId }
        val nuevaSeleccion = if (estado.viviendaSeleccionada?.id == viviendaId)
            restantes.firstOrNull() else estado.viviendaSeleccionada
        nuevaSeleccion?.id?.let { AppEstado.setViviendaSeleccionadaId(it) }
        estado = estado.copy(
            viviendas = restantes,
            viviendaSeleccionada = nuevaSeleccion,
            eliminando = true
        )
        repo.eliminarVivienda(viviendaId) { result ->
            result
                .onSuccess {
                    estado = estado.copy(eliminando = false, mensaje = "Vivienda eliminada")
                    cargarViviendas(forzar = true)
                }
                .onFailure {
                    // Rollback: el servidor rechazó el borrado, devolvemos la lista anterior.
                    estado = estado.copy(
                        viviendas = previas,
                        eliminando = false,
                        error = it.message
                    )
                }
        }
    }

    fun guardarVivienda(vivienda: Vivienda) {
        estado = estado.copy(cargando = true)
        repo.guardarVivienda(vivienda) { result ->
            result.onSuccess {
                estado = estado.copy(mensaje = "Vivienda actualizada")
                cargarViviendas(forzar = true)
            }.onFailure {
                estado = estado.copy(cargando = false, error = it.message)
            }
        }
    }

    fun limpiarMensaje() {
        estado = estado.copy(mensaje = null, error = null)
    }
}
