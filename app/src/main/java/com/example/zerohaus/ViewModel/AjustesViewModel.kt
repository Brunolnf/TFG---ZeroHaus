
package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.AjustesUsuario
import com.example.zerohaus.Repositorios.RepositorioAjustes

data class AjustesEstado(val ajustes: AjustesUsuario = AjustesUsuario(), val cargando: Boolean = false, val guardando: Boolean = false, val mensajeToast: String? = null, val error: String? = null)

class AjustesViewModel : ViewModel() {
    var estado by mutableStateOf(AjustesEstado())
        private set
    private val repo = RepositorioAjustes()

    init { cargarAjustes() }

    fun cargarAjustes() { estado = estado.copy(cargando = true); repo.obtenerAjustes { a -> estado = estado.copy(ajustes = a, cargando = false) } }
    fun cambiarPush(v: Boolean) { estado = estado.copy(ajustes = estado.ajustes.copy(notificacionesPush = v), mensajeToast = null) }
    fun cambiarEmail(v: Boolean) { estado = estado.copy(ajustes = estado.ajustes.copy(notificacionesEmail = v), mensajeToast = null) }
    fun cambiarSonido(v: Boolean) { estado = estado.copy(ajustes = estado.ajustes.copy(notificacionesSonido = v), mensajeToast = null) }
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
    fun limpiarMensaje() { estado = estado.copy(mensajeToast = null, error = null) }
}
