package com.example.zerohaus.Modelos

data class Usuario(
    val id: String,
    val nombre: String,
    val email: String,
    val tipoUsuario: String // Propietario o Técnico
)