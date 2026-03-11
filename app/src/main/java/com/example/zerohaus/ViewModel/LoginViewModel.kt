package com.example.zerohaus.ViewModel

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Estados.LoginEstado

class LoginViewModel : ViewModel() {

    // Estado de la pantalla que la UI observará
    var estado by mutableStateOf(LoginEstado())
        private set


    // Cambia el email cuando el usuario escribe
    fun cambiarEmail(nuevoEmail: String) {

        estado = estado.copy(
            email = nuevoEmail
        )

        validarFormulario()
    }


    // Cambia la contraseña cuando el usuario escribe
    fun cambiarContrasena(nuevaContrasena: String) {

        estado = estado.copy(
            contrasena = nuevaContrasena
        )

        validarFormulario()
    }


    // Valida el formulario completo
    private fun validarFormulario() {

        val emailValido =
            Patterns.EMAIL_ADDRESS.matcher(estado.email).matches()

        val passwordValida =
            estado.contrasena.length >= 8 &&
                    estado.contrasena.any { it.isDigit() } &&
                    estado.contrasena.any { it.isLetter() }

        val formularioValido =
            emailValido && passwordValida

        estado = estado.copy(
            emailValido = emailValido,
            passwordValida = passwordValida,
            formularioValido = formularioValido
        )
    }


    // Acción al pulsar iniciar sesión
    fun iniciarSesion() {

        // Aquí en el futuro irá la llamada a la API o BBDD


        if (estado.formularioValido) {

            estado = estado.copy(
                loginCorrecto = true
            )
        } else {

            estado = estado.copy(
                error = "Datos incorrectos"
            )
        }
    }


    // Limpia el estado de error
    fun limpiarError() {

        estado = estado.copy(
            error = null
        )
    }
}