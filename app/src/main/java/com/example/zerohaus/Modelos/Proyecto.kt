package com.example.zerohaus.Modelos

data class Proyecto(
    val id: String = "",
    val uid: String = "",                  // Auth UID del cliente propietario
    val titulo: String = "",
    val descripcion: String = "",
    val viviendaNombre: String = "",
    val tecnicoId: String = "",            // doc ID en /tecnicos
    val tecnicoUid: String = "",           // Auth UID del técnico (para queries)
    val tecnicoNombre: String = "",
    val progreso: Int = 0,
    val estado: String = "Pendiente",      // "Pendiente" / "En curso" / "Finalizado"
    val tareas: List<Tarea> = emptyList(),
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaFinEstimada: Long = 0L,
    val fechaInicio: Long = 0L,
    val precio: Double = 0.0,
    val solicitudId: String = "",          // vínculo con /solicitudes
    val pagado: Boolean = false
)

data class Tarea(
    val nombre: String = "",
    val completada: Boolean = false
)
