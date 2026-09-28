
package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.AjustesUsuario
import com.example.zerohaus.Repositorios.RepositorioAjustes
import com.example.zerohaus.Repositorios.RepositorioAutenticacion

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

    fun cargarAjustes() { estado = estado.copy(cargando = true); repo.obtenerAjustes { a -> estado = estado.copy(ajustes = a, cargando = false) } }
    fun cambiarPush(v: Boolean) { estado = estado.copy(ajustes = estado.ajustes.copy(notificacionesPush = v), mensajeToast = null) }
    fun cambiarEmail(v: Boolean) { estado = estado.copy(ajustes = estado.ajustes.copy(notificacionesEmail = v), mensajeToast = null) }
    fun cambiarSonido(v: Boolean) { estado = estado.copy(ajustes = estado.ajustes.copy(notificacionesSonido = v), mensajeToast = null) }
    fun cambiarNotifMensajes(v: Boolean) { estado = estado.copy(ajustes = estado.ajustes.copy(notificacionesMensajes = v), mensajeToast = null) }
    fun cambiarNotifValoraciones(v: Boolean) { estado = estado.copy(ajustes = estado.ajustes.copy(notificacionesValoraciones = v), mensajeToast = null) }
    fun cambiarIdioma(v: String) { estado = estado.copy(ajustes = estado.ajustes.copy(idioma = v), mensajeToast = null) }
    fun cambiarTema(v: String) { estado = estado.copy(ajustes = estado.ajustes.copy(tema = v), mensajeToast = null) }
    fun cambiarUnidadEnergia(v: String) { estado = estado.copy(ajustes = estado.ajustes.copy(unidadEnergia = v), mensajeToast = null) }
    fun cambiarUnidadMoneda(v: String) { estado = estado.copy(ajustes = estado.ajustes.copy(unidadMoneda = v), mensajeToast = null) }

    fun guardar() {
        estado = estado.copy(guardando = true, error = null, mensajeToast = null)
        repo.guardarAjustes(estado.ajustes) { r ->
            r.onSuccess { estado = estado.copy(guardando = false, mensajeToast = "Ajustes guardados correctamente") }
             .onFailure { estado = estado.copy(guardando = false, error = it.message) }
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
