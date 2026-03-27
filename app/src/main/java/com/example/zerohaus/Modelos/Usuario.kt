
package com.example.zerohaus.Modelos

data class Usuario(
    val uid: String = "",
    val nombre: String = "",
    val email: String = "",
    val tipoUsuario: String = "Propietario",
    val fotoPerfil: String = "",
    val tokenFCM: String = "",
    val fechaRegistro: Long = System.currentTimeMillis()
)