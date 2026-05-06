package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.SolicitudPresupuesto
import com.example.zerohaus.Repositorios.RepositorioTecnicos


data class PresupuestosEstado(
    val enviadas: List<SolicitudPresupuesto> = emptyList(),
    val recibidas: List<SolicitudPresupuesto> = emptyList(),
    val cargando: Boolean = true,
    val respondiendo: Boolean = false,
    val mensaje: String? = null,
    val error: String? = null
)

class PresupuestosViewModel : ViewModel() {

    var estado by mutableStateOf(PresupuestosEstado())
        private set

    private val repo = RepositorioTecnicos()

    fun cargarMisSolicitudes() {
        estado = estado.copy(cargando = true)
        var enviadas: List<SolicitudPresupuesto>? = null
        var recibidas: List<SolicitudPresupuesto>? = null

        fun checkDone() {
            if (enviadas != null && recibidas != null) {
                estado = estado.copy(enviadas = enviadas!!, recibidas = recibidas!!, cargando = false)
            }
        }

        repo.obtenerMisSolicitudes { lista -> enviadas = lista; checkDone() }
        repo.obtenerSolicitudesRecibidas { lista -> recibidas = lista; checkDone() }
    }

    fun responderPresupuesto(solicitudId: String, precio: Double, respuesta: String) {
        estado = estado.copy(respondiendo = true)
        repo.responderPresupuesto(solicitudId, precio, respuesta) { result ->
            result.onSuccess {
                estado = estado.copy(respondiendo = false, mensaje = "Presupuesto enviado al cliente")
                cargarMisSolicitudes()
            }.onFailure {
                estado = estado.copy(respondiendo = false, error = it.message)
            }
        }
    }

    fun aceptarPresupuesto(solicitudId: String) {
        repo.aceptarPresupuesto(solicitudId) { result ->
            result.onSuccess { estado = estado.copy(mensaje = "Presupuesto aceptado"); cargarMisSolicitudes() }
                .onFailure { estado = estado.copy(error = it.message) }
        }
    }

    fun rechazarPresupuesto(solicitudId: String) {
        repo.rechazarPresupuesto(solicitudId) { result ->
            result.onSuccess { estado = estado.copy(mensaje = "Presupuesto rechazado"); cargarMisSolicitudes() }
                .onFailure { estado = estado.copy(error = it.message) }
        }
    }

    fun completarSolicitud(solicitudId: String) {
        repo.completarSolicitud(solicitudId) { result ->
            result.onSuccess { estado = estado.copy(mensaje = "Reforma marcada como completada. Ya puedes valorar al técnico."); cargarMisSolicitudes() }
                .onFailure { estado = estado.copy(error = it.message) }
        }
    }

    fun limpiarMensaje() { estado = estado.copy(mensaje = null, error = null) }
}
