package com.example.zerohaus.UserInterface

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
import com.example.zerohaus.Modelos.Planes
import com.example.zerohaus.Modelos.esDestacado
import com.example.zerohaus.Modelos.esVerificado
import com.example.zerohaus.Util.Formato
import com.example.zerohaus.ViewModel.PanelTecnicoViewModel
import com.example.zerohaus.ViewModel.PanelViewModel
import com.example.zerohaus.Util.LocalCadenas
import java.util.Calendar
import java.util.Date

/**
 * Inicio del profesional: resumen de su perfil y actividad, accesos rápidos y notificaciones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelTecnicoScreen(
    viewModel: PanelTecnicoViewModel,
    panelViewModel: PanelViewModel,
    onAjustes: () -> Unit = {},
    onSuscripcion: () -> Unit = {},
    onResenas: () -> Unit = {},
    onEstadisticas: () -> Unit = {}
) {
    val c = LocalCadenas.current
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val estado = viewModel.estado
    val panelEstado = panelViewModel.estado
    val dorado = Color(0xFFF59E0B)

    var mostrarNotif by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.cargar()
        panelViewModel.cargarDatos()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
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
                    Box {
                        IconButton(onClick = { mostrarNotif = true }) {
                            Icon(Icons.Default.Notifications, c.panelNotificaciones, tint = gris, modifier = Modifier.size(24.dp))
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
                        Icon(Icons.Default.Settings, c.ajustesTitulo, tint = gris, modifier = Modifier.size(24.dp))
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
                        Text(c.ptecSinPerfilTitulo, fontWeight = FontWeight.SemiBold, color = Color(0xFF991B1B))
                        Text(
                            c.ptecSinPerfilSub,
                            fontSize = 13.sp, color = Color(0xFF7F1D1D)
                        )
                    }
                }
            } else {
                val tec = estado.tecnico

                Card(
                    colors = CardDefaults.cardColors(containerColor = verde),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(c.ptecHola, color = Color.White.copy(0.85f), fontSize = 14.sp)
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
                            StatVerde("⭐ ${tec.rating}", "${tec.opiniones} ${c.comOpiniones}")
                            StatVerde(
                                if (tec.esDestacado) c.estDestacado
                                else if (tec.esVerificado) c.estVerificado
                                else c.estGratuito,
                                c.subPlanActual
                            )
                        }
                    }
                }

                // Suscripción
                if (!tec.esVerificado) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = Color(0xFF92400E), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                c.ptecCtaSuscripcion,
                                fontSize = 13.sp, color = Color(0xFF92400E)
                            )
                        }
                    }
                }

                ResumenCard(
                    icono = Icons.Default.WorkspacePremium,
                    color = if (tec.esDestacado) dorado else if (tec.esVerificado) Color(0xFF065F46) else Color(0xFF2563EB),
                    titulo = c.tecMiSuscripcion,
                    subtitulo = if (tec.esDestacado) c.ptecPlanDestacadoActivo
                                else if (tec.esVerificado) c.ptecPlanVerificadoActivo
                                else c.ptecSinPlan,
                    badge = if (!tec.esVerificado) c.ptecMejora else null,
                    onClick = onSuscripcion
                )

                ResumenCard(
                    icono = Icons.Default.Star,
                    color = Color(0xFFEAB308),
                    titulo = c.resTitulo,
                    subtitulo = "${tec.opiniones} ${c.comOpiniones} · ⭐ ${tec.rating} ${c.ptecDeMedia}",
                    badge = null,
                    onClick = onResenas
                )

                ResumenCard(
                    icono = Icons.Default.BarChart,
                    color = Color(0xFF065F46),
                    titulo = c.estTitulo,
                    subtitulo = c.ptecEstadisticasSub,
                    badge = null,
                    onClick = onEstadisticas
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (mostrarNotif) {
        AlertDialog(
            onDismissRequest = { mostrarNotif = false },
            title = { Text(c.panelNotificaciones, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    if (panelEstado.notificaciones.isEmpty()) {
                        Text(c.panelSinNotificaciones, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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
                                        Text(formatTimestampTec(n.fecha, c.comHoy, c.comAyer), color = Color(0xFF9CA3AF), fontSize = 11.sp)
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

private fun formatTimestampTec(ts: Long, hoyTxt: String, ayerTxt: String): String {
    val hoy = Calendar.getInstance()
    val msg = Calendar.getInstance().apply { timeInMillis = ts }
    val sdfHora = Formato.fechas("HH:mm")
    val sdfFecha = Formato.fechas("dd MMM yyyy")
    return when {
        hoy.get(Calendar.YEAR) == msg.get(Calendar.YEAR) &&
        hoy.get(Calendar.DAY_OF_YEAR) == msg.get(Calendar.DAY_OF_YEAR) ->
            "$hoyTxt · ${sdfHora.format(Date(ts))}"
        hoy.get(Calendar.YEAR) == msg.get(Calendar.YEAR) &&
        hoy.get(Calendar.DAY_OF_YEAR) - msg.get(Calendar.DAY_OF_YEAR) == 1 ->
            "$ayerTxt · ${sdfHora.format(Date(ts))}"
        else -> sdfFecha.format(Date(ts))
    }
}
