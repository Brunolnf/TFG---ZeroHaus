package com.example.zerohaus.Modelos

/**
 * Estados del ciclo de vida de una solicitud:
 *   "Pendiente"           → cliente la envió, técnico aún no responde
 *   "Presupuestado"       → técnico envió precio, cliente aún no decide
 *   "Aceptado"            → cliente aceptó el presupuesto, falta la ficha de inicio
 *   "FichaEnviada"        → técnico envió la ficha de inicio, cliente debe aceptarla
 *   "FichaRechazada"      → cliente rechazó la ficha, técnico debe reenviarla
 *   "EnCurso"             → cliente aceptó la ficha; existe un /proyectos asociado
 *   "PendientePago"       → técnico marcó el trabajo como terminado, falta cobrar
 *   "PagoEnVerificacion"  → cliente dice haber pagado; técnico debe confirmar
 *   "Completado"          → técnico confirmó el cobro; reforma cerrada (puede valorar)
 *   "Rechazado"           → cliente rechazó el presupuesto (fin)
 */
data class SolicitudPresupuesto(
    val id: String = "",
    val uidCliente: String = "",
    val nombreCliente: String = "",
    val tecnicoId: String = "",       // ID del documento en /tecnicos (Firestore doc ID)
    val tecnicoUid: String = "",      // Auth UID del técnico (para notificaciones)
    val tecnicoNombre: String = "",
    val viviendaId: String = "",
    val descripcion: String = "",
    val estado: String = "Pendiente",
    val precioPresupuesto: Double = 0.0,
    val respuestaTecnico: String = "",
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaRespuesta: Long = 0L,

    val fichaFechaInicio: Long = 0L,
    val fichaFechaFinEstimada: Long = 0L,
    val fichaDescripcion: String = "",
    val fichaPrecioFinal: Double = 0.0,
    val fichaTareas: List<String> = emptyList(),

    val proyectoId: String = "",

    val pagado: Boolean = false,
    val fechaPago: Long = 0L,
    val metodoPago: String = "",         // "tarjeta" | "efectivo"
    val referenciaPago: String = "",     // referencia/concepto opcional indicado por el cliente
    val fechaPagoCliente: Long = 0L      // cuando el cliente marca "ya he pagado"
)

/**
 * Una solicitud está expirada si lleva más de 30 días en estado "Pendiente"
 * sin que el técnico haya respondido.
 */
val SolicitudPresupuesto.estaExpirada: Boolean
    get() = estado == "Pendiente" &&
            System.currentTimeMillis() - fechaCreacion > 30L * 24 * 60 * 60 * 1000
