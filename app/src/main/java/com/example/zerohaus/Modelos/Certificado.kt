package com.example.zerohaus.Modelos

data class Certificado(
    val id: String = "",
    val uid: String = "",
    val nombre: String = "",
    val tipo: String = "",
    val urlArchivo: String = "",               // primer archivo (retrocompat. admin)
    val urlsArchivos: List<String> = emptyList(), // todos los archivos (1-6)
    val fechaSubida: Long = System.currentTimeMillis(),
    val verificado: Boolean = false,
    val rechazado: Boolean = false,
    val motivoRechazo: String = "",
    val fechaVerificacion: Long = 0L,
    val tecnicoNombre: String = ""
) {
    /** Número real de archivos adjuntos. */
    val numArchivos: Int get() = urlsArchivos.size.coerceAtLeast(if (urlArchivo.isNotBlank()) 1 else 0)
}
