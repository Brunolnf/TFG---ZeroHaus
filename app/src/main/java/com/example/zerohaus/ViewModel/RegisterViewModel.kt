package com.example.zerohaus.viewmodel

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Estados.RegistroEstado

class RegistroViewModel : ViewModel() {

    var estado by mutableStateOf(RegistroEstado())
        private set


    fun cambiarNombre(nombre: String) {

        estado = estado.copy(
            nombre = nombre
        )

        validarFormulario()
    }


    fun cambiarEmail(email: String) {

        estado = estado.copy(
            email = email
        )

        validarFormulario()
    }


    fun cambiarTipoUsuario(tipo: String) {

        estado = estado.copy(
            tipoUsuario = tipo
        )
    }


    fun cambiarContrasena(contrasena: String) {

        estado = estado.copy(
            contrasena = contrasena
        )

        validarFormulario()
    }


    fun cambiarConfirmarContrasena(confirmar: String) {

        estado = estado.copy(
            confirmarContrasena = confirmar
        )

        validarFormulario()
    }


    private fun validarFormulario() {

        val emailValido =
            Patterns.EMAIL_ADDRESS.matcher(estado.email).matches()

        val contrasenaValida =
            estado.contrasena.length >= 8

        val contrasenaNumeroLetra =
            estado.contrasena.any { it.isDigit() } &&
                    estado.contrasena.any { it.isLetter() }

        val formularioValido =
            emailValido &&
                    contrasenaValida &&
                    contrasenaNumeroLetra &&
                    estado.contrasena == estado.confirmarContrasena &&
                    estado.nombre.isNotEmpty()

        estado = estado.copy(
            emailValido = emailValido,
            contrasenaValida = contrasenaValida,
            contrasenaNumeroLetra = contrasenaNumeroLetra,
            formularioValido = formularioValido
        )
    }


    fun crearCuenta() {

        // Aquí en el futuro irá la llamada a la API


        if (estado.formularioValido) {

            estado = estado.copy(
                registroCorrecto = true
            )

        } else {

            estado = estado.copy(
                error = "Datos incorrectos"
            )
        }
    }


    fun limpiarError() {

        estado = estado.copy(
            error = null
        )
    }

}