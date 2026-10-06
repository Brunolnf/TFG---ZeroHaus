package com.example.zerohaus.ViewModel

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Planes
import com.example.zerohaus.Modelos.Suscripcion
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Repositorios.RepositorioMonetizacion
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.BillingManager
import com.example.zerohaus.Util.ErrorCompra
import com.example.zerohaus.Util.PlanAnterior
import com.example.zerohaus.Util.getCadenas
import com.google.firebase.firestore.ListenerRegistration

/**
 * Estado de la pantalla de suscripción: perfil, suscripciones y precios de Google Play.
 */
data class SuscripcionEstado(
    val tecnico: Tecnico? = null,
    val suscripciones: List<Suscripcion> = emptyList(),
    // Hasta saber qué suscripción tiene, no se puede comprar: sin ella, un
    // cambio de plan se haría como compra nueva y se cobrarían las dos
    val suscripcionesCargadas: Boolean = false,
    val cargando: Boolean = false,
    val activando: Boolean = false,
    val error: String? = null,
    val exitoMensaje: String? = null,
    // Precios localizados que devuelve Google Play (productId -> "4,90 €")
    val precios: Map<String, String> = emptyMap(),
    val billingListo: Boolean = false
) {
    /** Suscripción vigente de mayor nivel (la que se sustituye al cambiar de plan). */
    val vigente: Suscripcion?
        get() {
            val ahora = System.currentTimeMillis()
            return suscripciones
                .filter { it.activa && it.fechaFin > ahora && it.id.isNotBlank() }
                .maxWithOrNull(compareBy<Suscripcion> { Planes.nivelPlan(it.plan) }.thenBy { it.fechaFin })
        }
}

/**
 * Compra de suscripciones con Google Play Billing y activación en el
 * servidor (Cloud Function `activar_suscripcion`).
 */
class SuscripcionViewModel : ViewModel() {

    var estado by mutableStateOf(SuscripcionEstado())
        private set

    private val repoMonetizacion = RepositorioMonetizacion()
    private var listenerPerfil: ListenerRegistration? = null
    private var listenerSubs: ListenerRegistration? = null
    private var billing: BillingManager? = null

    // true cuando el usuario acaba de comprar, cambiar de plan o restaurar: solo
    // entonces se le confirma. Las compras ya registradas que se revisan al
    // abrir la pantalla se procesan en silencio (antes salía "Suscripción
    // activada" cada vez que un profesional suscrito entraba).
    private var avisarActivacion = false

    fun iniciarBilling(activity: Activity) {
        if (billing != null) return
        val bm = BillingManager(activity)
        billing = bm

        bm.onSuscripcionPendiente = { productoId, token ->
            estado = estado.copy(activando = true, error = null)
            repoMonetizacion.activarSuscripcion(productoId, token) { result ->
                result
                    .onSuccess { resultado ->
                        // El servidor ya confirma la compra; esto es solo un respaldo
                        bm.acknowledge(token)
                        val avisar = avisarActivacion || resultado != "ya_activada"
                        avisarActivacion = false
                        estado = estado.copy(
                            activando = false,
                            exitoMensaje = if (avisar) getCadenas(AppEstado.idioma).subActivada else estado.exitoMensaje
                        )
                    }
                    .onFailure {
                        avisarActivacion = false
                        estado = estado.copy(activando = false, error = it.message)
                    }
            }
        }
        bm.onErrorCompra = { tipo ->
            val c = getCadenas(AppEstado.idioma)
            val msg = when (tipo) {
                ErrorCompra.NO_DISPONIBLE -> c.subPlanNoDisponible
                ErrorCompra.NO_INICIADA -> c.subErrorIniciar
                ErrorCompra.NO_COMPLETADA -> c.subErrorCompra
            }
            avisarActivacion = false
            estado = estado.copy(error = msg, activando = false)
        }
        bm.conectar {
            estado = estado.copy(precios = bm.precios(), billingListo = true)
            bm.procesarPendientes()
        }
    }

    fun cargar() {
        if (listenerPerfil != null) return
        estado = estado.copy(cargando = true)
        listenerPerfil = repoMonetizacion.escucharMiPerfilProfesional { t ->
            estado = estado.copy(tecnico = t, cargando = false)
        }
        listenerSubs = repoMonetizacion.escucharMisSuscripciones { lista ->
            estado = estado.copy(suscripciones = lista, suscripcionesCargadas = true)
        }
    }

    /**
     * Compra [productoId]. Si ya tiene un plan vigente, es un cambio de plan:
     * Google Play sustituye la suscripción en vez de crear una segunda.
     */
    fun suscribirse(activity: Activity, productoId: String) {
        if (!estado.suscripcionesCargadas) return
        val anterior = estado.vigente
            ?.takeIf { it.origen == "play" && it.planId.isNotBlank() && it.planId != productoId }
            ?.let { PlanAnterior(productoId = it.planId, purchaseToken = it.id) }
        avisarActivacion = true
        billing?.suscribirse(activity, productoId, anterior)
    }

    /**
     * Restaura las suscripciones activas (reinstalación / nuevo dispositivo).
     * Las compras encontradas se reenvían al servidor vía activar_suscripcion.
     */
    fun restaurarCompras() {
        val c = getCadenas(AppEstado.idioma)
        val bm = billing ?: run {
            estado = estado.copy(error = c.subPlayNoDisponible)
            return
        }
        avisarActivacion = true
        estado = estado.copy(activando = true, error = null)
        bm.restaurarCompras { n ->
            if (n <= 0) avisarActivacion = false
            estado = when {
                n < 0 -> estado.copy(activando = false, error = c.subPlayNoDisponible)
                n == 0 -> estado.copy(activando = false, exitoMensaje = c.subNadaRestaurar)
                else -> estado // la confirmación llega al activarlas en el servidor
            }
        }
    }

    /** productId de la suscripción vigente, para el deep link de gestión. */
    fun planIdActivo(): String? = estado.vigente?.planId

    fun limpiarMensajes() {
        estado = estado.copy(error = null, exitoMensaje = null)
    }

    override fun onCleared() {
        super.onCleared()
        listenerPerfil?.remove()
        listenerSubs?.remove()
        listenerPerfil = null
        listenerSubs = null
        billing?.liberar()
        billing = null
    }
}
