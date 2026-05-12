package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import com.example.zerohaus.Modelos.SolicitudPresupuesto
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.ViewModel.PresupuestosViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresupuestosScreen(
    viewModel: PresupuestosViewModel,
    onVolver: () -> Unit = {},
    esTecnico: Boolean = false
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val fondo = MaterialTheme.colorScheme.background
    val estado = viewModel.estado
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    // Los técnicos solo ven solicitudes recibidas; los clientes ven ambas pestañas
    var tabSeleccionado by remember { mutableIntStateOf(if (esTecnico) 1 else 0) }
    val tabs = if (esTecnico) listOf("Solicitudes recibidas") else listOf("Enviadas", "Recibidas")

    var solicitudResponder by remember { mutableStateOf<SolicitudPresupuesto?>(null) }
    var solicitudCompletar by remember { mutableStateOf<SolicitudPresupuesto?>(null) }
    var solicitudFicha by remember { mutableStateOf<SolicitudPresupuesto?>(null) }     // técnico: enviar ficha
    var solicitudVerFicha by remember { mutableStateOf<SolicitudPresupuesto?>(null) }  // cliente: ver y aceptar ficha
    var solicitudPagar by remember { mutableStateOf<SolicitudPresupuesto?>(null) }     // cliente: pagar
    var mostrarRechazadas by remember { mutableStateOf(false) }                        // toggle archivadas

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
                        Text("Gestiona tus solicitudes", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                }
            )
        }
    ) { pv ->
        Column(Modifier.padding(pv).fillMaxSize()) {
            TabRow(selectedTabIndex = tabSeleccionado, containerColor = MaterialTheme.colorScheme.surface, contentColor = verde) {
                tabs.forEachIndexed { i, titulo ->
                    // Para técnicos hay solo 1 tab (recibidas); para clientes: 0=enviadas, 1=recibidas
                    val count = if (esTecnico || i == 1) estado.recibidas.size else estado.enviadas.size
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
                // Si es técnico, tabSeleccionado == 1 siempre (solo tiene "Solicitudes recibidas")
                val esRecibidas = esTecnico || tabSeleccionado == 1
                val lista = if (esRecibidas) estado.recibidas else estado.enviadas
                if (lista.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (esRecibidas) Icons.Default.Inbox else Icons.Default.Send,
                                null, tint = gris.copy(alpha = 0.4f), modifier = Modifier.size(52.dp)
                            )
                            Text(
                                if (esRecibidas) "No tienes solicitudes recibidas"
                                else "No has enviado solicitudes aún",
                                color = gris, fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (esRecibidas) "Aquí aparecerán las solicitudes de clientes"
                                else "Busca técnicos y solicita un presupuesto",
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
                            if (esRecibidas) {
                                TarjetaRecibida(s, sdf, verde, gris,
                                    onResponder = { solicitudResponder = s },
                                    onEnviarFicha = { solicitudFicha = s },
                                    onMarcarTerminado = { viewModel.marcarTrabajoTerminado(s.id) },
                                    onConfirmarCobro = { viewModel.confirmarCobro(s.id) }
                                )
                            } else {
                                TarjetaEnviada(s, sdf, verde, gris,
                                    onAceptar = { viewModel.aceptarPresupuesto(s.id) },
                                    onRechazar = { viewModel.rechazarPresupuesto(s.id) },
                                    onCompletar = { solicitudCompletar = s },
                                    onVerFicha = { solicitudVerFicha = s },
                                    onPagar = { solicitudPagar = s }
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
                    Text("Tras marcarlo, podrás escribir una valoración sobre el técnico.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    // Diálogo del técnico: enviar la ficha de inicio
    solicitudFicha?.let { s ->
        FichaInicioDialog(
            solicitud = s,
            enviando = estado.respondiendo,
            onDismiss = { solicitudFicha = null },
            onEnviar = { fechaIni, fechaFin, descripcion, precio, tareas ->
                viewModel.enviarFichaActividad(s.id, fechaIni, fechaFin, descripcion, precio, tareas)
                solicitudFicha = null
            }
        )
    }

    // Diálogo del cliente: revisar la ficha y aceptarla / rechazarla
    solicitudVerFicha?.let { s ->
        VerFichaDialog(
            solicitud = s,
            onDismiss = { solicitudVerFicha = null },
            onAceptar = {
                viewModel.aceptarFichaYCrearProyecto(s.id)
                solicitudVerFicha = null
            },
            onRechazar = { motivo ->
                viewModel.rechazarFicha(s.id, motivo)
                solicitudVerFicha = null
            }
        )
    }

    // Diálogo del cliente: pagar (PayPal / Bizum / Otro)
    solicitudPagar?.let { s ->
        PagarDialog(
            solicitud = s,
            tecnico = viewModel.tecnicoDe(s),
            pagando = estado.respondiendo,
            onDismiss = { solicitudPagar = null },
            onMarcarPagado = { metodo, referencia ->
                viewModel.marcarPagado(s.id, metodo, referencia)
                solicitudPagar = null
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
    onCompletar: () -> Unit,
    onVerFicha: () -> Unit,
    onPagar: () -> Unit
) {
    val borde = MaterialTheme.colorScheme.outline
    val colorEstado = colorEstado(s.estado, verde, gris)
    val etiquetaEstado = etiquetaEstado(s.estado)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borde),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(s.tecnicoNombre, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = {},
                    label = { Text(etiquetaEstado, fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = colorEstado.copy(0.12f), labelColor = colorEstado)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("Enviado: ${sdf.format(Date(s.fechaCreacion))}", color = gris, fontSize = 12.sp)
            if (s.descripcion.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(s.descripcion, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            }

            // Bloque de presupuesto recibido del técnico
            if (s.estado in listOf("Presupuestado", "Aceptado", "FichaEnviada", "EnCurso", "PendientePago", "PagoEnVerificacion", "Completado")) {
                Spacer(Modifier.height(10.dp))
                Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Presupuesto del técnico", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF1E40AF))
                        Spacer(Modifier.height(4.dp))
                        Text("${"%.2f".format(s.precioPresupuesto)} €", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (s.respuestaTecnico.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(s.respuestaTecnico, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Bloque de la ficha de inicio (cuando ya existe)
            if (s.estado in listOf("FichaEnviada", "EnCurso", "PendientePago", "PagoEnVerificacion", "Completado") && s.fichaPrecioFinal > 0) {
                Spacer(Modifier.height(10.dp))
                Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Ficha de inicio", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF6D28D9))
                        Spacer(Modifier.height(4.dp))
                        if (s.fichaFechaInicio > 0) {
                            Text("Inicio: ${sdf.format(Date(s.fichaFechaInicio))}", fontSize = 12.sp, color = gris)
                        }
                        if (s.fichaFechaFinEstimada > 0) {
                            Text("Fin estimado: ${sdf.format(Date(s.fichaFechaFinEstimada))}", fontSize = 12.sp, color = gris)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Importe final: ${"%.2f".format(s.fichaPrecioFinal)} €", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            // Acciones según estado
            when (s.estado) {
                "Presupuestado" -> {
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
                "Aceptado" -> {
                    Spacer(Modifier.height(12.dp))
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HourglassEmpty, null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Esperando que el técnico te envíe la ficha de inicio", fontSize = 12.sp, color = Color(0xFF92400E))
                        }
                    }
                }
                "FichaEnviada" -> {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onVerFicha,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Revisar y aceptar ficha", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                "EnCurso" -> {
                    Spacer(Modifier.height(12.dp))
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, null, tint = Color(0xFF059669), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Reforma en curso · míralo en \"Mis proyectos\"", fontSize = 12.sp, color = Color(0xFF065F46))
                        }
                    }
                }
                "PendientePago" -> {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onPagar,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Icon(Icons.Default.CreditCard, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pagar ${"%.0f".format(s.fichaPrecioFinal)} €", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                "PagoEnVerificacion" -> {
                    Spacer(Modifier.height(12.dp))
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HourglassEmpty, null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                val medio = when (s.metodoPago) {
                                    "paypal" -> "PayPal"
                                    "bizum" -> "Bizum"
                                    else -> "transferencia"
                                }
                                Text("Pago por $medio en verificación", fontSize = 12.sp, color = Color(0xFF92400E), fontWeight = FontWeight.SemiBold)
                                Text("Esperando que ${s.tecnicoNombre} confirme la recepción.", fontSize = 11.sp, color = Color(0xFF92400E))
                            }
                        }
                    }
                }
                "Completado" -> {
                    Spacer(Modifier.height(12.dp))
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF059669), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (s.pagado) "Reforma completada y pagada · ya puedes valorar"
                                else "Reforma completada · ya puedes valorar",
                                fontSize = 12.sp,
                                color = Color(0xFF065F46)
                            )
                        }
                    }
                    // Mantengo onCompletar por si hace falta para solicitudes legacy sin pago
                    if (!s.pagado) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onCompletar,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Marcar como completada (legacy)", fontSize = 12.sp) }
                    }
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
    onResponder: () -> Unit,
    onEnviarFicha: () -> Unit,
    onMarcarTerminado: () -> Unit,
    onConfirmarCobro: () -> Unit
) {
    val borde = MaterialTheme.colorScheme.outline
    val colorEstado = colorEstado(s.estado, verde, gris)
    val etiquetaEstado = etiquetaEstado(s.estado)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    label = { Text(etiquetaEstado, fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = colorEstado.copy(0.12f), labelColor = colorEstado)
                )
            }
            if (s.descripcion.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(s.descripcion, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            }

            // Presupuesto enviado
            if (s.estado in listOf("Presupuestado", "Aceptado", "FichaEnviada", "EnCurso", "PendientePago", "PagoEnVerificacion", "Completado")) {
                Spacer(Modifier.height(8.dp))
                Text("Tu presupuesto: ${"%.2f".format(s.precioPresupuesto)} €", color = verde, fontWeight = FontWeight.SemiBold)
                if (s.respuestaTecnico.isNotEmpty()) {
                    Text(s.respuestaTecnico, color = gris, fontSize = 13.sp)
                }
            }

            // Ficha (si la enviaste)
            if (s.estado in listOf("FichaEnviada", "EnCurso", "PendientePago", "PagoEnVerificacion", "Completado") && s.fichaPrecioFinal > 0) {
                Spacer(Modifier.height(6.dp))
                Text("Importe final acordado: ${"%.2f".format(s.fichaPrecioFinal)} €", fontSize = 13.sp, color = Color(0xFF6D28D9), fontWeight = FontWeight.SemiBold)
            }

            // Acciones según estado
            when (s.estado) {
                "Pendiente" -> {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onResponder,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = verde)
                    ) { Text("Responder con presupuesto", color = Color.White, fontWeight = FontWeight.SemiBold) }
                }
                "Aceptado" -> {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onEnviarFicha,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enviar ficha de inicio", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                "FichaEnviada" -> {
                    Spacer(Modifier.height(12.dp))
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HourglassEmpty, null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Esperando que el cliente confirme la ficha", fontSize = 12.sp, color = Color(0xFF92400E))
                        }
                    }
                }
                "EnCurso" -> {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onMarcarTerminado,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF059669)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF059669))
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Marcar trabajo terminado", fontWeight = FontWeight.SemiBold)
                    }
                }
                "PendientePago" -> {
                    Spacer(Modifier.height(12.dp))
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HourglassEmpty, null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Esperando el pago del cliente", fontSize = 12.sp, color = Color(0xFF92400E))
                        }
                    }
                }
                "PagoEnVerificacion" -> {
                    Spacer(Modifier.height(12.dp))
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE))) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = Color(0xFF0284C7), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                val medio = when (s.metodoPago) {
                                    "paypal" -> "PayPal"
                                    "bizum" -> "Bizum"
                                    else -> "transferencia / efectivo"
                                }
                                Text("${s.nombreCliente} dice haber pagado por $medio", fontSize = 12.sp, color = Color(0xFF0C4A6E), fontWeight = FontWeight.SemiBold)
                            }
                            if (s.referenciaPago.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text("Referencia: ${s.referenciaPago}", fontSize = 11.sp, color = Color(0xFF0C4A6E))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Verifica que has recibido los ${"%.2f".format(s.fichaPrecioFinal)} € antes de confirmar.",
                                fontSize = 11.sp, color = Color(0xFF0C4A6E)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onConfirmarCobro,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Confirmar cobro recibido", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                "Completado" -> {
                    Spacer(Modifier.height(12.dp))
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF059669), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Reforma completada y cobrada ✓", fontSize = 12.sp, color = Color(0xFF065F46), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// Helpers comunes para estados
@Composable
private fun colorEstado(estado: String, verde: Color, gris: Color): Color = when (estado) {
    "Pendiente" -> Color(0xFFF59E0B)
    "Presupuestado" -> Color(0xFF2563EB)
    "Aceptado" -> verde
    "FichaEnviada" -> Color(0xFF7C3AED)
    "EnCurso" -> Color(0xFF0EA5E9)
    "PendientePago" -> Color(0xFFD97706)
    "PagoEnVerificacion" -> Color(0xFF0284C7)
    "Completado" -> Color(0xFF059669)
    "Rechazado" -> Color(0xFFDC2626)
    else -> gris
}

