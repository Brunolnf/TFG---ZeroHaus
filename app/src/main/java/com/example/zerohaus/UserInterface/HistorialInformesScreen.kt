package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Repositorios.RepositorioChat
import com.example.zerohaus.Util.Formato
import com.example.zerohaus.ViewModel.ChatViewModel
import com.example.zerohaus.ViewModel.HistorialInformesViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialInformesScreen(
    viewModel: HistorialInformesViewModel,
    chatViewModel: ChatViewModel? = null,
    onVolver: () -> Unit = {},
    onVerInforme: (InformeEnergetico) -> Unit = {}
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val fondo = MaterialTheme.colorScheme.background
    val borde = MaterialTheme.colorScheme.outline
    val estado = viewModel.estado
    val ctx = LocalContext.current
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    // Estado para el diálogo de compartir
    var informeACompartir by remember { mutableStateOf<InformeEnergetico?>(null) }
    var enviandoAChat by remember { mutableStateOf(false) }
    var mensajeEnvio by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(mensajeEnvio) {
        mensajeEnvio?.let { snackbarHostState.showSnackbar(it); mensajeEnvio = null }
    }
    LaunchedEffect(Unit) {
        viewModel.cargarInformes()
        chatViewModel?.cargarChats()
    }

    Scaffold(
        containerColor = fondo,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Historial de informes", fontWeight = FontWeight.SemiBold)
                        Text("${estado.informes.size} informes generados", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    if (estado.modoComparar) {
                        TextButton(onClick = { viewModel.limpiarComparacion() }) {
                            Text("Cancelar", color = Color(0xFFDC2626))
                        }
                    }
                }
            )
        }
    ) { pv ->
        if (estado.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = verde)
            }
        } else if (estado.informes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pv), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Assessment, null, tint = gris, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No hay informes aún", color = gris, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Realiza un preestudio para generar uno", color = gris, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(pv).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Botón entrar modo comparar
                if (!estado.modoComparar && estado.informes.size >= 2) {
                    item {
                        OutlinedButton(
                            onClick = { viewModel.activarModoComparar() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, verde)
                        ) {
                            Icon(Icons.Default.CompareArrows, null, tint = verde)
                            Spacer(Modifier.width(8.dp))
                            Text("Comparar dos informes", color = verde, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Instrucción modo comparar
                if (estado.modoComparar) {
                    val seleccionados = listOfNotNull(estado.informeSeleccionado, estado.informeComparar).size
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF))
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = Color(0xFF2563EB))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (seleccionados == 0) "Toca el primer informe a comparar"
                                    else if (seleccionados == 1) "Ahora toca el segundo informe"
                                    else "Comparación lista",
                                    color = Color(0xFF1E40AF),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Panel de comparación
                if (estado.informeSeleccionado != null && estado.informeComparar != null) {
                    item {
                        val a = estado.informeSeleccionado!!
                        val b = estado.informeComparar!!
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(2.dp, verde),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Comparación", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(Modifier.height(12.dp))

                                // Cabecera
                                Row(Modifier.fillMaxWidth()) {
                                    Text("", modifier = Modifier.weight(1.2f))
                                    Text(
                                        sdf.format(Date(a.fechaGeneracion)),
                                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        sdf.format(Date(b.fechaGeneracion)),
                                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text("Delta", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = gris, modifier = Modifier.weight(0.8f))
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                FilaComparacion("Etiqueta", a.etiqueta, b.etiqueta, null, verde, gris)
                                FilaComparacion(
                                    "Consumo",
                                    Formato.formatEnergia(a.consumoEstimado),
                                    Formato.formatEnergia(b.consumoEstimado),
                                    Formato.convertirEnergia(b.consumoEstimado - a.consumoEstimado),
                                    verde, gris
                                )
                                FilaComparacion(
                                    "Emisiones",
                                    "${String.format("%.1f", a.emisiones)} kg",
                                    "${String.format("%.1f", b.emisiones)} kg",
                                    b.emisiones - a.emisiones,
                                    verde, gris
                                )
                                FilaComparacion(
                                    "Coste",
                                    Formato.formatMoneda(a.costeAnual, 1),
                                    Formato.formatMoneda(b.costeAnual, 1),
                                    Formato.convertirMoneda(b.costeAnual - a.costeAnual),
                                    verde, gris
                                )

                                Spacer(Modifier.height(12.dp))
                                val mejora = a.consumoEstimado - b.consumoEstimado
                                val mejoraColor = if (mejora > 0) verde else if (mejora < 0) Color(0xFFDC2626) else gris
                                val mejoraTexto = when {
                                    mejora > 0 -> "Mejora de ${Formato.formatEnergia(mejora)} entre informes"
                                    mejora < 0 -> "Aumento de ${Formato.formatEnergia(-mejora)} entre informes"
                                    else -> "Sin cambio en consumo"
                                }
                                Text(mejoraTexto, color = mejoraColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Lista de informes
                items(estado.informes) { informe ->
                    val seleccionado = informe.id == estado.informeSeleccionado?.id || informe.id == estado.informeComparar?.id
                    Card(
                        onClick = {
                            if (estado.modoComparar) viewModel.seleccionarParaComparar(informe)
                            else onVerInforme(informe)
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (seleccionado) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, if (seleccionado) verde else borde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(informe.nombreVivienda, fontWeight = FontWeight.SemiBold)
                                    Text(sdf.format(Date(informe.fechaGeneracion)), color = gris, fontSize = 12.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (seleccionado) {
                                        Icon(Icons.Default.CheckCircle, null, tint = verde, modifier = Modifier.size(20.dp))
                                    }
                                    EtiquetaBadge(informe.etiqueta)
                                    if (!estado.modoComparar) {
                                        IconButton(
                                            onClick = { informeACompartir = informe },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Share, "Compartir", tint = gris, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(Formato.formatEnergiaAnual(informe.consumoEstimado), color = gris, fontSize = 13.sp)
                                Text("${String.format("%.1f", informe.emisiones)} kg CO₂", color = gris, fontSize = 13.sp)
                                Text(Formato.formatMonedaAnual(informe.costeAnual, 1), color = gris, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Diálogo: ¿Cómo compartir? ──
    informeACompartir?.let { informe ->
        val chats = chatViewModel?.listaEstado?.chats ?: emptyList()
        val miUid = chatViewModel?.miUid ?: ""

        AlertDialog(
            onDismissRequest = { informeACompartir = null },
            icon = { Icon(Icons.Default.Share, null, tint = verde) },
            title = { Text("Compartir informe", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("\"${informe.nombreVivienda}\"", fontSize = 13.sp, color = gris)
                    Spacer(Modifier.height(4.dp))

                    // Botón: Compartir externamente
                    OutlinedButton(
                        onClick = {
                            informeACompartir = null
                            compartirInforme(ctx, informe)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Compartir externamente")
                    }

                    if (chats.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Enviar a un técnico de la app:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        chats.forEach { chat ->
                            val otroNombre = chat.nombresParticipantes
                                .filterKeys { it != miUid }
                                .values.firstOrNull() ?: "Usuario"
                            val otroUid = chat.participantes.firstOrNull { it != miUid } ?: ""

                            Card(
                                onClick = {
                                    if (!enviandoAChat) {
                                        enviandoAChat = true
                                        informeACompartir = null
                                        enviarInformeAChat(ctx, informe, chat.id, otroNombre) { ok ->
                                            enviandoAChat = false
                                            mensajeEnvio = if (ok) "Informe enviado a $otroNombre" else "Error al enviar"
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(verde.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            otroNombre.take(1).uppercase(),
                                            color = verde,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                    Text(otroNombre, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    } else {
                        Text(
                            "No tienes chats activos. Contacta con un técnico primero para poder enviarle informes.",
                            fontSize = 12.sp,
                            color = gris
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { informeACompartir = null }) { Text("Cancelar") }
            }
        )
    }

    if (enviandoAChat) {
        AlertDialog(
            onDismissRequest = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = verde, modifier = Modifier.size(24.dp))
                    Text("Enviando informe...")
                }
            },
            confirmButton = {}
        )
    }
}

private fun enviarInformeAChat(
    context: android.content.Context,
    informe: InformeEnergetico,
    chatId: String,
    destinatario: String,
    onResult: (Boolean) -> Unit
) {
    try {
        val pdfFile = generarPdfParaChat(context, informe)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        val nombre = "Informe_${informe.nombreVivienda.replace(" ", "_")}.pdf"
        val bytes = pdfFile.length()
        val repo = RepositorioChat()
        repo.enviarArchivo(chatId, uri, nombre, bytes) { ok -> onResult(ok) }
    } catch (e: Exception) {
        onResult(false)
    }
}

private fun generarPdfParaChat(
    context: android.content.Context,
    informe: InformeEnergetico
): java.io.File {
    // Reutiliza la función de generación de PDF de CompartirInforme
    val dir = java.io.File(context.cacheDir, "informes")
    dir.mkdirs()
    val nombreSeguro = informe.nombreVivienda
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "vivienda" }
        .take(40)
    val fileName = "informe_${nombreSeguro}_${informe.fechaGeneracion}.pdf"
    val file = java.io.File(dir, fileName)
    if (!file.exists()) {
        // Llamar a la función interna del CompartirInforme
        compartirInformeSilencioso(context, informe, file)
    }
    return file
}

@Composable
private fun FilaComparacion(
    label: String,
    v1: String,
    v2: String,
    delta: Double?,
    verde: Color,
    gris: Color
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = gris, fontSize = 13.sp, modifier = Modifier.weight(1.2f))
        Text(v1, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(v2, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (delta != null) {
            val deltaColor = when {
                delta < 0 -> verde
                delta > 0 -> Color(0xFFDC2626)
                else -> gris
            }
            val deltaTexto = when {
                delta < 0 -> "↓ ${String.format("%.1f", -delta)}"
                delta > 0 -> "↑ ${String.format("%.1f", delta)}"
                else -> "="
            }
            Text(deltaTexto, color = deltaColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.8f))
        } else {
            Spacer(Modifier.weight(0.8f))
        }
    }
}
