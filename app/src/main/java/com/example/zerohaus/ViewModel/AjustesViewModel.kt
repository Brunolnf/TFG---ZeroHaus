
package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.AjustesUsuario
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
    val mensajeToast: String? = null,
    val error: String? = null
)

/**
 * Carga y guarda los ajustes del usuario y gestiona la seguridad de la cuenta.
 */
class AjustesViewModel : ViewModel() {
    var estado by mutableStateOf(AjustesEstado())
        private set
    private val repo = RepositorioAjustes()
    private val repoAuth = RepositorioAutenticacion()

    init { cargarAjustes() }

    fun cargarAjustes() {
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

    fun eliminarCuenta(onExito: () -> Unit) {
        estado = estado.copy(eliminandoCuenta = true, error = null, mensajeToast = null)
        repoAuth.eliminarMiCuenta { r ->
            r.onSuccess {
                estado = estado.copy(eliminandoCuenta = false)
                onExito()
            }.onFailure {
                estado = estado.copy(eliminandoCuenta = false, error = it.message)
            }
        }
    }

    fun mostrarMensaje(msg: String) { estado = estado.copy(mensajeToast = msg) }
    fun mostrarError(msg: String) { estado = estado.copy(error = msg) }
    fun limpiarMensaje() { estado = estado.copy(mensajeToast = null, error = null) }
}
