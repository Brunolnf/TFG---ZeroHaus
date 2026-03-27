
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelScreen(
    panelViewModel: PanelViewModel, certificadoViewModel: CertificadoViewModel,
    onCerrarSesion: () -> Unit = {}, onNuevoPreestudio: () -> Unit = {}, onBuscarTecnicos: () -> Unit = {},
    onMisProyectos: () -> Unit = {}, onRankings: () -> Unit = {}, onVerUltimoInforme: () -> Unit = {},
    onPerfil: () -> Unit = {}, onPresupuestos: () -> Unit = {}, onHistorialInformes: () -> Unit = {},
    onMisViviendas: () -> Unit = {}, onChats: () -> Unit = {}, onGraficas: () -> Unit = {},
    onMapaTecnicos: () -> Unit = {}, onAjustes: () -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val estado = panelViewModel.estado; val certEstado = certificadoViewModel.estado
    var mostrarNotif by remember { mutableStateOf(false) }; var mostrarCert by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let { certificadoViewModel.seleccionarArchivo(it, "archivo") } }
    LaunchedEffect(Unit) { panelViewModel.cargarDatos(); certificadoViewModel.cargarCertificados() }
    val nombre = estado.usuario?.nombre ?: "Usuario"; val fotoUrl = estado.usuario?.fotoPerfil ?: ""
    val vivienda = estado.vivienda; val informe = estado.ultimoInforme

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
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
                    if (estado.hayNoLeidas) { Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFEF4444)).align(Alignment.TopEnd).offset(x = (-6).dp, y = 6.dp)) }
                }
                IconButton(onClick = onAjustes) {
                    Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }
        )
    }) { pv ->
        if (estado.cargando) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = verde) } }
        else {
            LazyColumn(Modifier.padding(pv).fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Saludo con avatar
                item {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (fotoUrl.isNotEmpty()) {
                            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(fotoUrl).crossfade(true).build(), contentDescription = "Perfil", contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onPerfil() })
                        } else {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(verde.copy(0.12f)).clickable { onPerfil() }, contentAlignment = Alignment.Center) {
                                Text(nombre.take(1).uppercase(), color = verde, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Hola, $nombre", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text("Gestiona tu eficiencia energética", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                }

                // Tarjeta vivienda
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = verde), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Tu vivienda", color = Color.White.copy(0.85f), fontSize = 14.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(vivienda?.nombre ?: "Sin vivienda", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                if (informe != null) { Box(Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), contentAlignment = Alignment.Center) { Text(informe.etiqueta, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = verde) } }
                            }
                            Spacer(Modifier.height(14.dp))
                            if (informe != null) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column { Text("Consumo", color = Color.White.copy(0.8f), fontSize = 13.sp); Text("${informe.consumoEstimado} kWh", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                                    Column(horizontalAlignment = Alignment.End) { Text("Emisiones", color = Color.White.copy(0.8f), fontSize = 13.sp); Text("${informe.emisiones} kg CO₂", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                                    Column(horizontalAlignment = Alignment.End) { Text("Coste", color = Color.White.copy(0.8f), fontSize = 13.sp); Text("${informe.costeAnual} €/año", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                                }
                            } else { Text("Realiza un preestudio para ver datos", color = Color.White.copy(0.8f)) }
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = onVerUltimoInforme, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.18f)), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 14.dp)) {
                                Text(if (informe != null) "Ver último informe →" else "Crear preestudio →", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            }
                        }
                    }
                }

                item { Text("Acciones rápidas", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground) }
                item { T(Icons.Default.Add, Color(0xFFD1FAE5), Color(0xFF059669), "Nuevo preestudio", "Analiza tu vivienda", onNuevoPreestudio) }
                item { T(Icons.Default.Home, Color(0xFFE0F2FE), Color(0xFF0284C7), "Mis viviendas", "Gestiona viviendas", onMisViviendas) }
                item { T(Icons.Default.Place, Color(0xFFDBEAFE), Color(0xFF2563EB), "Buscar técnicos", "Encuentra profesionales", onBuscarTecnicos) }
                item { T(Icons.Default.LocationOn, Color(0xFFFEF3C7), Color(0xFFD97706), "Mapa técnicos", "Ver en mapa", onMapaTecnicos) }
                item { T(Icons.Default.MailOutline, Color(0xFFD1FAE5), Color(0xFF059669), "Mensajes", "Chat con técnicos", onChats) }
                item { T(Icons.Default.Menu, Color(0xFFEDE9FE), Color(0xFF7C3AED), "Mis proyectos", "Gestiona reformas", onMisProyectos) }
                item { T(Icons.Default.Star, Color(0xFFFFEDD5), Color(0xFFEA580C), "Rankings", "Mejores técnicos", onRankings) }
                item { T(Icons.Default.Description, Color(0xFFDBEAFE), Color(0xFF2563EB), "Presupuestos", "Gestiona solicitudes", onPresupuestos) }
                item { T(Icons.Default.Assessment, Color(0xFFD1FAE5), Color(0xFF059669), "Historial informes", "Compara evolución", onHistorialInformes) }
                item { T(Icons.Default.ShowChart, Color(0xFFFCE7F3), Color(0xFFDB2777), "Gráficas consumo", "Visualiza tendencias", onGraficas) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    // Diálogo notificaciones
    if (mostrarNotif) { val sdf = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }; AlertDialog(onDismissRequest = { mostrarNotif = false }, title = { Text("Notificaciones", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }, text = { Column(Modifier.fillMaxWidth()) { if (estado.notificaciones.isEmpty()) Text("No tienes notificaciones", color = Color(0xFF6B7280)) else { LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(estado.notificaciones) { n -> Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(n.titulo, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), fontSize = 14.sp); if (!n.leida) Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444))) }; Spacer(Modifier.height(4.dp)); Text(n.detalle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp); Spacer(Modifier.height(4.dp)); Text(sdf.format(Date(n.fecha)), color = Color(0xFF9CA3AF), fontSize = 11.sp) } } } }; Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = { panelViewModel.marcarTodasLeidas() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("Marcar todas como leídas") } } } }, confirmButton = { Button(onClick = { mostrarNotif = false }, colors = ButtonDefaults.buttonColors(containerColor = verde)) { Text("Cerrar", color = Color.White) } }) }

    // Diálogo certificado
    if (mostrarCert) { var abrirT by remember { mutableStateOf(false) }; AlertDialog(onDismissRequest = { mostrarCert = false; certificadoViewModel.limpiar() }, title = { Text("Subir certificado", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(value = certEstado.nombre, onValueChange = { certificadoViewModel.cambiarNombre(it) }, label = { Text("Nombre") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()); Box(Modifier.fillMaxWidth()) { OutlinedTextField(value = certEstado.tipo, onValueChange = {}, readOnly = true, label = { Text("Tipo") }, trailingIcon = { IconButton(onClick = { abrirT = true }) { Icon(Icons.Default.ArrowDropDown, null) } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()); DropdownMenu(expanded = abrirT, onDismissRequest = { abrirT = false }) { listOf("Instalación","Auditoría","Energías renovables","Certificación").forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { certificadoViewModel.cambiarTipo(o); abrirT = false }) } } }; OutlinedButton(onClick = { filePicker.launch("*/*") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(if (certEstado.archivoUri != null) "Archivo seleccionado" else "Seleccionar archivo") }; if (certEstado.cargando) LinearProgressIndicator(Modifier.fillMaxWidth(), color = verde); certEstado.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; if (certEstado.exito) Text("Certificado subido", color = verde) } }, confirmButton = { Button(onClick = { certificadoViewModel.subirCertificado() }, enabled = !certEstado.cargando && certEstado.archivoUri != null && certEstado.nombre.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = verde)) { Text("Subir", color = Color.White) } }, dismissButton = { OutlinedButton(onClick = { mostrarCert = false; certificadoViewModel.limpiar() }) { Text("Cancelar") } }) }
}

@Composable
private fun T(icono: ImageVector, bgI: Color, cI: Color, titulo: String, sub: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.5f))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(bgI), contentAlignment = Alignment.Center) { Icon(icono, null, tint = cI, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface); Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        }
    }
}