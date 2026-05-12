package com.example.zerohaus.UserInterface

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.CertificadoViewModel
import com.example.zerohaus.ViewModel.PanelTecnicoViewModel
import com.example.zerohaus.ViewModel.PanelViewModel
import com.example.zerohaus.Util.LocalCadenas
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelTecnicoScreen(
    viewModel: PanelTecnicoViewModel,
    panelViewModel: PanelViewModel,
    certificadoViewModel: CertificadoViewModel,
    onAjustes: () -> Unit = {},
    onProyectos: () -> Unit = {},
    onResenas: () -> Unit = {},
    onEstadisticas: () -> Unit = {}
) {
    val c = LocalCadenas.current
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val estado = viewModel.estado
    val panelEstado = panelViewModel.estado
    val certEstado = certificadoViewModel.estado

    var mostrarNotif by remember { mutableStateOf(false) }
    var mostrarCert by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { certificadoViewModel.seleccionarArchivo(it, "archivo") }
    }

    LaunchedEffect(Unit) {
        viewModel.cargar()
        panelViewModel.cargarDatos()
        certificadoViewModel.cargarCertificados()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZeroHausLogo(size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("ZeroHaus Pro", color = verde, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { mostrarCert = true }) {
                        Icon(Icons.Default.AddCircle, null, tint = gris, modifier = Modifier.size(24.dp))
                    }
                    Box {
                        IconButton(onClick = { mostrarNotif = true }) {
                            Icon(Icons.Default.Notifications, null, tint = gris, modifier = Modifier.size(24.dp))
                        }
                        if (panelEstado.hayNoLeidas) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-6).dp, y = 6.dp)
                            )
                        }
                    }
                    IconButton(onClick = onAjustes) {
                        Icon(Icons.Default.Settings, null, tint = gris, modifier = Modifier.size(24.dp))
                    }
                }
            )
        }
    ) { pv ->
        Column(
            Modifier
                .padding(pv)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (estado.cargando) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (estado.tecnico == null) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No encontramos tu perfil", fontWeight = FontWeight.SemiBold, color = Color(0xFF991B1B))
                        Text(
                            "Tu cuenta está marcada como técnico pero no hay un perfil vinculado en la colección /tecnicos. Pide al administrador que vincule tu uid de Auth con tu perfil.",
                            fontSize = 13.sp, color = Color(0xFF7F1D1D)
                        )
                    }
                }
            } else {
                val tec = estado.tecnico

                // Cabecera verde con saludo, ciudad y stats
                Card(
                    colors = CardDefaults.cardColors(containerColor = verde),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Hola,", color = Color.White.copy(0.85f), fontSize = 14.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(tec.nombre, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        if (tec.ciudad.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(tec.ciudad, color = Color.White.copy(0.85f), fontSize = 14.sp)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatVerde("⭐ ${tec.rating}", "${tec.opiniones} opiniones")
                            StatVerde("${tec.proyectosCompletados}", "Proyectos")
                            StatVerde("${estado.solicitudesAceptadas}", "Trabajos activos")
                        }
                    }
                }

                // Mis proyectos asignados
                ResumenCard(
                    icono = Icons.Default.Construction,
                    color = Color(0xFF2563EB),
                    titulo = "Mis proyectos",
                    subtitulo = "Gestiona tareas, marca progreso y entrega trabajos",
                    badge = null,
                    onClick = onProyectos
                )

                // Reseñas recibidas
                ResumenCard(
                    icono = Icons.Default.Star,
                    color = Color(0xFFEAB308),
                    titulo = "Mis reseñas",
                    subtitulo = "${tec.opiniones} opiniones · ⭐ ${tec.rating} de media",
                    badge = null,
                    onClick = onResenas
                )

                // Estadísticas
                ResumenCard(
                    icono = Icons.Default.BarChart,
                    color = Color(0xFF059669),
                    titulo = "Estadísticas",
                    subtitulo = "Ingresos, tasa de aceptación y rendimiento",
                    badge = null,
                    onClick = onEstadisticas
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    // ── Diálogo de notificaciones ──
    if (mostrarNotif) {
        AlertDialog(
            onDismissRequest = { mostrarNotif = false },
            title = { Text(c.panelNotificaciones, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    if (panelEstado.notificaciones.isEmpty()) {
                        Text(c.panelSinNotificaciones, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(panelEstado.notificaciones) { n ->
                                val bgColor = if (!n.leida)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surface
                                Card(
                                    onClick = { if (!n.leida) panelViewModel.marcarLeida(n.id) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = bgColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(n.titulo, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                            if (!n.leida) Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(n.detalle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text(formatTimestampTec(n.fecha), color = Color(0xFF9CA3AF), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { panelViewModel.marcarTodasLeidas() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(c.panelMarcarLeidas)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { mostrarNotif = false }, colors = ButtonDefaults.buttonColors(containerColor = verde)) {
                    Text(c.cerrar, color = Color.White)
                }
            }
        )
    }

    // ── Diálogo de subir certificado/documento ──
    if (mostrarCert) {
        var abrirT by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { mostrarCert = false; certificadoViewModel.limpiar() },
            title = { Text(c.panelSubirCertificado, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = certEstado.nombre,
                        onValueChange = { certificadoViewModel.cambiarNombre(it) },
                        label = { Text(c.panelNombreLabel) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = certEstado.tipo,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(c.panelTipoLabel) },
                            trailingIcon = {
                                IconButton(onClick = { abrirT = true }) {
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = abrirT, onDismissRequest = { abrirT = false }) {
                            listOf("Instalación", "Auditoría", "Energías renovables", "Certificación").forEach { o ->
                                DropdownMenuItem(text = { Text(o) }, onClick = {
                                    certificadoViewModel.cambiarTipo(o); abrirT = false
                                })
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { filePicker.launch("*/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (certEstado.archivoUri != null) c.panelArchivoSeleccionado else c.panelSeleccionarArchivo)
                    }
                    if (certEstado.cargando) LinearProgressIndicator(Modifier.fillMaxWidth(), color = verde)
                    certEstado.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (certEstado.exito) Text(c.panelCertificadoSubido, color = verde)
                }
            },
            confirmButton = {
                Button(
                    onClick = { certificadoViewModel.subirCertificado() },
                    enabled = !certEstado.cargando && certEstado.archivoUri != null && certEstado.nombre.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = verde)
                ) { Text(c.subir, color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { mostrarCert = false; certificadoViewModel.limpiar() }) {
                    Text(c.cancelar)
                }
            }
        )
    }
}

@Composable
private fun StatVerde(valor: String, etiqueta: String) {
    Column {
        Text(valor, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(etiqueta, color = Color.White.copy(0.8f), fontSize = 12.sp)
    }
}

@Composable
private fun ResumenCard(
    icono: ImageVector,
    color: Color,
    titulo: String,
    subtitulo: String,
    badge: String?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icono, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(badge, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
        }
    }
}

private fun formatTimestampTec(ts: Long): String {
    val hoy = Calendar.getInstance()
    val msg = Calendar.getInstance().apply { timeInMillis = ts }
    val sdfHora = SimpleDateFormat("HH:mm", Locale.getDefault())
    val sdfFecha = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return when {
        hoy.get(Calendar.YEAR) == msg.get(Calendar.YEAR) &&
        hoy.get(Calendar.DAY_OF_YEAR) == msg.get(Calendar.DAY_OF_YEAR) ->
            "Hoy · ${sdfHora.format(Date(ts))}"
        hoy.get(Calendar.YEAR) == msg.get(Calendar.YEAR) &&
        hoy.get(Calendar.DAY_OF_YEAR) - msg.get(Calendar.DAY_OF_YEAR) == 1 ->
            "Ayer · ${sdfHora.format(Date(ts))}"
        else -> sdfFecha.format(Date(ts))
    }
}
