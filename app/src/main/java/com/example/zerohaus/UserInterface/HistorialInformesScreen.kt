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
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)
    val estado = viewModel.estado
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    LaunchedEffect(Unit) { viewModel.cargarInformes() }

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = { Column { Text("Historial de informes", fontWeight = FontWeight.SemiBold); Text("${estado.informes.size} informes generados", color = gris, fontSize = 12.sp) } },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = { if (estado.modoComparar) { TextButton(onClick = { viewModel.limpiarComparacion() }) { Text("Cancelar", color = Color(0xFFDC2626)) } } }
            )
        }
    ) { pv ->
        if (estado.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = verde) }
        } else if (estado.informes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pv), contentAlignment = Alignment.Center) { Text("No hay informes. Realiza un preestudio.", color = gris) }
        } else {
            LazyColumn(modifier = Modifier.padding(pv).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Botón comparar
                if (!estado.modoComparar && estado.informes.size >= 2) {
                    item {
                        OutlinedButton(onClick = { viewModel.seleccionarParaComparar(estado.informes.first()) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, verde)) {
                            Icon(Icons.Default.Info, null, tint = verde); Spacer(Modifier.width(8.dp))
                            Text("Comparar informes", color = verde, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (estado.modoComparar && estado.informeComparar == null) {
                    item {
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF))) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = Color(0xFF2563EB)); Spacer(Modifier.width(8.dp))
                                Text("Selecciona un segundo informe para comparar", color = Color(0xFF1E40AF), fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Panel de comparación
                if (estado.informeSeleccionado != null && estado.informeComparar != null) {
                    item {
                        val a = estado.informeSeleccionado!!
                        val b = estado.informeComparar!!
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, verde), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Comparación", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    Text("", modifier = Modifier.weight(1f))
                                    Text(sdf.format(Date(a.fechaGeneracion)), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    Text(sdf.format(Date(b.fechaGeneracion)), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(8.dp))
                                FilaComp("Etiqueta", a.etiqueta, b.etiqueta)
                                FilaComp("Consumo", "${a.consumoEstimado} kWh", "${b.consumoEstimado} kWh")
                                FilaComp("Emisiones", "${a.emisiones} kg", "${b.emisiones} kg")
                                FilaComp("Coste", "${a.costeAnual} €", "${b.costeAnual} €")
                                Spacer(Modifier.height(10.dp))
                                val mejora = a.consumoEstimado - b.consumoEstimado
                                val mejoraColor = if (mejora > 0) verde else Color(0xFFDC2626)
                                Text(if (mejora > 0) "Mejora de ${String.format("%.1f", mejora)} kWh" else "Empeora ${String.format("%.1f", -mejora)} kWh", color = mejoraColor, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Lista de informes
                items(estado.informes) { informe ->
                    val seleccionado = informe.id == estado.informeSeleccionado?.id || informe.id == estado.informeComparar?.id
                    Card(
                        onClick = { if (estado.modoComparar) viewModel.seleccionarParaComparar(informe) else onVerInforme(informe) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (seleccionado) Color(0xFFD1FAE5) else Color.White),
                        border = BorderStroke(1.dp, if (seleccionado) verde else borde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(informe.nombreVivienda, fontWeight = FontWeight.SemiBold)
                                    Text(sdf.format(Date(informe.fechaGeneracion)), color = gris, fontSize = 12.sp)
                                }
                                Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = verde.copy(0.12f))) {
                                    Text(informe.etiqueta, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = verde)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${informe.consumoEstimado} kWh/año", color = gris, fontSize = 13.sp)
                                Text("${informe.emisiones} kg CO₂/año", color = gris, fontSize = 13.sp)
                                Text("${informe.costeAnual} €/año", color = gris, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaComp(label: String, v1: String, v2: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFF6B7280), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(v1, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(v2, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}
