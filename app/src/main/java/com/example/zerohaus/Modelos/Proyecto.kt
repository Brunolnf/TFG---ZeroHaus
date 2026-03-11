package com.example.zerohaus.Modelos

data class Proyecto(
    val id: String,
    val titulo: String,
    val descripcion: String,
    val estado: String, // Pendiente, En curso, Finalizado
    val tecnicoAsignado: String
)