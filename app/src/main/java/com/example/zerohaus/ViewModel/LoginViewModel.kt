package com.example.zerohaus.ViewModel

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Estados.LoginEstado
import com.example.zerohaus.Repositorios.RepositorioAutenticacion

class LoginViewModel : ViewModel() {

    var estado by mutableStateOf(LoginEstado())
        private set

    private val repo = RepositorioAutenticacion()

    fun cambiarEmail(nuevoEmail: String) {
        estado = estado.copy(email = nuevoEmail)
        validarFormulario()
    }

    fun cambiarContrasena(nuevaContrasena: String) {
        estado = estado.copy(contrasena = nuevaContrasena)
        validarFormulario()
    }

    private fun validarFormulario() {
        val emailValido = Patterns.EMAIL_ADDRESS.matcher(estado.email).matches()
        val passwordValida = estado.contrasena.length >= 8 &&
                estado.contrasena.any { it.isDigit() } &&
                estado.contrasena.any { it.isLetter() }
        estado = estado.copy(
            emailValido = emailValido,
            passwordValida = passwordValida,
            formularioValido = emailValido && passwordValida
        )
    }

    fun iniciarSesion() {
        if (!estado.formularioValido) {
            estado = estado.copy(error = "Datos incorrectos")
            return
        }
        estado = estado.copy(cargando = true, error = null)
        repo.login(estado.email, estado.contrasena) { result ->
            result
                .onSuccess { estado = estado.copy(loginCorrecto = true, cargando = false) }
                .onFailure { estado = estado.copy(error = it.message, cargando = false) }
        }
    }

    fun recuperarContrasena(email: String, callback: (Result<Unit>) -> Unit) {
        repo.recuperarPassword(email, callback)
    }

    fun limpiarError() {
        estado = estado.copy(error = null)
    }
}