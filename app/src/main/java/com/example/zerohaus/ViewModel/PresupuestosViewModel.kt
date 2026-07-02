package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.SolicitudPresupuesto
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.estaExpirada
import com.example.zerohaus.Repositorios.RepositorioTecnicos
import com.google.firebase.firestore.ListenerRegistration


data class PresupuestosEstado(
    val enviadas: List<SolicitudPresupuesto> = emptyList(),
    val recibidas: List<SolicitudPresupuesto> = emptyList(),
    val tecnicosCache: Map<String, Tecnico> = emptyMap(),
    val cargando: Boolean = false,
    val respondiendo: Boolean = false,
    val mensaje: String? = null,
    val error: String? = null
)

class PresupuestosViewModel : ViewModel() {

    var estado by mutableStateOf(PresupuestosEstado())
        private set

    private val repo = RepositorioTecnicos()

    private var listenerEnviadas: ListenerRegistration? = null
    private var listenerRecibidas: ListenerRegistration? = null

    init { cargarMisSolicitudes(forzar = true) }

    /**
     * Arranca los listeners en tiempo real (una sola vez). A partir de ahí, cualquier
     * solicitud nueva o cambio de estado se refleja AL INSTANTE sin recargar la app.
     * El parámetro [forzar] se mantiene por compatibilidad con las llamadas existentes,
     * pero ya no fuerza re-fetch: si los listeners están activos, no hace falta.
     */
    fun cargarMisSolicitudes(forzar: Boolean = false) {
        if (listenerEnviadas != null && listenerRecibidas != null) return  // ya en tiempo real
        if (estado.enviadas.isEmpty() && estado.recibidas.isEmpty()) {
            estado = estado.copy(cargando = true)
        }
        listenerEnviadas?.remove()
        listenerRecibidas?.remove()

        // CLIENTE — mis solicitudes enviadas. Soy el dueño → limpio expiradas y duplicados.
        listenerEnviadas = repo.escucharMisSolicitudes { lista ->
            val expiradas = lista.filter { it.estaExpirada }
            expiradas.forEach { s -> repo.cancelarSolicitud(s.id) { /* silencioso */ } }
            val visibles = colapsarDuplicados(lista.filterNot { it.estaExpirada }, borrar = true)
            estado = estado.copy(enviadas = visibles, cargando = false)
            cargarTecnicosDeEnviadas(visibles)
        }

        // TÉCNICO — solicitudes recibidas. No soy dueño → solo oculto duplicados.
        listenerRecibidas = repo.escucharSolicitudesRecibidas { lista ->
            estado = estado.copy(recibidas = colapsarDuplicados(lista, borrar = false), cargando = false)
        }
    }

    /**
     * Colapsa duplicados accidentales: dos solicitudes con el mismo técnico, descripción
     * y estado creadas con < 60 s de diferencia son el mismo envío disparado dos veces.
     * Nos quedamos con la más antigua (la "original"). Primero dedup por id (por si la
     * misma solicitud llegara repetida) y luego por contenido.
     *
     * Si [borrar] es true, además ELIMINA de Firestore las copias sobrantes (solo válido
     * para la lista del cliente, que es su dueño). Tras el borrado la próxima carga ya no
     * las verá. Para la lista del técnico [borrar]=false: no es dueño, solo se ocultan.
     */
    private fun colapsarDuplicados(
        lista: List<SolicitudPresupuesto>,
        borrar: Boolean
    ): List<SolicitudPresupuesto> {
        val porId = lista.distinctBy { it.id }.sortedBy { it.fechaCreacion }
        val resultado = mutableListOf<SolicitudPresupuesto>()
        porId.forEach { s ->
            val yaHay = resultado.any { prev ->
                prev.uidCliente == s.uidCliente &&
                    prev.tecnicoId == s.tecnicoId &&
                    prev.descripcion == s.descripcion &&
                    prev.estado == s.estado &&
                    kotlin.math.abs(prev.fechaCreacion - s.fechaCreacion) <= 60_000L
            }
            if (!yaHay) resultado.add(s)
            else if (borrar && s.id.isNotBlank()) repo.cancelarSolicitud(s.id) { /* silencioso */ }
        }
        return resultado.sortedByDescending { it.fechaCreacion }
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
        if (estado.respondiendo) return
        estado = estado.copy(respondiendo = true)
        repo.responderPresupuesto(solicitudId, precio, respuesta) { result ->
            result.onSuccess {
                estado = estado.copy(respondiendo = false, mensaje = "Presupuesto enviado al cliente")
                cargarMisSolicitudes(forzar = true)
            }.onFailure {
                estado = estado.copy(respondiendo = false, error = it.message)
            }
        }
    }

    fun aceptarPresupuesto(solicitudId: String) {
        if (estado.respondiendo) return
        estado = estado.copy(respondiendo = true)
        repo.aceptarPresupuesto(solicitudId) { result ->
            result.onSuccess { estado = estado.copy(respondiendo = false, mensaje = "Presupuesto aceptado"); cargarMisSolicitudes(forzar = true) }
                .onFailure { estado = estado.copy(respondiendo = false, error = it.message) }
        }
    }

    fun rechazarPresupuesto(solicitudId: String) {
        if (estado.respondiendo) return
        estado = estado.copy(respondiendo = true)
        repo.rechazarPresupuesto(solicitudId) { result ->
            result.onSuccess { estado = estado.copy(respondiendo = false, mensaje = "Presupuesto rechazado"); cargarMisSolicitudes(forzar = true) }
                .onFailure { estado = estado.copy(respondiendo = false, error = it.message) }
        }
    }

