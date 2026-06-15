package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Proyecto
import com.example.zerohaus.Modelos.SolicitudPresupuesto
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Repositorios.RepositorioProyectos
import com.example.zerohaus.Repositorios.RepositorioTecnicos
import com.google.firebase.firestore.ListenerRegistration

data class EstadisticasTecnicoEstado(
    val tecnico: Tecnico? = null,
    val solicitudesTotales: Int = 0,
    val solicitudesPendientes: Int = 0,
    val solicitudesPresupuestadas: Int = 0,
    val solicitudesAceptadas: Int = 0,
    val solicitudesRechazadas: Int = 0,
    val solicitudesCompletadas: Int = 0,
    val ingresosTotales: Double = 0.0,
    val ingresosPotenciales: Double = 0.0,
    val tasaAceptacion: Int = 0, // %
    val tasaRespuesta: Int = 0,  // %
    val proyectosActivos: Int = 0,
    val proyectosCompletados: Int = 0,
    val cargando: Boolean = false
)

class EstadisticasTecnicoViewModel : ViewModel() {

    var estado by mutableStateOf(EstadisticasTecnicoEstado())
        private set

    private val repoTecnicos = RepositorioTecnicos()
    private val repoProyectos = RepositorioProyectos()

    private var listenerSolicitudes: ListenerRegistration? = null
    private var listenerProyectos: ListenerRegistration? = null
    private var tecnicoActual: Tecnico? = null
    private var ultimasSolicitudes: List<SolicitudPresupuesto> = emptyList()
    private var ultimosProyectos: List<Proyecto> = emptyList()

    init { cargar() }

    /**
     * Estadísticas en tiempo real. Antes era one-shot cacheado: si llegaba una
     * solicitud o cambiaba un estado, los números no se movían hasta reabrir.
     */
    fun cargar(forzar: Boolean = false) {
        if (listenerSolicitudes != null && listenerProyectos != null) return
        estado = estado.copy(cargando = true)
        repoTecnicos.obtenerMiPerfilTecnico { tec ->
            if (tec == null) {
                estado = EstadisticasTecnicoEstado(cargando = false)
                return@obtenerMiPerfilTecnico
            }
            tecnicoActual = tec

            listenerSolicitudes = repoTecnicos.escucharSolicitudesRecibidas { lista ->
                ultimasSolicitudes = lista
                recomponer()
            }
            listenerProyectos = repoProyectos.escucharProyectosAsignados { lista ->
                ultimosProyectos = lista
                recomponer()
            }
        }
    }

    private fun recomponer() {
        val tec = tecnicoActual ?: return
        val solicitudes = ultimasSolicitudes
        val proyectos = ultimosProyectos

        val pendientes = solicitudes.count { it.estado == "Pendiente" }
        val presupuestadas = solicitudes.count { it.estado == "Presupuestado" }
        val aceptadas = solicitudes.count { it.estado == "Aceptado" }
        val rechazadas = solicitudes.count { it.estado == "Rechazado" }
        val completadas = solicitudes.count { it.estado == "Completado" }

        val ingresosTotales = solicitudes
            .filter { it.estado == "Aceptado" || it.estado == "Completado" }
            .sumOf { it.precioPresupuesto }
        val ingresosPotenciales = solicitudes
            .filter { it.estado == "Presupuestado" }
            .sumOf { it.precioPresupuesto }

        val totalRespondidas = presupuestadas + aceptadas + rechazadas + completadas
        val tasaRespuesta = if (solicitudes.isNotEmpty())
            (totalRespondidas * 100) / solicitudes.size else 0
        val tasaAceptacion = if (totalRespondidas > 0)
            ((aceptadas + completadas) * 100) / totalRespondidas else 0

        val activos = proyectos.count { it.estado != "Finalizado" }
        val completos = proyectos.count { it.estado == "Finalizado" }

        estado = EstadisticasTecnicoEstado(
            tecnico = tec,
            solicitudesTotales = solicitudes.size,
            solicitudesPendientes = pendientes,
            solicitudesPresupuestadas = presupuestadas,
            solicitudesAceptadas = aceptadas,
            solicitudesRechazadas = rechazadas,
            solicitudesCompletadas = completadas,
            ingresosTotales = ingresosTotales,
            ingresosPotenciales = ingresosPotenciales,
            tasaAceptacion = tasaAceptacion,
            tasaRespuesta = tasaRespuesta,
            proyectosActivos = activos,
            proyectosCompletados = completos,
            cargando = false
        )
    }

    override fun onCleared() {
        super.onCleared()
        listenerSolicitudes?.remove()
        listenerProyectos?.remove()
        listenerSolicitudes = null
        listenerProyectos = null
    }
}
