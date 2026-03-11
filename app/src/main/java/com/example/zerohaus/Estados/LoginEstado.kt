package com.example.zerohaus.Estados

data class LoginEstado(

    val email: String = "",
    val contrasena: String = "",

    val emailValido: Boolean = false,
    val passwordValida: Boolean = false,

    val formularioValido: Boolean = false,

    val cargando: Boolean = false,

    val error: String? = null,

    val loginCorrecto: Boolean = false
)