    fun completarSolicitud(solicitudId: String) {
        if (estado.respondiendo) return
        estado = estado.copy(respondiendo = true)
        repo.completarSolicitud(solicitudId) { result ->
            result.onSuccess { estado = estado.copy(respondiendo = false, mensaje = "Reforma marcada como completada. Ya puedes valorar al técnico."); cargarMisSolicitudes(forzar = true) }
                .onFailure { estado = estado.copy(respondiendo = false, error = it.message) }
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
        if (estado.respondiendo) return
        estado = estado.copy(respondiendo = true)
        repo.enviarFichaActividad(solicitudId, fechaInicio, fechaFinEstimada, descripcion, precioFinal, tareas) { result ->
            result.onSuccess {
                estado = estado.copy(respondiendo = false, mensaje = "Ficha enviada al cliente")
                cargarMisSolicitudes(forzar = true)
            }.onFailure { estado = estado.copy(respondiendo = false, error = it.message) }
        }
    }

    /** CLIENTE — acepta la ficha. Crea automáticamente el proyecto. */
    fun aceptarFichaYCrearProyecto(solicitudId: String) {
        // Sin guard, un doble tap aceptaría la ficha dos veces y, como cada llamada
        // crea un nuevo doc en /proyectos con ID aleatorio, acabaríamos con DOS
        // proyectos vinculados a la misma solicitud.
        if (estado.respondiendo) return
        estado = estado.copy(respondiendo = true)
        repo.aceptarFichaYCrearProyecto(solicitudId) { result ->
            result.onSuccess {
                estado = estado.copy(respondiendo = false, mensaje = "Reforma iniciada. Ya puedes ver el proyecto en \"Mis proyectos\".")
                cargarMisSolicitudes(forzar = true)
            }.onFailure { estado = estado.copy(respondiendo = false, error = it.message) }
        }
    }

    /** CLIENTE — rechaza la ficha (estado pasa a "FichaRechazada", el técnico debe reenviarla). */
    fun rechazarFicha(solicitudId: String, motivo: String = "") {
        if (estado.respondiendo) return
        estado = estado.copy(respondiendo = true)
        repo.rechazarFicha(solicitudId, motivo) { result ->
            result.onSuccess { estado = estado.copy(respondiendo = false, mensaje = "Ficha rechazada"); cargarMisSolicitudes(forzar = true) }
                .onFailure { estado = estado.copy(respondiendo = false, error = it.message) }
        }
    }

    /** TÉCNICO — marca el trabajo como terminado, pendiente de pago. */
    fun marcarTrabajoTerminado(solicitudId: String) {
        if (estado.respondiendo) return
        estado = estado.copy(respondiendo = true)
        repo.marcarTrabajoTerminado(solicitudId) { result ->
            result.onSuccess { estado = estado.copy(respondiendo = false, mensaje = "Trabajo marcado como terminado. El cliente puede pagar."); cargarMisSolicitudes(forzar = true) }
                .onFailure { estado = estado.copy(respondiendo = false, error = it.message) }
        }
    }

    /** CLIENTE — declara que ya pagó (tarjeta/efectivo). Pasa a "PagoEnVerificacion". */
    fun marcarPagado(solicitudId: String, metodo: String, referencia: String = "") {
        if (estado.respondiendo) return
        estado = estado.copy(respondiendo = true)
        repo.clienteMarcaPagado(solicitudId, metodo, referencia) { result ->
            result.onSuccess {
                estado = estado.copy(respondiendo = false, mensaje = "Pago notificado al técnico. Esperando su confirmación.")
                cargarMisSolicitudes(forzar = true)
            }.onFailure { estado = estado.copy(respondiendo = false, error = it.message) }
        }
    }

    /** TÉCNICO — confirma haber recibido el pago. Cierra todo y permite valorar al cliente. */
    fun confirmarCobro(solicitudId: String) {
        // Cada confirmación crea un nuevo doc en /pagos (histórico). Un doble tap
        // sin guard generaría dos entradas idénticas en el historial de cobros.
        if (estado.respondiendo) return
        estado = estado.copy(respondiendo = true)
        repo.tecnicoConfirmaPago(solicitudId) { result ->
            result.onSuccess {
                estado = estado.copy(respondiendo = false, mensaje = "Cobro confirmado. Reforma cerrada.")
                cargarMisSolicitudes(forzar = true)
            }.onFailure { estado = estado.copy(respondiendo = false, error = it.message) }
        }
    }

    /** CLIENTE — anula la declaración de pago (vuelve a PendientePago). */
    fun cancelarMarcaPago(solicitudId: String) {
        if (estado.respondiendo) return
        estado = estado.copy(respondiendo = true)
        repo.cancelarMarcaPago(solicitudId) { result ->
            result.onSuccess {
                estado = estado.copy(respondiendo = false, mensaje = "Notificación de pago cancelada")
                cargarMisSolicitudes(forzar = true)
            }.onFailure { estado = estado.copy(respondiendo = false, error = it.message) }
        }
    }

    /** CLIENTE — cancela (elimina) una solicitud en estado Pendiente o Presupuestado. */
    fun cancelarSolicitud(solicitudId: String) {
        if (estado.respondiendo) return
        estado = estado.copy(respondiendo = true)
        repo.cancelarSolicitud(solicitudId) { result ->
            result.onSuccess {
                estado = estado.copy(respondiendo = false, mensaje = "Solicitud cancelada")
                cargarMisSolicitudes(forzar = true)
            }.onFailure { estado = estado.copy(respondiendo = false, error = it.message) }
        }
    }

    fun limpiarMensaje() { estado = estado.copy(mensaje = null, error = null) }

    override fun onCleared() {
        super.onCleared()
        listenerEnviadas?.remove()
        listenerRecibidas?.remove()
        listenerEnviadas = null
        listenerRecibidas = null
    }
}
