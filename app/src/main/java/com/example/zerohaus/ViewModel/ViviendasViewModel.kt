package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Vivienda
import com.example.zerohaus.Repositorios.RepositorioViviendas


data class ViviendasEstado(
    val viviendas: List<Vivienda> = emptyList(),
    val viviendaSeleccionada: Vivienda? = null,
    val cargando: Boolean = true,
    val eliminando: Boolean = false,
    val mensaje: String? = null,
    val error: String? = null
)

class ViviendasViewModel : ViewModel() {

    var estado by mutableStateOf(ViviendasEstado())
        private set

    private val repo = RepositorioViviendas()

    fun cargarViviendas() {
        estado = estado.copy(cargando = true)
        repo.obtenerViviendas { lista ->
            estado = estado.copy(
                viviendas = lista,
                viviendaSeleccionada = lista.firstOrNull(),
                cargando = false
            )
        }
    }

    fun seleccionarVivienda(vivienda: Vivienda) {
        estado = estado.copy(viviendaSeleccionada = vivienda)
    }

    fun eliminarVivienda(viviendaId: String) {
        estado = estado.copy(eliminando = true)
        repo.eliminarVivienda(viviendaId) { result ->
            result
                .onSuccess {
                    estado = estado.copy(eliminando = false, mensaje = "Vivienda eliminada")
                    cargarViviendas()
                }
                .onFailure {
                    estado = estado.copy(eliminando = false, error = it.message)
                }
        }
    }

    fun guardarVivienda(vivienda: Vivienda) {
        estado = estado.copy(cargando = true)
        repo.guardarVivienda(vivienda) { result ->
            result.onSuccess {
                estado = estado.copy(mensaje = "Vivienda actualizada")
                cargarViviendas()
            }.onFailure {
                estado = estado.copy(cargando = false, error = it.message)
            }
        }
    }

    fun limpiarMensaje() {
        estado = estado.copy(mensaje = null, error = null)
    }
}
