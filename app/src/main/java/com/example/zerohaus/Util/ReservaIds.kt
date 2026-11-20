package com.example.zerohaus.Util

/**
 * Ids de la vivienda y del informe reservados con `document()` antes de guardar.
 * Se conservan en el estado del ViewModel: si el guardado falla, el reintento
 * escribe los MISMOS documentos en vez de crear otra vivienda.
 */
data class ReservaIds(val viviendaId: String, val informeId: String)

/**
 * Devuelve la reserva a usar: la ya existente si la hay; si no, ids nuevos.
 * [viviendaExistenteId] (vivienda elegida del selector) manda sobre cualquier id reservado.
 */
fun reservarIds(
    actual: ReservaIds?,
    viviendaExistenteId: String?,
    nuevoIdVivienda: () -> String,
    nuevoIdInforme: () -> String
): ReservaIds = ReservaIds(
    viviendaId = viviendaExistenteId?.takeIf { it.isNotEmpty() } ?: actual?.viviendaId ?: nuevoIdVivienda(),
    informeId = actual?.informeId ?: nuevoIdInforme()
)
