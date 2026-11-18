
package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.AjustesUsuario
import com.example.zerohaus.Modelos.Moderacion
import com.example.zerohaus.Modelos.UsuarioBloqueado
import com.example.zerohaus.Repositorios.RepositorioModeracion
import com.google.firebase.firestore.ListenerRegistration
import com.example.zerohaus.Modelos.esVerificado
import com.example.zerohaus.Repositorios.RepositorioTecnicos
import com.example.zerohaus.Repositorios.RepositorioAjustes
import com.example.zerohaus.Repositorios.RepositorioAutenticacion
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.getCadenas

/**
 * Estado de la pantalla de ajustes.
 */
data class AjustesEstado(
    val ajustes: AjustesUsuario = AjustesUsuario(),
    val cargando: Boolean = false,
    val guardando: Boolean = false,
    val cambiandoPassword: Boolean = false,
    val eliminandoCuenta: Boolean = false,
    /** Profesional con plan vigente (null = aún sin saber: se muestra el aviso por defecto). */
    val planVigente: Boolean? = null,
    /** Cuenta ya borrada pero con la suscripción por cancelar a mano en Play; sobrevive a la recreación. */
    val avisoCancelarEnPlay: Boolean = false,
    val mensajeToast: String? = null,
    val error: String? = null,
    /** Usuarios que he bloqueado (con nombre si se ha podido resolver). */
    val bloqueados: List<UsuarioBloqueado> = emptyList()
)

/**
 * Carga y guarda los ajustes del usuario y gestiona la seguridad de la cuenta.
 */
class AjustesViewModel : ViewModel() {
    var estado by mutableStateOf(AjustesEstado())
        private set
    private val repo = RepositorioAjustes()
    private val repoAuth = RepositorioAutenticacion()
    private val repoModeracion = RepositorioModeracion()
    private val nombresBloqueados = mutableMapOf<String, String>()
    // Uids ya intentados (también los que no se resolvieron): no se vuelven a leer
    private val nombresIntentados = mutableSetOf<String>()
    private var resolucion = 0
    private var listenerBloqueos: ListenerRegistration? = null

    /** Si el listener murió por un error se vuelve a suscribir al recargar. */
    private fun escucharBloqueos() {
        if (listenerBloqueos != null) return
        listenerBloqueos = repoModeracion.escucharMisBloqueos(
            alFallar = { listenerBloqueos?.remove(); listenerBloqueos = null }
        ) { uids -> actualizarBloqueados(uids) }
    }

    init {
        escucharBloqueos()
        cargarAjustes()
        cargarPlan()
    }

    private fun actualizarBloqueados(uids: Set<String>) {
        estado = estado.copy(bloqueados = Moderacion.combinarBloqueados(uids, nombresBloqueados))
        val faltan = uids.filter { it !in nombresIntentados }.toSet()
        if (faltan.isEmpty()) return
        // Una resolución nueva cancela la anterior (sus uids sin resolver se piden de nuevo)
        val miResolucion = ++resolucion
        repoModeracion.resolverNombres(faltan, cancelado = { miResolucion != resolucion }) { nombres, fallidos ->
            // Los que fallaron por error se reintentan la próxima vez
            nombresIntentados.addAll(faltan - fallidos)
            nombresBloqueados.putAll(nombres)
            // Los uids actuales, no los de la consulta: el usuario pudo desbloquear mientras tanto
            val actuales = estado.bloqueados.map { it.uid }.toSet()
            estado = estado.copy(bloqueados = Moderacion.combinarBloqueados(actuales, nombresBloqueados))
        }
    }

    fun desbloquear(uid: String) {
        repoModeracion.desbloquear(uid) { ok ->
            val c = getCadenas(AppEstado.idioma)
            estado = if (ok) estado.copy(mensajeToast = c.modDesbloqueado, error = null)
                     else estado.copy(error = c.modErrorBloqueo, mensajeToast = null)
        }
    }

    override fun onCleared() {
        listenerBloqueos?.remove()
        super.onCleared()
    }

    private fun cargarPlan() {
        val profesional = AppEstado.tipoUsuarioCache == "Técnico" || AppEstado.tipoUsuarioCache == "Empresa"
        if (!profesional) return
        RepositorioTecnicos().obtenerMiPerfilTecnico { t ->
            estado = estado.copy(planVigente = t?.esVerificado == true)
        }
    }

    fun cargarAjustes() {
        escucharBloqueos()
        estado = estado.copy(cargando = true)
        repo.obtenerAjustes { remoto ->
            // Sin documento (o sin conexión) valen los del dispositivo; antes se
            // tomaban los de por defecto y la pantalla pisaba con ellos los locales.
            // El idioma y el tema los manda siempre el dispositivo (la pantalla los
            // muestra de AppEstado) y el servidor usa el idioma para los avisos.
            val a = (remoto ?: ajustesLocales()).copy(idioma = AppEstado.idioma, tema = AppEstado.tema)
            estado = estado.copy(ajustes = a, cargando = false)
            if (a != remoto) subir()
        }
    }

