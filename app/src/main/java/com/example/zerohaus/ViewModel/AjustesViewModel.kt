
package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.AjustesUsuario
import com.example.zerohaus.Repositorios.RepositorioAjustes

data class AjustesEstado(val ajustes: AjustesUsuario = AjustesUsuario(), val cargando: Boolean = true, val guardando: Boolean = false, val exito: Boolean = false, val error: String? = null)

class AjustesViewModel : ViewModel() {
    var estado by mutableStateOf(AjustesEstado())
        private set
    private val repo = RepositorioAjustes()

    fun cargarAjustes() { estado = estado.copy(cargando = true); repo.obtenerAjustes { a -> estado = estado.copy(ajustes = a, cargando = false) } }
    fun cambiarPush(v: Boolean) { estado = estado.copy(ajustes = estado.ajustes.copy(notificacionesPush = v), exito = false) }
    fun cambiarEmail(v: Boolean) { estado = estado.copy(ajustes = estado.ajustes.copy(notificacionesEmail = v), exito = false) }
    fun cambiarSonido(v: Boolean) { estado = estado.copy(ajustes = estado.ajustes.copy(notificacionesSonido = v), exito = false) }
    fun cambiarIdioma(v: String) { estado = estado.copy(ajustes = estado.ajustes.copy(idioma = v), exito = false) }
    fun cambiarTema(v: String) { estado = estado.copy(ajustes = estado.ajustes.copy(tema = v), exito = false) }
    fun cambiarUnidadEnergia(v: String) { estado = estado.copy(ajustes = estado.ajustes.copy(unidadEnergia = v), exito = false) }
    fun cambiarUnidadMoneda(v: String) { estado = estado.copy(ajustes = estado.ajustes.copy(unidadMoneda = v), exito = false) }
    fun guardar() { estado = estado.copy(guardando = true, error = null, exito = false); repo.guardarAjustes(estado.ajustes) { r -> r.onSuccess { estado = estado.copy(guardando = false, exito = true) }.onFailure { estado = estado.copy(guardando = false, error = it.message) } } }
}
