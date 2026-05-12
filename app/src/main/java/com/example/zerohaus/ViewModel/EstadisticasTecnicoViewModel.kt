package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Repositorios.RepositorioProyectos
import com.example.zerohaus.Repositorios.RepositorioTecnicos

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
    val cargando: Boolean = true
)

class EstadisticasTecnicoViewModel : ViewModel() {

    var estado by mutableStateOf(EstadisticasTecnicoEstado())
        private set

    private val repoTecnicos = RepositorioTecnicos()
    private val repoProyectos = RepositorioProyectos()

    fun cargar() {
        estado = estado.copy(cargando = true)
        repoTecnicos.obtenerMiPerfilTecnico { tec ->
            if (tec == null) {
                estado = EstadisticasTecnicoEstado(cargando = false)
                return@obtenerMiPerfilTecnico
            }

            // 1. Solicitudes recibidas
            repoTecnicos.obtenerSolicitudesRecibidas { solicitudes ->
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

                // 2. Proyectos asignados
                repoProyectos.obtenerProyectosAsignados(tec.nombre) { proyectos ->
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
            }
        }
    }
}
