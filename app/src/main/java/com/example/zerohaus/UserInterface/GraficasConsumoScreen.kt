package com.example.zerohaus.UserInterface

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.GraficasViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraficasConsumoScreen(
    viewModel: GraficasViewModel,
    onVolver: () -> Unit = {}
) {

    val verde = Color(0xFF16A34A)
    val azul = Color(0xFF2563EB)
    val naranja = Color(0xFFEA580C)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)

    val estado = viewModel.estado
    val sdf = remember { SimpleDateFormat("MMM yy", Locale.getDefault()) }

    LaunchedEffect(Unit) { viewModel.cargarDatos() }

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = { Text("Gráficas de consumo") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->

        if (estado.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = verde)
            }
            return@Scaffold
        }

        if (estado.informes.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Genera informes para ver gráficas", color = gris)
            }
            return@Scaffold
        }

        val datos = estado.informes

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 🔹 RESUMEN
            val ultimo = datos.last()
            val primero = datos.first()
            val mejora = primero.consumoEstimado - ultimo.consumoEstimado

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, borde)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Resumen", fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Informes: ${datos.size}")
                    Text("Consumo actual: ${ultimo.consumoEstimado} kWh")
                    Text("Mejora: $mejora")
                }
            }

            // 🔹 GRÁFICA CONSUMO
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, borde)
            ) {
                Column(Modifier.padding(16.dp)) {

                    Text("Consumo (kWh/año)")
                    Spacer(Modifier.height(12.dp))

                    val maxConsumo = datos.maxOf { it.consumoEstimado }
                        .toFloat()
                        .coerceAtLeast(1f)

                    Canvas(Modifier.fillMaxWidth().height(200.dp)) {

                        val barWidth = size.width / datos.size

                        datos.forEachIndexed { i, inf ->

                            val valor = inf.consumoEstimado.toFloat()
                            val h = (valor / maxConsumo) * size.height

                            drawRect(
                                color = verde,
                                topLeft = Offset(i * barWidth, size.height - h),
                                size = Size(barWidth * 0.6f, h)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        datos.forEach {
                            Text(
                                sdf.format(Date(it.fechaGeneracion)),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // 🔹 GRÁFICA EMISIONES
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, borde)
            ) {
                Column(Modifier.padding(16.dp)) {

                    Text("Emisiones CO₂")
                    Spacer(Modifier.height(12.dp))

                    val max = datos.maxOf { it.emisiones }
                        .toFloat()
                        .coerceAtLeast(1f)

                    Canvas(Modifier.fillMaxWidth().height(180.dp)) {

                        val stepX = size.width / (datos.size - 1).coerceAtLeast(1)

                        val path = Path()

                        datos.forEachIndexed { i, inf ->

                            val x = i * stepX
                            val valor = inf.emisiones.toFloat()
                            val y = size.height - (valor / max) * size.height

                            if (i == 0) path.moveTo(x, y)
                            else path.lineTo(x, y)
                        }

                        drawPath(path, color = naranja, style = Stroke(width = 4f))
                    }
                }
            }

            // 🔹 GRÁFICA COSTE
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, borde)
            ) {
                Column(Modifier.padding(16.dp)) {

                    Text("Coste (€)")
                    Spacer(Modifier.height(12.dp))

                    val max = datos.maxOf { it.costeAnual }
                        .toFloat()
                        .coerceAtLeast(1f)

                    Canvas(Modifier.fillMaxWidth().height(200.dp)) {

                        val barWidth = size.width / datos.size

                        datos.forEachIndexed { i, inf ->

                            val valor = inf.costeAnual.toFloat()
                            val h = (valor / max) * size.height

                            drawRect(
                                color = azul,
                                topLeft = Offset(i * barWidth, size.height - h),
                                size = Size(barWidth * 0.6f, h)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}