package com.example.zerohaus.ViewModel

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Planes
import com.example.zerohaus.Modelos.Suscripcion
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.esDestacado
import com.example.zerohaus.Modelos.esVerificado
import com.example.zerohaus.Repositorios.RepositorioMonetizacion
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.BillingManager
import com.example.zerohaus.Util.ErrorCompra
import com.example.zerohaus.Util.getCadenas
import com.google.firebase.firestore.ListenerRegistration

/**
 * Estado de la pantalla de suscripción: perfil, suscripciones y precios de Google Play.
 */
data class SuscripcionEstado(
    val tecnico: Tecnico? = null,
    val suscripciones: List<Suscripcion> = emptyList(),
    val cargando: Boolean = false,
    val activando: Boolean = false,
    val error: String? = null,
    val exitoMensaje: String? = null,
    // Precios localizados que devuelve Google Play (productId -> "4,90 €")
    val precios: Map<String, String> = emptyMap(),
    val billingListo: Boolean = false
)

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

    // Textos localizados para los mensajes de feedback (la UI los inyecta desde
    // LocalCadenas; se conservan valores por defecto en español por seguridad).
    private var txtActivada = "Suscripción activada"
    private var txtNadaRestaurar = "No hay suscripciones que restaurar."
    private var txtPlayNoDisponible = "Google Play no está disponible ahora mismo."

    fun configurarTextos(activada: String, nadaRestaurar: String, playNoDisponible: String) {
        txtActivada = activada
        txtNadaRestaurar = nadaRestaurar
        txtPlayNoDisponible = playNoDisponible
    }

    fun iniciarBilling(activity: Activity) {
        if (billing != null) return
        val bm = BillingManager(activity)
        billing = bm

        bm.onSuscripcionPendiente = { productoId, token ->
            estado = estado.copy(activando = true, error = null)
            repoMonetizacion.activarSuscripcion(productoId, token) { result ->
                result
                    .onSuccess {
                        // El servidor ya confirma la compra; esto es solo un respaldo
                        bm.acknowledge(token)
                        estado = estado.copy(activando = false, exitoMensaje = txtActivada)
                    }
                    .onFailure {
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
            estado = estado.copy(suscripciones = lista)
        }
    }

    fun suscribirse(activity: Activity, productoId: String) {
        billing?.suscribirse(activity, productoId)
    }

    /**
     * Restaura las suscripciones activas (reinstalación / nuevo dispositivo).
     * Las compras encontradas se reenvían al servidor vía activar_suscripcion,
     * que muestra el mensaje de éxito al reactivarlas.
     */
    fun restaurarCompras() {
        val bm = billing ?: run {
            estado = estado.copy(error = txtPlayNoDisponible)
            return
        }
        estado = estado.copy(activando = true, error = null)
        bm.restaurarCompras { n ->
            estado = when {
                n < 0 -> estado.copy(activando = false, error = txtPlayNoDisponible)
                n == 0 -> estado.copy(activando = false, exitoMensaje = txtNadaRestaurar)
                else -> estado.copy(activando = false) // activar_suscripcion emite el mensaje de éxito
            }
        }
    }

    /** productId de la suscripción actualmente vigente, para el deep link de gestión. */
    fun planIdActivo(): String? {
        val ahora = System.currentTimeMillis()
        return estado.suscripciones
            .filter { it.activa && it.fechaFin > ahora }
            .maxByOrNull { it.fechaFin }
            ?.planId
    }


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
