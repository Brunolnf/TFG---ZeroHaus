package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.ViewModel.HistorialInformesViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialInformesScreen(
    viewModel: HistorialInformesViewModel,
    onVolver: () -> Unit = {},
    onVerInforme: (InformeEnergetico) -> Unit = {}
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val fondo = MaterialTheme.colorScheme.background
    val borde = MaterialTheme.colorScheme.outline
    val estado = viewModel.estado
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    LaunchedEffect(Unit) { viewModel.cargarInformes() }

    Scaffold(
        containerColor = fondo,
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
                            colors = CardDefaults.cardColors(containerColor = Color.White),
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
                                    "${String.format("%.1f", a.consumoEstimado)} kWh",
                                    "${String.format("%.1f", b.consumoEstimado)} kWh",
                                    b.consumoEstimado - a.consumoEstimado,
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
                                    "${String.format("%.1f", a.costeAnual)} €",
                                    "${String.format("%.1f", b.costeAnual)} €",
                                    b.costeAnual - a.costeAnual,
                                    verde, gris
                                )

                                Spacer(Modifier.height(12.dp))
                                val mejora = a.consumoEstimado - b.consumoEstimado
                                val mejoraColor = if (mejora > 0) verde else if (mejora < 0) Color(0xFFDC2626) else gris
                                val mejoraTexto = when {
                                    mejora > 0 -> "Mejora de ${String.format("%.1f", mejora)} kWh entre informes"
                                    mejora < 0 -> "Aumento de ${String.format("%.1f", -mejora)} kWh entre informes"
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
                            containerColor = if (seleccionado) Color(0xFFD1FAE5) else Color.White
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
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (seleccionado) {
                                        Icon(Icons.Default.CheckCircle, null, tint = verde, modifier = Modifier.size(20.dp))
                                    }
                                    EtiquetaBadge(informe.etiqueta)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${String.format("%.1f", informe.consumoEstimado)} kWh/año", color = gris, fontSize = 13.sp)
                                Text("${String.format("%.1f", informe.emisiones)} kg CO₂", color = gris, fontSize = 13.sp)
                                Text("${String.format("%.1f", informe.costeAnual)} €/año", color = gris, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
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
