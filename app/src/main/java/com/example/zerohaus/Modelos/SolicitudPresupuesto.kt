package com.example.zerohaus.Modelos

data class SolicitudPresupuesto(
    val id: String = "",
    val uidCliente: String = "",
    val nombreCliente: String = "",
    val tecnicoId: String = "",
    val tecnicoNombre: String = "",
    val viviendaId: String = "",
    val descripcion: String = "",
    val estado: String = "Pendiente",
    val precioPresupuesto: Double = 0.0,   // ← ¿tienes este?
    val respuestaTecnico: String = "",      // ← ¿y este?
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaRespuesta: Long = 0            // ← ¿y este?
)