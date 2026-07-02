package com.example.zerohaus.ViewModel

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.*
import com.example.zerohaus.Repositorios.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

data class PanelEstado(
    val usuario: Usuario? = null,
    val vivienda: Vivienda? = null,
    val ultimoInforme: InformeEnergetico? = null,
    val notificaciones: List<Notificacion> = emptyList(),
    val hayNoLeidas: Boolean = false,
    val cargando: Boolean = false
)

class PanelViewModel : ViewModel() {

    var estado by mutableStateOf(PanelEstado())
        private set

    private val repoAuth = RepositorioAutenticacion()
    private val repoViviendas = RepositorioViviendas()
    private val repoInformes = RepositorioInformes()
    private val repoNotificaciones = RepositorioNotificaciones()

    private var listenerNotifs: ListenerRegistration? = null
    private val watchdog = Handler(Looper.getMainLooper())

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        if (auth.currentUser == null) {
            listenerNotifs?.remove()
            listenerNotifs = null
            watchdog.removeCallbacksAndMessages(null)
            // Reset limpio: cargando = false para no dejar spinner huérfano.
            estado = PanelEstado(cargando = false)
        }
    }

    init {
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        // Carga inmediata: los datos llegan de la caché de Firestore antes
        // de que el composable haga su primer render.
        if (FirebaseAuth.getInstance().currentUser != null) cargarDatos()
    }

    fun cargarDatos(forzar: Boolean = false) {
        if (!forzar && estado.usuario != null) return   // ya tiene datos
        if (!forzar && estado.cargando) return           // carga en curso (init)
        estado = estado.copy(cargando = true)
        // Watchdog: si en 8s las queries ni responden ni fallan (App Check / red
        // colgada), soltamos el spinner igualmente. Los datos se pintarán cuando
        // (si) lleguen los callbacks.
        watchdog.removeCallbacksAndMessages(null)
        watchdog.postDelayed({
            if (estado.cargando) {
                estado = estado.copy(cargando = false)
                arrancarListenerNotificaciones()
            }
        }, 8000L)
        // Las 3 consultas en paralelo; cuando completan las 3 se oculta el spinner.
        var pendiente = 3
        fun onCompletada() {
            if (--pendiente == 0) {
                watchdog.removeCallbacksAndMessages(null)
                estado = estado.copy(cargando = false)
                arrancarListenerNotificaciones()
            }
        }

        repoAuth.obtenerUsuario { u ->
            estado = estado.copy(usuario = u)
            onCompletada()
        }
        repoViviendas.obtenerViviendas { viviendas ->
            estado = estado.copy(vivienda = viviendas.firstOrNull())
            onCompletada()
        }
        repoInformes.obtenerUltimoInforme { informe ->
            estado = estado.copy(ultimoInforme = informe)
            onCompletada()
        }
    }

    private fun arrancarListenerNotificaciones() {
        listenerNotifs?.remove()
        listenerNotifs = repoNotificaciones.escucharNotificaciones { notifs ->
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
        watchdog.removeCallbacksAndMessages(null)
    }
}
