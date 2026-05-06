package com.example.zerohaus.UserInterface

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.zerohaus.ViewModel.CertificadoViewModel
import com.example.zerohaus.ViewModel.PanelViewModel
import com.example.zerohaus.util.LocalCadenas
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelScreen(
    panelViewModel: PanelViewModel,
    certificadoViewModel: CertificadoViewModel,
    onNuevoPreestudio: () -> Unit = {},
    onMisViviendas: () -> Unit = {},
    onMisProyectos: () -> Unit = {},
    onPresupuestos: () -> Unit = {},
    onHistorialInformes: () -> Unit = {},
    onGraficas: () -> Unit = {},
    onVerUltimoInforme: () -> Unit = {},
    onPerfil: () -> Unit = {},
    onAjustes: () -> Unit = {}
) {
    val c = LocalCadenas.current
    val verde = Color(0xFF16A34A)
    val estado = panelViewModel.estado
    val certEstado = certificadoViewModel.estado
    var mostrarNotif by remember { mutableStateOf(false) }
    var mostrarCert by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { certificadoViewModel.seleccionarArchivo(it, "archivo") }
    }
    LaunchedEffect(Unit) { panelViewModel.cargarDatos(); certificadoViewModel.cargarCertificados() }
    val nombre = estado.usuario?.nombre ?: "Usuario"
    val fotoUrl = estado.usuario?.fotoPerfil ?: ""
    val vivienda = estado.vivienda
    val informe = estado.ultimoInforme

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZeroHausLogo(size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("ZeroHaus", color = verde, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { mostrarCert = true }) {
                        Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    }
                    Box {
                        IconButton(onClick = { mostrarNotif = true }) {
                            Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        }
                        if (estado.hayNoLeidas) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFEF4444)).align(Alignment.TopEnd).offset(x = (-6).dp, y = 6.dp))
                        }
                    }
                    IconButton(onClick = onAjustes) {
                        Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    }
                }
            )
        }
    ) { pv ->
        if (estado.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = verde) }
        } else {
            LazyColumn(
                Modifier.padding(pv).fillMaxSize().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (fotoUrl.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(fotoUrl).crossfade(true).build(),
                                contentDescription = "Perfil",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onPerfil() }
                            )
                        } else {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(verde.copy(0.12f)).clickable { onPerfil() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(nombre.take(1).uppercase(), color = verde, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${c.panelSaludo}$nombre", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text(c.panelSubtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                }

                item {
                    Card(colors = CardDefaults.cardColors(containerColor = verde), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text(c.panelVivienda, color = Color.White.copy(0.85f), fontSize = 14.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(vivienda?.nombre ?: c.panelSinVivienda, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                if (informe != null) {
                                    Box(Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(fondoEtiqueta(informe.etiqueta)), contentAlignment = Alignment.Center) {
                                        Text(informe.etiqueta, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = colorEtiqueta(informe.etiqueta))
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            if (informe != null) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column { Text(c.panelConsumo, color = Color.White.copy(0.8f), fontSize = 13.sp); Text("${informe.consumoEstimado} kWh", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                                    Column(horizontalAlignment = Alignment.End) { Text(c.panelEmisiones, color = Color.White.copy(0.8f), fontSize = 13.sp); Text("${informe.emisiones} kg CO₂", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                                    Column(horizontalAlignment = Alignment.End) { Text(c.panelCoste, color = Color.White.copy(0.8f), fontSize = 13.sp); Text("${informe.costeAnual} €/año", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                                }
                            } else {
                                Text(c.panelSinDatos, color = Color.White.copy(0.8f))
                            }
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = onVerUltimoInforme,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.18f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 14.dp)
                            ) {
                                Text(if (informe != null) c.panelVerInforme else c.panelCrearPreestudio, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            }
                        }
                    }
                }

                item { Text(c.panelAccionesRapidas, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground) }
                item { TarjetaAccion(Icons.Default.Add, Color(0xFFD1FAE5), Color(0xFF059669), c.panelNuevoPreestudio, c.panelNuevoPreestudioSub, onNuevoPreestudio) }
                item { TarjetaAccion(Icons.Default.Home, Color(0xFFE0F2FE), Color(0xFF0284C7), c.masViviendas, c.panelMisViviendasSub, onMisViviendas) }
                item { TarjetaAccion(Icons.Default.Menu, Color(0xFFEDE9FE), Color(0xFF7C3AED), c.masProyectos, c.panelMisProyectosSub, onMisProyectos) }
                item { TarjetaAccion(Icons.Default.Description, Color(0xFFDBEAFE), Color(0xFF2563EB), c.masPresupuestos, c.panelPresupuestosSub, onPresupuestos) }
                item { TarjetaAccion(Icons.Default.Assessment, Color(0xFFD1FAE5), Color(0xFF059669), c.explorarHistorial, c.panelHistorialInformesSub, onHistorialInformes) }
                item { TarjetaAccion(Icons.Default.ShowChart, Color(0xFFFCE7F3), Color(0xFFDB2777), c.explorarGraficas, c.panelGraficasSub, onGraficas) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (mostrarNotif) {
        AlertDialog(
            onDismissRequest = { mostrarNotif = false },
            title = { Text(c.panelNotificaciones, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    if (estado.notificaciones.isEmpty()) {
                        Text(c.panelSinNotificaciones, color = Color(0xFF6B7280))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(estado.notificaciones) { n ->
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
                                        Text(formatTimestampPanel(n.fecha), color = Color(0xFF9CA3AF), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { panelViewModel.marcarTodasLeidas() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
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

    if (mostrarCert) {
        var abrirT by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { mostrarCert = false; certificadoViewModel.limpiar() },
            title = { Text(c.panelSubirCertificado, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = certEstado.nombre, onValueChange = { certificadoViewModel.cambiarNombre(it) }, label = { Text(c.panelNombreLabel) }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = certEstado.tipo, onValueChange = {}, readOnly = true, label = { Text(c.panelTipoLabel) }, trailingIcon = { IconButton(onClick = { abrirT = true }) { Icon(Icons.Default.ArrowDropDown, null) } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                        DropdownMenu(expanded = abrirT, onDismissRequest = { abrirT = false }) {
                            listOf("Instalación", "Auditoría", "Energías renovables", "Certificación").forEach { o ->
                                DropdownMenuItem(text = { Text(o) }, onClick = { certificadoViewModel.cambiarTipo(o); abrirT = false })
                            }
                        }
                    }
                    OutlinedButton(onClick = { filePicker.launch("*/*") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
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
                OutlinedButton(onClick = { mostrarCert = false; certificadoViewModel.limpiar() }) { Text(c.cancelar) }
            }
        )
    }
}

@Composable
private fun TarjetaAccion(icono: ImageVector, bgI: Color, cI: Color, titulo: String, sub: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.5f))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(bgI), contentAlignment = Alignment.Center) {
                Icon(icono, null, tint = cI, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), modifier = Modifier.size(20.dp))
        }
    }
}

private fun formatTimestampPanel(ts: Long): String {
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
