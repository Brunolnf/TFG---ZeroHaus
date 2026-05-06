package com.example.zerohaus.UserInterface

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
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
                title = {
                    Column {
                        Text("Gráficas de consumo", fontWeight = FontWeight.SemiBold)
                        if (estado.informes.isNotEmpty()) {
                            Text("${estado.informes.size} informes generados", color = gris, fontSize = 12.sp)
                        }
                    }
                },
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sin datos todavía", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Genera un informe energético para ver las gráficas", color = gris, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
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
            // RESUMEN
            val ultimo = datos.last()
            val primero = datos.first()
            val mejora = primero.consumoEstimado - ultimo.consumoEstimado

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, borde)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Resumen", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ResumenStat("Informes", "${datos.size}", gris)
                        ResumenStat("Consumo actual", "${String.format("%.1f", ultimo.consumoEstimado)} kWh", gris)
                        ResumenStat("Etiqueta", ultimo.etiqueta, etiquetaColor(ultimo.etiqueta))
                    }
                    if (datos.size >= 2) {
                        Spacer(Modifier.height(10.dp))
                        val mejoraColor = if (mejora >= 0) verde else Color(0xFFDC2626)
                        val mejoraTexto = if (mejora >= 0)
                            "Reducción de ${String.format("%.1f", mejora)} kWh respecto al primer informe"
                        else
                            "Aumento de ${String.format("%.1f", -mejora)} kWh respecto al primer informe"
                        Text(mejoraTexto, color = mejoraColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            // EVOLUCIÓN ETIQUETA ENERGÉTICA
            if (datos.size >= 2) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, borde)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Evolución etiqueta energética", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            datos.forEachIndexed { i, inf ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(etiquetaColor(inf.etiqueta), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(inf.etiqueta, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(sdf.format(Date(inf.fechaGeneracion)), fontSize = 10.sp, color = gris)
                                }
                                if (i < datos.size - 1) {
                                    Text("→", color = gris, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }

            // GRÁFICA CONSUMO (barras)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, borde)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Consumo energético (kWh/año)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))

                    val maxConsumo = datos.maxOf { it.consumoEstimado }.toFloat().coerceAtLeast(1f)
                    val textPaint = android.graphics.Paint().apply {
                        color = gris.toArgb()
                        textSize = 28f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    val valuePaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.DKGRAY
                        textSize = 26f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                        isAntiAlias = true
                    }

                    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
                        val chartHeight = size.height - 30f
                        val barWidth = size.width / datos.size
                        val barPad = barWidth * 0.2f

                        drawLine(Color(0xFFE5E7EB), Offset(0f, chartHeight), Offset(size.width, chartHeight), 2f)

                        datos.forEachIndexed { i, inf ->
                            val valor = inf.consumoEstimado.toFloat()
                            val h = (valor / maxConsumo) * chartHeight
                            val left = i * barWidth + barPad / 2
                            val barW = barWidth - barPad

                            drawRect(
                                color = verde,
                                topLeft = Offset(left, chartHeight - h),
                                size = Size(barW, h)
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                "${String.format("%.0f", valor)}",
                                left + barW / 2,
                                chartHeight - h - 6f,
                                valuePaint
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                sdf.format(Date(inf.fechaGeneracion)),
                                left + barW / 2,
                                size.height,
                                textPaint
                            )
                        }
                    }
                }
            }

            // GRÁFICA EMISIONES CO2 (línea)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, borde)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Emisiones CO₂ (kg/año)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))

                    val max = datos.maxOf { it.emisiones }.toFloat().coerceAtLeast(1f)
                    val valuePaint = android.graphics.Paint().apply {
                        color = naranja.toArgb()
                        textSize = 26f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                        isAntiAlias = true
                    }
                    val textPaint = android.graphics.Paint().apply {
                        color = gris.toArgb()
                        textSize = 28f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }

                    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
                        val chartHeight = size.height - 30f
                        val stepX = if (datos.size > 1) size.width / (datos.size - 1) else size.width / 2

                        drawLine(Color(0xFFE5E7EB), Offset(0f, chartHeight), Offset(size.width, chartHeight), 2f)

                        val puntos = datos.mapIndexed { i, inf ->
                            val x = if (datos.size > 1) i * stepX else size.width / 2
                            val y = chartHeight - (inf.emisiones.toFloat() / max) * chartHeight
                            Offset(x, y)
                        }

                        if (puntos.size > 1) {
                            val path = Path()
                            puntos.forEachIndexed { i, p ->
                                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                            }
                            drawPath(path, color = naranja, style = Stroke(width = 4f))
                        }

                        puntos.forEachIndexed { i, p ->
                            drawCircle(naranja, radius = 10f, center = p)
                            drawCircle(Color.White, radius = 5f, center = p)
                            val valor = datos[i].emisiones
                            drawContext.canvas.nativeCanvas.drawText(
                                "${String.format("%.1f", valor)}",
                                p.x,
                                p.y - 18f,
                                valuePaint
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                sdf.format(Date(datos[i].fechaGeneracion)),
                                p.x,
                                size.height,
                                textPaint
                            )
                        }
                    }
                }
            }

            // GRÁFICA COSTE (barras)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, borde)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Coste anual estimado (€)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))

                    val max = datos.maxOf { it.costeAnual }.toFloat().coerceAtLeast(1f)
                    val textPaint = android.graphics.Paint().apply {
                        color = gris.toArgb()
                        textSize = 28f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    val valuePaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.DKGRAY
                        textSize = 26f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                        isAntiAlias = true
                    }

                    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
                        val chartHeight = size.height - 30f
                        val barWidth = size.width / datos.size
                        val barPad = barWidth * 0.2f

                        drawLine(Color(0xFFE5E7EB), Offset(0f, chartHeight), Offset(size.width, chartHeight), 2f)

                        datos.forEachIndexed { i, inf ->
                            val valor = inf.costeAnual.toFloat()
                            val h = (valor / max) * chartHeight
                            val left = i * barWidth + barPad / 2
                            val barW = barWidth - barPad

                            drawRect(
                                color = azul,
                                topLeft = Offset(left, chartHeight - h),
                                size = Size(barW, h)
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                "${String.format("%.0f", valor)}€",
                                left + barW / 2,
                                chartHeight - h - 6f,
                                valuePaint
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                sdf.format(Date(inf.fechaGeneracion)),
                                left + barW / 2,
                                size.height,
                                textPaint
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ResumenStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
        Text(label, fontSize = 11.sp, color = Color(0xFF6B7280))
    }
}

private fun etiquetaColor(etiqueta: String): Color = when (etiqueta) {
    "A" -> Color(0xFF16A34A)
    "B" -> Color(0xFF65A30D)
    "C" -> Color(0xFFCA8A04)
    "D" -> Color(0xFFEA580C)
    "E" -> Color(0xFFDC2626)
    "F" -> Color(0xFF9F1239)
    else -> Color(0xFF6B2737)
}
