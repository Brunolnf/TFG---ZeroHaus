package com.example.zerohaus.UserInterface

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.EstadisticasTecnicoViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstadisticasTecnicoScreen(
    viewModel: EstadisticasTecnicoViewModel,
    onVolver: () -> Unit = {}
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val estado = viewModel.estado
    val euro = remember { NumberFormat.getCurrencyInstance(Locale("es", "ES")) }

    LaunchedEffect(Unit) { viewModel.cargar() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Estadísticas", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                }
            )
        }
    ) { pv ->
        if (estado.cargando) {
            Box(Modifier.padding(pv).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .padding(pv)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card destacada de ingresos
            Card(
                colors = CardDefaults.cardColors(containerColor = verde),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Ingresos generados", color = Color.White.copy(0.85f), fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        euro.format(estado.ingresosTotales),
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "De presupuestos aceptados o completados",
                        color = Color.White.copy(0.75f), fontSize = 12.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(0.2f))
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Pendientes de respuesta", color = Color.White.copy(0.75f), fontSize = 12.sp)
                            Text(euro.format(estado.ingresosPotenciales), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total presupuestos", color = Color.White.copy(0.75f), fontSize = 12.sp)
                            Text("${estado.solicitudesPresupuestadas + estado.solicitudesAceptadas + estado.solicitudesCompletadas}",
                                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // Métricas del negocio
            Text("Rendimiento", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricaMini("${estado.tasaRespuesta}%", "Tasa respuesta", Icons.Default.Reply, Color(0xFF2563EB), Modifier.weight(1f))
                MetricaMini("${estado.tasaAceptacion}%", "Tasa aceptación", Icons.Default.ThumbUp, verde, Modifier.weight(1f))
            }

            // Solicitudes detalladas
            Text("Solicitudes", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilaContador("Total recibidas", estado.solicitudesTotales, gris)
                    FilaContador("Pendientes", estado.solicitudesPendientes, Color(0xFFD97706))
                    FilaContador("Presupuestadas", estado.solicitudesPresupuestadas, Color(0xFF2563EB))
                    FilaContador("Aceptadas", estado.solicitudesAceptadas, verde)
                    FilaContador("Completadas", estado.solicitudesCompletadas, Color(0xFF059669))
                    FilaContador("Rechazadas", estado.solicitudesRechazadas, Color(0xFFDC2626))
                }
            }

            // Proyectos
            Text("Proyectos", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricaMini("${estado.proyectosActivos}", "Activos", Icons.Default.PlayCircle, Color(0xFF2563EB), Modifier.weight(1f))
                MetricaMini("${estado.proyectosCompletados}", "Completados", Icons.Default.CheckCircle, verde, Modifier.weight(1f))
            }

            // Reputación
            estado.tecnico?.let { tec ->
                Text("Reputación", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(verde.copy(0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFEAB308), modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            val ratingTexto = if (tec.opiniones == 0) "Sin valoraciones" else "%.1f".format(tec.rating)
                            Text(ratingTexto, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Text("${tec.opiniones} opiniones · ${tec.proyectosCompletados} proyectos completados",
                                color = gris, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun MetricaMini(valor: String, etiqueta: String, icono: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icono, null, tint = color, modifier = Modifier.size(20.dp))
            Text(valor, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(etiqueta, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FilaContador(etiqueta: String, valor: Int, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color)
        )
        Spacer(Modifier.width(10.dp))
        Text(etiqueta, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text("$valor", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
