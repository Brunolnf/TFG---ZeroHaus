package com.example.zerohaus.ViewModel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RegistroViewModel : ViewModel() {

    private val _nombre = MutableStateFlow("")
    val nombre: StateFlow<String> = _nombre

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email

    private val _tipoUsuario = MutableStateFlow("Propietario")
    val tipoUsuario: StateFlow<String> = _tipoUsuario

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    private val _confirmarPassword = MutableStateFlow("")
    val confirmarPassword: StateFlow<String> = _confirmarPassword

    private val _registroCorrecto = MutableStateFlow(false)
    val registroCorrecto: StateFlow<Boolean> = _registroCorrecto

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun onNombreChange(valor: String) {
        _nombre.value = valor
    }

    fun onEmailChange(valor: String) {
        _email.value = valor
    }

    fun onTipoUsuarioChange(valor: String) {
        _tipoUsuario.value = valor
    }

    fun onPasswordChange(valor: String) {
        _password.value = valor
    }

    fun onConfirmarPasswordChange(valor: String) {
        _confirmarPassword.value = valor
    }

    fun limpiarEstado() {
        _registroCorrecto.value = false
        _error.value = null
    }
}
