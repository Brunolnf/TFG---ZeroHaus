package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.SolicitudPresupuesto
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Repositorios.RepositorioTecnicos


data class PresupuestosEstado(
    val enviadas: List<SolicitudPresupuesto> = emptyList(),
    val recibidas: List<SolicitudPresupuesto> = emptyList(),
    val tecnicosCache: Map<String, Tecnico> = emptyMap(),  // tecnicoId → Tecnico (para PayPal/Bizum)
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
                // Tras cargar las enviadas (cliente), pre-cargamos los técnicos para conocer
                // sus métodos de pago (PayPal/Bizum) cuando se pulse "Pagar"
                cargarTecnicosDeEnviadas(enviadas!!)
            }
        }

        repo.obtenerMisSolicitudes { lista -> enviadas = lista; checkDone() }
        repo.obtenerSolicitudesRecibidas { lista -> recibidas = lista; checkDone() }
    }

    private fun cargarTecnicosDeEnviadas(enviadas: List<SolicitudPresupuesto>) {
        val ids = enviadas.map { it.tecnicoId }.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return
        ids.forEach { id ->
            if (estado.tecnicosCache[id] != null) return@forEach
            repo.obtenerTecnico(id) { tec ->
                if (tec != null) {
                    estado = estado.copy(tecnicosCache = estado.tecnicosCache + (id to tec))
                }
            }
        }
    }

    /** Devuelve el técnico cacheado a partir del ID en la solicitud. */
    fun tecnicoDe(solicitud: SolicitudPresupuesto): Tecnico? = estado.tecnicosCache[solicitud.tecnicoId]

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

    /** TÉCNICO — envía la ficha de inicio (estado: Aceptado → FichaEnviada). */
    fun enviarFichaActividad(
        solicitudId: String,
        fechaInicio: Long,
        fechaFinEstimada: Long,
        descripcion: String,
        precioFinal: Double,
        tareas: List<String>
    ) {
        estado = estado.copy(respondiendo = true)
        repo.enviarFichaActividad(solicitudId, fechaInicio, fechaFinEstimada, descripcion, precioFinal, tareas) { result ->
            result.onSuccess {
                estado = estado.copy(respondiendo = false, mensaje = "Ficha enviada al cliente")
                cargarMisSolicitudes()
            }.onFailure { estado = estado.copy(respondiendo = false, error = it.message) }
        }
    }

    /** CLIENTE — acepta la ficha. Crea automáticamente el proyecto. */
    fun aceptarFichaYCrearProyecto(solicitudId: String) {
        repo.aceptarFichaYCrearProyecto(solicitudId) { result ->
            result.onSuccess {
                estado = estado.copy(mensaje = "Reforma iniciada. Ya puedes ver el proyecto en \"Mis proyectos\".")
                cargarMisSolicitudes()
            }.onFailure { estado = estado.copy(error = it.message) }
        }
    }

    /** CLIENTE — rechaza la ficha (vuelve a "Aceptado" para que el técnico la ajuste). */
    fun rechazarFicha(solicitudId: String, motivo: String = "") {
        repo.rechazarFicha(solicitudId, motivo) { result ->
            result.onSuccess { estado = estado.copy(mensaje = "Ficha rechazada"); cargarMisSolicitudes() }
                .onFailure { estado = estado.copy(error = it.message) }
        }
    }

    /** TÉCNICO — marca el trabajo como terminado, pendiente de pago. */
    fun marcarTrabajoTerminado(solicitudId: String) {
        repo.marcarTrabajoTerminado(solicitudId) { result ->
            result.onSuccess { estado = estado.copy(mensaje = "Trabajo marcado como terminado. El cliente puede pagar."); cargarMisSolicitudes() }
                .onFailure { estado = estado.copy(error = it.message) }
        }
    }

    /** CLIENTE — declara que ya pagó (PayPal/Bizum). Pasa a "PagoEnVerificacion". */
    fun marcarPagado(solicitudId: String, metodo: String, referencia: String = "") {
        estado = estado.copy(respondiendo = true)
        repo.clienteMarcaPagado(solicitudId, metodo, referencia) { result ->
            result.onSuccess {
                estado = estado.copy(respondiendo = false, mensaje = "Pago notificado al técnico. Esperando su confirmación.")
                cargarMisSolicitudes()
            }.onFailure { estado = estado.copy(respondiendo = false, error = it.message) }
        }
    }

    /** TÉCNICO — confirma haber recibido el pago. Cierra todo y permite valorar al cliente. */
    fun confirmarCobro(solicitudId: String) {
        estado = estado.copy(respondiendo = true)
        repo.tecnicoConfirmaPago(solicitudId) { result ->
            result.onSuccess {
                estado = estado.copy(respondiendo = false, mensaje = "Cobro confirmado. Reforma cerrada.")
                cargarMisSolicitudes()
            }.onFailure { estado = estado.copy(respondiendo = false, error = it.message) }
        }
    }

    /** CLIENTE — anula la declaración de pago (vuelve a PendientePago). */
    fun cancelarMarcaPago(solicitudId: String) {
        repo.cancelarMarcaPago(solicitudId) { result ->
            result.onSuccess {
                estado = estado.copy(mensaje = "Notificación de pago cancelada")
                cargarMisSolicitudes()
            }.onFailure { estado = estado.copy(error = it.message) }
        }
    }

    fun limpiarMensaje() { estado = estado.copy(mensaje = null, error = null) }
}
