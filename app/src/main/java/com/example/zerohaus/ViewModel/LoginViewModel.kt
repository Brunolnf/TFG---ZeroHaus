package com.zerohaus.app.ui.login

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


class LoginViewModel : ViewModel() {

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    private val _loginCorrecto = MutableStateFlow(false)
    val loginCorrecto: StateFlow<Boolean> = _loginCorrecto

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun onEmailChange(valor: String) {
        _email.value = valor
    }

    fun onPasswordChange(valor: String) {
        _password.value = valor
    }


    fun limpiarEstado() {
        _loginCorrecto.value = false
        _error.value = null
    }
}
