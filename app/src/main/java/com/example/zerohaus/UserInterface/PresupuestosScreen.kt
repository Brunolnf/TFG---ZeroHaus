package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Modelos.SolicitudPresupuesto
import com.example.zerohaus.ViewModel.PresupuestosViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresupuestosScreen(
    viewModel: PresupuestosViewModel,
    onVolver: () -> Unit = {}
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val fondo = MaterialTheme.colorScheme.background
    val estado = viewModel.estado
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    var tabSeleccionado by remember { mutableIntStateOf(0) }
    val tabs = listOf("Enviadas", "Recibidas")

    var solicitudResponder by remember { mutableStateOf<SolicitudPresupuesto?>(null) }
    var solicitudCompletar by remember { mutableStateOf<SolicitudPresupuesto?>(null) }

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
                title = {
                    Column {
                        Text("Presupuestos", fontWeight = FontWeight.SemiBold)
                        Text("Gestiona tus solicitudes", color = Color(0xFF6B7280), fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                }
            )
        }
    ) { pv ->
        Column(Modifier.padding(pv).fillMaxSize()) {
            TabRow(selectedTabIndex = tabSeleccionado, containerColor = Color.White, contentColor = verde) {
                tabs.forEachIndexed { i, titulo ->
                    val count = if (i == 0) estado.enviadas.size else estado.recibidas.size
                    Tab(
                        selected = tabSeleccionado == i,
                        onClick = { tabSeleccionado = i },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(titulo)
                                if (count > 0) {
                                    Badge { Text("$count") }
                                }
                            }
                        }
                    )
                }
            }

            if (estado.cargando) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = verde)
                }
            } else {
                val lista = if (tabSeleccionado == 0) estado.enviadas else estado.recibidas
                if (lista.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (tabSeleccionado == 0) Icons.Default.Send else Icons.Default.Inbox,
                                null, tint = gris.copy(alpha = 0.4f), modifier = Modifier.size(52.dp)
                            )
                            Text(
                                if (tabSeleccionado == 0) "No has enviado solicitudes aún"
                                else "No tienes solicitudes recibidas",
                                color = gris, fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (tabSeleccionado == 0) "Busca técnicos y solicita un presupuesto"
                                else "Aquí aparecerán las solicitudes de clientes",
                                color = gris.copy(alpha = 0.7f), fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(lista) { s ->
                            if (tabSeleccionado == 0) {
                                TarjetaEnviada(s, sdf, verde, gris,
                                    onAceptar = { viewModel.aceptarPresupuesto(s.id) },
                                    onRechazar = { viewModel.rechazarPresupuesto(s.id) },
                                    onCompletar = { solicitudCompletar = s }
                                )
                            } else {
                                TarjetaRecibida(s, sdf, verde, gris,
                                    onResponder = { solicitudResponder = s }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    solicitudResponder?.let { s ->
        ResponderDialog(
            solicitud = s,
            respondiendo = estado.respondiendo,
            onDismiss = { solicitudResponder = null },
            onEnviar = { precio, respuesta ->
                viewModel.responderPresupuesto(s.id, precio, respuesta)
                solicitudResponder = null
            }
        )
    }

    solicitudCompletar?.let { s ->
        AlertDialog(
            onDismissRequest = { solicitudCompletar = null },
            title = { Text("Marcar como completada", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¿Confirmas que la reforma con ${s.tecnicoNombre} ha finalizado correctamente?")
                    Text("Tras marcarlo, podrás escribir una valoración sobre el técnico.", fontSize = 13.sp, color = Color(0xFF6B7280))
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.completarSolicitud(s.id); solicitudCompletar = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) { Text("Confirmar", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { solicitudCompletar = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun TarjetaEnviada(
    s: SolicitudPresupuesto,
    sdf: SimpleDateFormat,
    verde: Color,
    gris: Color,
    onAceptar: () -> Unit,
    onRechazar: () -> Unit,
    onCompletar: () -> Unit
) {
    val borde = MaterialTheme.colorScheme.outline
    val colorEstado = when (s.estado) {
        "Pendiente" -> Color(0xFFF59E0B)
        "Presupuestado" -> Color(0xFF2563EB)
        "Aceptado" -> verde
        "Completado" -> Color(0xFF059669)
        "Rechazado" -> Color(0xFFDC2626)
        else -> gris
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, borde),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(s.tecnicoNombre, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = {},
                    label = { Text(s.estado, fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = colorEstado.copy(0.12f), labelColor = colorEstado)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("Enviado: ${sdf.format(Date(s.fechaCreacion))}", color = gris, fontSize = 12.sp)
            if (s.descripcion.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(s.descripcion, color = Color(0xFF374151), fontSize = 13.sp)
            }
            if (s.estado == "Presupuestado" || s.estado == "Aceptado") {
                Spacer(Modifier.height(10.dp))
                Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Respuesta del técnico", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF1E40AF))
                        Spacer(Modifier.height(4.dp))
                        Text("${String.format("%.2f", s.precioPresupuesto)} €", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (s.respuestaTecnico.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(s.respuestaTecnico, color = Color(0xFF374151), fontSize = 13.sp)
                        }
                    }
                }
            }
            if (s.estado == "Presupuestado") {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onRechazar,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFDC2626)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                    ) { Text("Rechazar", fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = onAceptar,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = verde)
                    ) { Text("Aceptar", color = Color.White, fontWeight = FontWeight.SemiBold) }
                }
            }
            if (s.estado == "Aceptado") {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onCompletar,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    Text("Marcar como completada", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TarjetaRecibida(
    s: SolicitudPresupuesto,
    sdf: SimpleDateFormat,
    verde: Color,
    gris: Color,
    onResponder: () -> Unit
) {
    val borde = MaterialTheme.colorScheme.outline
    val colorEstado = when (s.estado) {
        "Pendiente" -> Color(0xFFF59E0B)
        "Presupuestado" -> Color(0xFF2563EB)
        "Aceptado" -> verde
        "Completado" -> Color(0xFF059669)
        "Rechazado" -> Color(0xFFDC2626)
        else -> gris
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, borde),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.nombreCliente, fontWeight = FontWeight.SemiBold)
                    Text("Recibido: ${sdf.format(Date(s.fechaCreacion))}", color = gris, fontSize = 12.sp)
                }
                AssistChip(
                    onClick = {},
                    label = { Text(s.estado, fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = colorEstado.copy(0.12f), labelColor = colorEstado)
                )
            }
            if (s.descripcion.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(s.descripcion, color = Color(0xFF374151), fontSize = 13.sp)
            }
            if (s.estado == "Presupuestado" || s.estado == "Aceptado") {
                Spacer(Modifier.height(8.dp))
                Text("Tu respuesta: ${String.format("%.2f", s.precioPresupuesto)} €", color = verde, fontWeight = FontWeight.SemiBold)
                if (s.respuestaTecnico.isNotEmpty()) {
                    Text(s.respuestaTecnico, color = gris, fontSize = 13.sp)
                }
            }
            if (s.estado == "Pendiente") {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onResponder,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = verde)
                ) { Text("Responder con presupuesto", color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun ResponderDialog(
    solicitud: SolicitudPresupuesto,
    respondiendo: Boolean,
    onDismiss: () -> Unit,
    onEnviar: (Double, String) -> Unit
) {
    val verde = MaterialTheme.colorScheme.primary
    var precio by remember { mutableStateOf("") }
    var respuesta by remember { mutableStateOf("") }
    val precioValido = precio.toDoubleOrNull()?.let { it > 0 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enviar presupuesto", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cliente: ${solicitud.nombreCliente}", fontSize = 13.sp, color = Color(0xFF6B7280))
                if (solicitud.descripcion.isNotEmpty()) {
                    Text("Solicitud: ${solicitud.descripcion}", fontSize = 13.sp)
                }
                OutlinedTextField(
                    value = precio,
                    onValueChange = { if (it.matches(Regex("^\\d*\\.?\\d*$"))) precio = it },
                    label = { Text("Precio (€)") },
                    leadingIcon = { Icon(Icons.Default.Euro, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = verde, focusedLabelColor = verde),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = respuesta,
                    onValueChange = { respuesta = it },
                    label = { Text("Mensaje (opcional)") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = verde, focusedLabelColor = verde),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onEnviar(precio.toDouble(), respuesta.trim()) },
                enabled = precioValido && !respondiendo,
                colors = ButtonDefaults.buttonColors(containerColor = verde)
            ) {
                if (respondiendo) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Enviar", color = Color.White)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
