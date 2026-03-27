package com.example.zerohaus.Modelos

data class Proyecto(
    val id: String = "",
    val uid: String = "",
    val titulo: String = "",
    val viviendaNombre: String = "",
    val tecnicoId: String = "",
    val tecnicoNombre: String = "",
    val progreso: Int = 0,
    val estado: String = "Pendiente", // Pendiente, En curso, Finalizado
    val tareas: List<Tarea> = emptyList(),
    val fechaCreacion: Long = System.currentTimeMillis()
)

data class Tarea(
    val nombre: String = "",
    val completada: Boolean = false
)