private fun etiquetaEstado(estado: String): String = when (estado) {
    "FichaEnviada" -> "Ficha enviada"
    "EnCurso" -> "En curso"
    "PendientePago" -> "Pendiente pago"
    "PagoEnVerificacion" -> "Verificando pago"
    else -> estado
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
                Text("Cliente: ${solicitud.nombreCliente}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/* =================================================================== */
/* === DIÁLOGO TÉCNICO: Enviar ficha de inicio                       === */
/* =================================================================== */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FichaInicioDialog(
    solicitud: SolicitudPresupuesto,
    enviando: Boolean,
    onDismiss: () -> Unit,
    onEnviar: (fechaInicio: Long, fechaFin: Long, descripcion: String, precio: Double, tareas: List<String>) -> Unit
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val sdf = remember { java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    var fechaInicioTexto by remember { mutableStateOf("") }
    var fechaFinTexto by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var precio by remember { mutableStateOf(solicitud.precioPresupuesto.takeIf { it > 0 }?.let { "%.2f".format(it) } ?: "") }
    var tareas by remember { mutableStateOf(listOf<String>()) }
    var nuevaTarea by remember { mutableStateOf("") }
    var fechaError by remember { mutableStateOf(false) }

    val precioNum = precio.toDoubleOrNull()
    val precioOk = precioNum != null && precioNum > 0
    val descripcionOk = descripcion.trim().isNotEmpty()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ficha de inicio", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Cliente: ${solicitud.nombreCliente}", fontSize = 12.sp, color = gris)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                }
                HorizontalDivider()
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Detalles del trabajo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    OutlinedTextField(
                        value = descripcion,
                        onValueChange = { descripcion = it },
                        label = { Text("Descripción detallada *") },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = fechaInicioTexto,
                            onValueChange = { fechaInicioTexto = it; fechaError = false },
                            label = { Text("Inicio dd/MM/yyyy") },
                            singleLine = true,
                            isError = fechaError,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = fechaFinTexto,
                            onValueChange = { fechaFinTexto = it; fechaError = false },
                            label = { Text("Fin dd/MM/yyyy") },
                            singleLine = true,
                            isError = fechaError,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (fechaError) {
                        Text("Formato de fechas incorrecto. Usa dd/MM/yyyy", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    OutlinedTextField(
                        value = precio,
                        onValueChange = { if (it.matches(Regex("^\\d*\\.?\\d*$"))) precio = it },
                        label = { Text("Precio final acordado (€) *") },
                        leadingIcon = { Icon(Icons.Default.Euro, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Tareas / fases del trabajo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    tareas.forEachIndexed { i, t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckBoxOutlineBlank, null, tint = gris, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(t, modifier = Modifier.weight(1f), fontSize = 14.sp)
                            IconButton(
                                onClick = { tareas = tareas.toMutableList().also { it.removeAt(i) } },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, "Eliminar", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = nuevaTarea,
                            onValueChange = { nuevaTarea = it },
                            label = { Text("Añadir tarea") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (nuevaTarea.isNotBlank()) {
                                    tareas = tareas + nuevaTarea.trim()
                                    nuevaTarea = ""
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.AddCircle, null, tint = verde, modifier = Modifier.size(32.dp))
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Cancelar") }
                    Button(
                        onClick = {
                            var fIni = 0L
                            var fFin = 0L
                            try {
                                sdf.isLenient = false
                                if (fechaInicioTexto.isNotBlank()) fIni = sdf.parse(fechaInicioTexto)?.time ?: 0L
                                if (fechaFinTexto.isNotBlank()) fFin = sdf.parse(fechaFinTexto)?.time ?: 0L
                            } catch (e: Exception) {
                                fechaError = true; return@Button
                            }
                            onEnviar(fIni, fFin, descripcion.trim(), precioNum ?: 0.0, tareas)
                        },
                        enabled = !enviando && precioOk && descripcionOk,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (enviando) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Enviar ficha", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/* =================================================================== */
/* === DIÁLOGO CLIENTE: Ver ficha y aceptar / rechazar              === */
/* =================================================================== */
@Composable
private fun VerFichaDialog(
    solicitud: SolicitudPresupuesto,
    onDismiss: () -> Unit,
    onAceptar: () -> Unit,
    onRechazar: (String) -> Unit
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val sdf = remember { java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    var mostrarRechazar by remember { mutableStateOf(false) }
    var motivo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Ficha de inicio", fontWeight = FontWeight.SemiBold)
                Text("De ${solicitud.tecnicoNombre}", color = gris, fontSize = 12.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (solicitud.fichaDescripcion.isNotBlank()) {
                    Text("Descripción", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = gris)
                    Text(solicitud.fichaDescripcion, fontSize = 14.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (solicitud.fichaFechaInicio > 0) {
                        Column {
                            Text("Inicio", fontSize = 12.sp, color = gris, fontWeight = FontWeight.SemiBold)
                            Text(sdf.format(Date(solicitud.fichaFechaInicio)), fontSize = 13.sp)
                        }
                    }
                    if (solicitud.fichaFechaFinEstimada > 0) {
                        Column {
                            Text("Fin estimado", fontSize = 12.sp, color = gris, fontWeight = FontWeight.SemiBold)
                            Text(sdf.format(Date(solicitud.fichaFechaFinEstimada)), fontSize = 13.sp)
                        }
                    }
                }
                Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = verde.copy(0.1f))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Euro, null, tint = verde)
                        Spacer(Modifier.width(8.dp))
                        Text("Importe final: ${"%.2f".format(solicitud.fichaPrecioFinal)} €", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = verde)
                    }
                }
                if (solicitud.fichaTareas.isNotEmpty()) {
                    Text("Tareas (${solicitud.fichaTareas.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = gris)
                    solicitud.fichaTareas.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckBoxOutlineBlank, null, tint = gris, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t, fontSize = 13.sp)
                        }
                    }
                }
                if (mostrarRechazar) {
                    OutlinedTextField(
                        value = motivo,
                        onValueChange = { motivo = it },
                        label = { Text("Motivo (opcional)") },
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (mostrarRechazar) {
                Button(
                    onClick = { onRechazar(motivo.trim()) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Confirmar rechazo", color = Color.White) }
            } else {
                Button(
                    onClick = onAceptar,
                    colors = ButtonDefaults.buttonColors(containerColor = verde)
                ) { Text("Aceptar e iniciar", color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        },
        dismissButton = {
            if (mostrarRechazar) {
                OutlinedButton(onClick = { mostrarRechazar = false }) { Text("Atrás") }
            } else {
                OutlinedButton(
                    onClick = { mostrarRechazar = true },
                    border = BorderStroke(1.dp, Color(0xFFDC2626)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                ) { Text("Rechazar") }
            }
        }
    )
}

/* =================================================================== */
/* === DIÁLOGO CLIENTE: Pagar con PayPal o Bizum                     === */
/* =================================================================== */
@Composable
private fun PagarDialog(
    solicitud: SolicitudPresupuesto,
    tecnico: Tecnico?,
    pagando: Boolean,
    onDismiss: () -> Unit,
    onMarcarPagado: (metodo: String, referencia: String) -> Unit
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val context = LocalContext.current

    val paypal = tecnico?.paypalUsername?.trim().orEmpty()
    val bizum = tecnico?.bizumTelefono?.trim().orEmpty()
    val hayMetodos = paypal.isNotEmpty() || bizum.isNotEmpty()

    var metodoElegido by remember { mutableStateOf<String?>(null) }   // "paypal" / "bizum" / "otro"
    var pagoIniciado by remember { mutableStateOf(false) }            // true tras abrir la URL
    var referencia by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Pagar reforma", fontWeight = FontWeight.SemiBold)
                Text("a ${solicitud.tecnicoNombre}", fontSize = 12.sp, color = gris)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Cabecera con el importe
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = verde)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Importe a pagar", color = Color.White.copy(0.85f), fontSize = 13.sp)
                        Spacer(Modifier.height(2.dp))
                        Text("${"%.2f".format(solicitud.fichaPrecioFinal)} €", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                    }
                }

                if (!hayMetodos) {
                    // El técnico no ha configurado métodos de pago
                    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${solicitud.tecnicoNombre} aún no ha configurado métodos de cobro. Contáctale por chat para acordar el pago manualmente.",
                                fontSize = 12.sp, color = Color(0xFF991B1B)
                            )
                        }
                    }
                    Text(
                        "Si ya os habéis puesto de acuerdo (transferencia, efectivo, etc.) puedes marcar el pago como realizado manualmente:",
                        fontSize = 12.sp, color = gris
                    )
                    OutlinedTextField(
                        value = referencia,
                        onValueChange = { referencia = it },
                        label = { Text("Referencia / concepto (opcional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onMarcarPagado("otro", referencia.trim()) },
                        enabled = !pagando,
                        colors = ButtonDefaults.buttonColors(containerColor = verde),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Marcar como pagado", color = Color.White, fontWeight = FontWeight.SemiBold) }
                } else if (metodoElegido == null) {
                    Text("¿Cómo quieres pagar?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                    if (paypal.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { metodoElegido = "paypal" },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF003087)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF003087))
                        ) {
                            Icon(Icons.Default.AccountBalance, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("PayPal", fontWeight = FontWeight.SemiBold)
                                Text("paypal.me/$paypal", fontSize = 11.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp))
                        }
                    }

                    if (bizum.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { metodoElegido = "bizum" },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF00B8D4)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00B8D4))
                        ) {
                            Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Bizum", fontWeight = FontWeight.SemiBold)
                                Text(bizum, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    // Vista del método elegido — instrucciones + botón abrir + confirmación
                    val esPayPal = metodoElegido == "paypal"
                    val titulo = if (esPayPal) "Pagar con PayPal" else "Pagar con Bizum"
                    val color = if (esPayPal) Color(0xFF003087) else Color(0xFF00B8D4)

                    Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = color)

                    if (esPayPal) {
                        Text("Pulsa el botón para abrir PayPal con el importe pre-rellenado. Cuando completes el pago, vuelve aquí y confirma.", fontSize = 12.sp, color = gris)
                    } else {
                        Text("Pulsa el botón para abrir tu app del banco con un Bizum a $bizum por ${"%.2f".format(solicitud.fichaPrecioFinal)} €. Cuando completes el pago, vuelve aquí y confirma.", fontSize = 12.sp, color = gris)
                    }

                    Button(
                        onClick = {
                            val url = if (esPayPal) {
                                "https://paypal.me/$paypal/${"%.2f".format(solicitud.fichaPrecioFinal).replace(",", ".")}EUR"
                            } else {
                                // Bizum no tiene esquema universal; usamos un enlace que funciona en algunos bancos
                                // y como fallback abrimos el marcador con el teléfono.
                                "tel:$bizum"
                            }
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                pagoIniciado = true
                            } catch (e: Exception) {
                                pagoIniciado = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = color)
                    ) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (esPayPal) "Abrir PayPal" else "Abrir Bizum",
                            color = Color.White, fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (pagoIniciado) {
                        OutlinedTextField(
                            value = referencia,
                            onValueChange = { referencia = it },
                            label = { Text("Referencia / concepto (opcional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Info, null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Tras pulsar \"Ya he pagado\", el técnico recibirá una notificación y deberá confirmar la recepción para cerrar la reforma.",
                                    fontSize = 11.sp, color = Color(0xFF92400E)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (metodoElegido != null && pagoIniciado) {
                Button(
                    onClick = { onMarcarPagado(metodoElegido!!, referencia.trim()) },
                    enabled = !pagando,
                    colors = ButtonDefaults.buttonColors(containerColor = verde)
                ) {
                    if (pagando) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Ya he pagado", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Spacer(Modifier.width(0.dp))
            }
        },
        dismissButton = {
            if (metodoElegido != null) {
                OutlinedButton(onClick = { metodoElegido = null; pagoIniciado = false }) { Text("Atrás") }
            } else {
                OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}
