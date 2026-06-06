package com.example.zerohaus.Modelos

data class Certificado(
    val id: String = "",
    val uid: String = "",
    val nombre: String = "",
    val tipo: String = "",
    val urlArchivo: String = "",
    val fechaSubida: Long = System.currentTimeMillis(),
    // Campos de verificación
    val verificado: Boolean = false,
    val rechazado: Boolean = false,
    val motivoRechazo: String = "",
    val fechaVerificacion: Long = 0L,
    val tecnicoNombre: String = ""   // denormalizado para que admin lo vea sin join
)
