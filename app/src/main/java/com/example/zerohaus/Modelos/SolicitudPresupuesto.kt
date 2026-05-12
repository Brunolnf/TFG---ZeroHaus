package com.example.zerohaus.Modelos

/**
 * Estados del ciclo de vida de una solicitud:
 *   "Pendiente"           → cliente la envió, técnico aún no responde
 *   "Presupuestado"       → técnico envió precio, cliente aún no decide
 *   "Aceptado"            → cliente aceptó el presupuesto, falta la ficha de inicio
 *   "FichaEnviada"        → técnico envió la ficha de inicio, cliente debe aceptarla
 *   "EnCurso"             → cliente aceptó la ficha; existe un /proyectos asociado
 *   "PendientePago"       → técnico marcó el trabajo como terminado, falta cobrar
 *   "PagoEnVerificacion"  → cliente dice haber pagado por PayPal/Bizum; técnico debe confirmar
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

    // ── Ficha de inicio (la envía el técnico tras "Aceptado") ──
    val fichaFechaInicio: Long = 0L,
    val fichaFechaFinEstimada: Long = 0L,
    val fichaDescripcion: String = "",
    val fichaPrecioFinal: Double = 0.0,
    val fichaTareas: List<String> = emptyList(),

    // ── Vínculo con el proyecto creado tras aceptar la ficha ──
    val proyectoId: String = "",

    // ── Pago ──
    val pagado: Boolean = false,
    val fechaPago: Long = 0L,
    val metodoPago: String = "",         // "paypal" | "bizum" | "otro"
    val referenciaPago: String = "",     // referencia/concepto opcional indicado por el cliente
    val fechaPagoCliente: Long = 0L      // cuando el cliente marca "ya he pagado"
)