    private fun ajustesLocales() = AjustesUsuario(
        notificacionesPush = AppEstado.notificacionesPush,
        notificacionesSonido = AppEstado.notificacionesSonido,
        idioma = AppEstado.idioma,
        tema = AppEstado.tema,
        unidadEnergia = AppEstado.unidadEnergia,
        unidadMoneda = AppEstado.unidadMoneda
    )
    fun cambiarPush(v: Boolean) { estado = estado.copy(ajustes = estado.ajustes.copy(notificacionesPush = v), mensajeToast = null); subir() }
    fun cambiarEmail(v: Boolean) { estado = estado.copy(ajustes = estado.ajustes.copy(notificacionesEmail = v), mensajeToast = null); subir() }
    fun cambiarSonido(v: Boolean) { estado = estado.copy(ajustes = estado.ajustes.copy(notificacionesSonido = v), mensajeToast = null); subir() }
    fun cambiarNotifMensajes(v: Boolean) { estado = estado.copy(ajustes = estado.ajustes.copy(notificacionesMensajes = v), mensajeToast = null); subir() }
    fun cambiarNotifValoraciones(v: Boolean) { estado = estado.copy(ajustes = estado.ajustes.copy(notificacionesValoraciones = v), mensajeToast = null); subir() }
    fun cambiarIdioma(v: String) { estado = estado.copy(ajustes = estado.ajustes.copy(idioma = v), mensajeToast = null); subir() }
    fun cambiarTema(v: String) { estado = estado.copy(ajustes = estado.ajustes.copy(tema = v), mensajeToast = null); subir() }
    fun cambiarUnidadEnergia(v: String) { estado = estado.copy(ajustes = estado.ajustes.copy(unidadEnergia = v), mensajeToast = null); subir() }
    fun cambiarUnidadMoneda(v: String) { estado = estado.copy(ajustes = estado.ajustes.copy(unidadMoneda = v), mensajeToast = null); subir() }

    // Cada cambio se sube en el momento: la pantalla ya lo aplica en local y, si
    // solo se subiera con «Guardar», al volver a entrar el valor del servidor lo
    // pisaría. Mientras se cargan no se sube nada (se mandarían los valores por
    // defecto). Si falla, el botón «Guardar» permite reintentarlo.
    private fun subir() { if (!estado.cargando) repo.guardarAjustes(estado.ajustes) { } }

    fun guardar() {
        estado = estado.copy(guardando = true, error = null, mensajeToast = null)
        repo.guardarAjustes(estado.ajustes) { r ->
            r.onSuccess { estado = estado.copy(guardando = false, mensajeToast = getCadenas(AppEstado.idioma).ajustesGuardados) }
             .onFailure { estado = estado.copy(guardando = false, error = getCadenas(AppEstado.idioma).authErrGenerico) }
        }
    }

    fun getEmail(): String = repoAuth.getEmail() ?: ""

    fun cambiarPassword(actual: String, nueva: String, onExito: (String) -> Unit) {
        estado = estado.copy(cambiandoPassword = true, error = null, mensajeToast = null)
        repoAuth.cambiarPassword(actual, nueva) { r ->
            r.onSuccess {
                estado = estado.copy(cambiandoPassword = false)
                onExito("password_ok")
            }.onFailure {
                estado = estado.copy(cambiandoPassword = false, error = it.message)
            }
        }
    }

    /** Si queda una suscripción por cancelar en Play se activa `avisoCancelarEnPlay`; si no, [onExito]. */
    fun eliminarCuenta(onExito: () -> Unit) {
        estado = estado.copy(eliminandoCuenta = true, error = null, mensajeToast = null)
        repoAuth.eliminarMiCuenta { r ->
            r.onSuccess { cancelarEnPlay ->
                estado = estado.copy(eliminandoCuenta = false, avisoCancelarEnPlay = cancelarEnPlay)
                if (!cancelarEnPlay) onExito()
            }.onFailure {
                estado = estado.copy(eliminandoCuenta = false, error = it.message)
            }
        }
    }

    fun descartarAvisoCancelarEnPlay() { estado = estado.copy(avisoCancelarEnPlay = false) }

    fun mostrarMensaje(msg: String) { estado = estado.copy(mensajeToast = msg) }
    fun mostrarError(msg: String) { estado = estado.copy(error = msg) }
    fun limpiarMensaje() { estado = estado.copy(mensajeToast = null, error = null) }
}
