package com.example.zerohaus.ViewModel

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Estados.RegistroEstado
import com.example.zerohaus.Repositorios.RepositorioAutenticacion

class RegistroViewModel : ViewModel() {

    var estado by mutableStateOf(RegistroEstado())
        private set

    private val repo = RepositorioAutenticacion()

    fun cambiarNombre(nombre: String) {
        estado = estado.copy(nombre = nombre)
        validarFormulario()
    }

    fun cambiarEmail(email: String) {
        estado = estado.copy(email = email)
        validarFormulario()
    }

    fun cambiarTipoUsuario(tipo: String) {
        estado = estado.copy(tipoUsuario = tipo)
    }

    fun cambiarContrasena(contrasena: String) {
        estado = estado.copy(contrasena = contrasena)
        validarFormulario()
    }

    fun cambiarConfirmarContrasena(confirmar: String) {
        estado = estado.copy(confirmarContrasena = confirmar)
        validarFormulario()
    }

    private fun validarFormulario() {
        val emailValido = Patterns.EMAIL_ADDRESS.matcher(estado.email).matches()
        val contrasenaValida = estado.contrasena.length >= 8
        val contrasenaNumeroLetra = estado.contrasena.any { it.isDigit() } &&
                estado.contrasena.any { it.isLetter() }
        val formularioValido = emailValido && contrasenaValida && contrasenaNumeroLetra &&
                estado.contrasena == estado.confirmarContrasena && estado.nombre.isNotEmpty()
        estado = estado.copy(
            emailValido = emailValido,
            contrasenaValida = contrasenaValida,
            contrasenaNumeroLetra = contrasenaNumeroLetra,
            formularioValido = formularioValido
        )
    }

    fun crearCuenta() {
        if (!estado.formularioValido) {
            estado = estado.copy(error = "Datos incorrectos")
            return
        }
        estado = estado.copy(cargando = true, error = null)
        repo.registro(estado.nombre, estado.email, estado.contrasena, estado.tipoUsuario) { result ->
            result
                .onSuccess { estado = estado.copy(registroCorrecto = true, cargando = false) }
                .onFailure { estado = estado.copy(error = it.message, cargando = false) }
        }
    }
}
