
package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.InformeViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformeScreen(
    viewModel: InformeViewModel,
    onVolver: () -> Unit = {},
    onContactarTecnicos: () -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val borde = Color(0xFFE5E7EB)
    val informe = viewModel.informe
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val ctx = LocalContext.current

    var mostrarDialogCompartir by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (informe == null) viewModel.cargarUltimoInforme() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Informe energético", fontWeight = FontWeight.SemiBold)
                        Text("Resumen de eficiencia", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    if (informe != null) {
                        IconButton(onClick = { mostrarDialogCompartir = true }) {
                            Icon(Icons.Default.Share, "Compartir")
                        }
                    }
                }
            )
        }
    ) { pv ->
        if (viewModel.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = verde)
            }
        } else if (informe == null) {
            Box(Modifier.fillMaxSize().padding(pv), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Assessment, null, tint = gris, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No hay informes disponibles", color = gris)
                    Text("Realiza un preestudio primero", color = gris, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.cargarUltimoInforme() }) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reintentar")
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .padding(pv)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Vivienda
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, borde),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Vivienda", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(informe.nombreVivienda, fontWeight = FontWeight.Medium)
                        Text(
                            "Generado: ${sdf.format(Date(informe.fechaGeneracion))}",
                            color = gris, fontSize = 12.sp
                        )
                    }
                }

                // Calificación
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, borde),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Calificación energética", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Etiqueta", color = gris, fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                EtiquetaBadge(informe.etiqueta)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Estado", color = gris, fontSize = 12.sp)
                                Text(informe.estadoEficiencia, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                // Indicadores
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, borde),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Indicadores", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Consumo", color = gris, fontSize = 12.sp)
                                Text("${informe.consumoEstimado} kWh/año", fontWeight = FontWeight.Medium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Emisiones", color = gris, fontSize = 12.sp)
                                Text("${informe.emisiones} kg CO₂/año", fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Coste anual", color = gris, fontSize = 12.sp)
                        Text("${informe.costeAnual} €/año", fontWeight = FontWeight.Medium)
                    }
                }

                // Recomendaciones
                if (informe.recomendaciones.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, borde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Recomendaciones", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(10.dp))
                            informe.recomendaciones.forEachIndexed { i, r ->
                                Text("• ${r.titulo}", fontWeight = FontWeight.Medium)
                                Text("Ahorro estimado: ${r.ahorroEstimado}%", color = gris, fontSize = 12.sp)
                                if (i < informe.recomendaciones.lastIndex) Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }

                // Botones
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    OutlinedButton(
                        onClick = { mostrarDialogCompartir = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, verde)
                    ) {
                        Icon(Icons.Default.Share, null, tint = verde, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Compartir", color = verde, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onContactarTecnicos,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = verde),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.AccountBox, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Técnicos", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ── Diálogo de compartir ──
    if (mostrarDialogCompartir && informe != null) {
        AlertDialog(
            onDismissRequest = { mostrarDialogCompartir = false },
            title = { Text("Compartir informe", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Elige cómo quieres compartir el informe energético en PDF:",
                        color = gris,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))

                    OpcionCompartir(
                        icono = Icons.Default.Email,
                        titulo = "Enviar por email",
                        subtitulo = "Gmail, Outlook u otro cliente de correo",
                        color = Color(0xFF2563EB)
                    ) {
                        mostrarDialogCompartir = false
                        compartirPorEmail(ctx, informe)
                    }

                    OpcionCompartir(
                        icono = Icons.Default.Chat,
                        titulo = "Enviar por WhatsApp",
                        subtitulo = "Comparte el PDF directamente en WhatsApp",
                        color = Color(0xFF16A34A)
                    ) {
                        mostrarDialogCompartir = false
                        compartirPorWhatsApp(ctx, informe)
                    }

                    OpcionCompartir(
                        icono = Icons.Default.MoreHoriz,
                        titulo = "Otras aplicaciones",
                        subtitulo = "Telegram, Drive, Dropbox…",
                        color = Color(0xFF6B7280)
                    ) {
                        mostrarDialogCompartir = false
                        compartirInforme(ctx, informe)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { mostrarDialogCompartir = false }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun OpcionCompartir(
    icono: ImageVector,
    titulo: String,
    subtitulo: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = color.copy(alpha = 0.12f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icono, null, tint = color, modifier = Modifier.size(24.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitulo, color = Color(0xFF6B7280), fontSize = 12.sp)
        }
        Icon(
            Icons.Default.ChevronRight, null,
            tint = Color(0xFFD1D5DB),
            modifier = Modifier.size(20.dp)
        )
    }
}
