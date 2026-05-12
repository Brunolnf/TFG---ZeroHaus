package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.*
import com.example.zerohaus.Repositorios.*
import com.example.zerohaus.Util.NotificacionesLocales
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

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

    private var listenerNotifs: ListenerRegistration? = null
    private var notifIdsVistos = emptySet<String>()

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        if (auth.currentUser == null) {
            listenerNotifs?.remove()
            listenerNotifs = null
            notifIdsVistos = emptySet()
        }
    }

    init {
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
    }

    fun cargarDatos() {
        estado = estado.copy(cargando = true)
        repoAuth.obtenerUsuario { usuario ->
            estado = estado.copy(usuario = usuario)
            repoViviendas.obtenerViviendas { viviendas ->
                estado = estado.copy(vivienda = viviendas.firstOrNull())
                repoInformes.obtenerUltimoInforme { informe ->
                    estado = estado.copy(ultimoInforme = informe, cargando = false)
                    arrancarListenerNotificaciones()
                }
            }
        }
    }

    private fun arrancarListenerNotificaciones() {
        listenerNotifs?.remove()
        listenerNotifs = repoNotificaciones.escucharNotificaciones { notifs ->
            // Si ya teníamos notifs cargadas, mostrar push local para las nuevas no leídas
            if (notifIdsVistos.isNotEmpty()) {
                notifs.filter { !it.leida && it.id !in notifIdsVistos }
                    .forEach { n -> NotificacionesLocales.mostrar(n.titulo, n.detalle, n.tipo) }
            }
            notifIdsVistos = notifs.map { it.id }.toSet()
            estado = estado.copy(
                notificaciones = notifs,
                hayNoLeidas = notifs.any { !it.leida }
            )
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

    override fun onCleared() {
        super.onCleared()
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        listenerNotifs?.remove()
    }
}
