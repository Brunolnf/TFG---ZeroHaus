package com.example.zerohaus.Estados

data class RegistroEstado(
    val nombre: String = "",
    val email: String = "",
    val tipoUsuario: String = "Propietario",
    val contrasena: String = "",
    val confirmarContrasena: String = "",
    val emailValido: Boolean = false,
    val contrasenaValida: Boolean = false,
    val contrasenaNumeroLetra: Boolean = false,
    val formularioValido: Boolean = false,
    val cargando: Boolean = false,
    val error: String? = null,
    val registroCorrecto: Boolean = false
)
