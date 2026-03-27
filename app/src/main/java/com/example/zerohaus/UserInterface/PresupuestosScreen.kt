package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.PresupuestosViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresupuestosScreen(
    viewModel: PresupuestosViewModel,
    onVolver: () -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)
    val estado = viewModel.estado
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(estado.mensaje, estado.error) {
        estado.mensaje?.let { snackbarHostState.showSnackbar(it); viewModel.limpiarMensaje() }
        estado.error?.let { snackbarHostState.showSnackbar(it); viewModel.limpiarMensaje() }
    }
    LaunchedEffect(Unit) { viewModel.cargarMisSolicitudes() }

    Scaffold(
        containerColor = fondo,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Column { Text("Mis presupuestos", fontWeight = FontWeight.SemiBold); Text("${estado.solicitudes.size} solicitudes", color = gris, fontSize = 12.sp) } },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }
            )
        }
    ) { pv ->
        if (estado.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = verde) }
        } else if (estado.solicitudes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pv), contentAlignment = Alignment.Center) { Text("No tienes solicitudes de presupuesto", color = gris) }
        } else {
            LazyColumn(modifier = Modifier.padding(pv).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(estado.solicitudes) { s ->
                    val colorEstado = when (s.estado) { "Pendiente" -> Color(0xFFF59E0B); "Presupuestado" -> Color(0xFF2563EB); "Aceptado" -> verde; "Rechazado" -> Color(0xFFDC2626); else -> gris }
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, borde), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(s.tecnicoNombre, fontWeight = FontWeight.SemiBold)
                                AssistChip(onClick = {}, label = { Text(s.estado, fontSize = 11.sp) }, colors = AssistChipDefaults.assistChipColors(containerColor = colorEstado.copy(0.12f), labelColor = colorEstado))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Enviado: ${sdf.format(Date(s.fechaCreacion))}", color = gris, fontSize = 12.sp)
                            if (s.descripcion.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text(s.descripcion, color = Color(0xFF374151), fontSize = 13.sp) }

                            // Respuesta del técnico
                            if (s.estado == "Presupuestado" || s.estado == "Aceptado") {
                                Spacer(Modifier.height(10.dp))
                                Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF))) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text("Respuesta del técnico", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF1E40AF))
                                        Spacer(Modifier.height(4.dp))
                                        Text("Precio: ${s.precioPresupuesto} €", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        if (s.respuestaTecnico.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text(s.respuestaTecnico, color = Color(0xFF374151), fontSize = 13.sp) }
                                    }
                                }
                            }

                            // Botones aceptar/rechazar
                            if (s.estado == "Presupuestado") {
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(onClick = { viewModel.rechazarPresupuesto(s.id) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFDC2626)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))) { Text("Rechazar", fontWeight = FontWeight.SemiBold) }
                                    Button(onClick = { viewModel.aceptarPresupuesto(s.id) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = verde)) { Text("Aceptar", color = Color.White, fontWeight = FontWeight.SemiBold) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}