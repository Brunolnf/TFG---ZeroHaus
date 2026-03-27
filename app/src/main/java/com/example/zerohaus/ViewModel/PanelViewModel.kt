package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.*
import com.example.zerohaus.Repositorios.*


data class PanelEstado(
    val usuario: Usuario? = null,
    val vivienda: Vivienda? = null,
    val ultimoInforme: InformeEnergetico? = null,
    val notificaciones: List<Notificacion> = emptyList(),
    val hayNoLeidas: Boolean = false,
    val cargando: Boolean = true
)

class PanelViewModel : ViewModel() {

    var estado by mutableStateOf(PanelEstado())
        private set

    private val repoAuth = RepositorioAutenticacion()
    private val repoViviendas = RepositorioViviendas()
    private val repoInformes = RepositorioInformes()
    private val repoNotificaciones = RepositorioNotificaciones()

    fun cargarDatos() {
        estado = estado.copy(cargando = true)

        repoAuth.obtenerUsuario { usuario ->
            estado = estado.copy(usuario = usuario)

            repoViviendas.obtenerViviendas { viviendas ->
                estado = estado.copy(vivienda = viviendas.firstOrNull())

                repoInformes.obtenerUltimoInforme { informe ->
                    estado = estado.copy(ultimoInforme = informe)

                    repoNotificaciones.obtenerNotificaciones { notifs ->
                        estado = estado.copy(
                            notificaciones = notifs,
                            hayNoLeidas = notifs.any { !it.leida },
                            cargando = false
                        )
                    }
                }
            }
        }
    }

    fun marcarTodasLeidas() {
        repoNotificaciones.marcarTodasLeidas { result ->
            result.onSuccess {
                val actualizadas = estado.notificaciones.map { it.copy(leida = true) }
                estado = estado.copy(notificaciones = actualizadas, hayNoLeidas = false)
            }
        }
    }

    fun marcarLeida(notificacionId: String) {
        repoNotificaciones.marcarLeida(notificacionId) { result ->
            result.onSuccess {
                val actualizadas = estado.notificaciones.map {
                    if (it.id == notificacionId) it.copy(leida = true) else it
                }
                estado = estado.copy(
                    notificaciones = actualizadas,
                    hayNoLeidas = actualizadas.any { !it.leida }
                )
            }
        }
    }
}
