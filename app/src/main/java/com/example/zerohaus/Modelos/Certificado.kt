package com.example.zerohaus.Modelos

data class Certificado(
    val id: String = "",
    val uid: String = "",
    val nombre: String = "",
    val tipo: String = "",
    val urlArchivo: String = "",
    val fechaSubida: Long = System.currentTimeMillis()
)
