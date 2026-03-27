package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.SolicitudPresupuesto
import com.example.zerohaus.Repositorios.RepositorioTecnicos


data class PresupuestosEstado(
    val solicitudes: List<SolicitudPresupuesto> = emptyList(),
    val cargando: Boolean = true,
    val mensaje: String? = null,
    val error: String? = null
)

class PresupuestosViewModel : ViewModel() {

    var estado by mutableStateOf(PresupuestosEstado())
        private set

    private val repo = RepositorioTecnicos()

    fun cargarMisSolicitudes() {
        estado = estado.copy(cargando = true)
        repo.obtenerMisSolicitudes { lista ->
            estado = estado.copy(solicitudes = lista, cargando = false)
        }
    }

    fun aceptarPresupuesto(solicitudId: String) {
        repo.aceptarPresupuesto(solicitudId) { result ->
            result
                .onSuccess { estado = estado.copy(mensaje = "Presupuesto aceptado"); cargarMisSolicitudes() }
                .onFailure { estado = estado.copy(error = it.message) }
        }
    }

    fun rechazarPresupuesto(solicitudId: String) {
        repo.rechazarPresupuesto(solicitudId) { result ->
            result
                .onSuccess { estado = estado.copy(mensaje = "Presupuesto rechazado"); cargarMisSolicitudes() }
                .onFailure { estado = estado.copy(error = it.message) }
        }
    }

    fun limpiarMensaje() { estado = estado.copy(mensaje = null, error = null) }
}