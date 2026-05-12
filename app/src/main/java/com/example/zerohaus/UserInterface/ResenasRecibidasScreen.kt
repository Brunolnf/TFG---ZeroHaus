package com.example.zerohaus.UserInterface

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Modelos.Resena
import com.example.zerohaus.ViewModel.ResenasRecibidasViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResenasRecibidasScreen(
    viewModel: ResenasRecibidasViewModel,
    onVolver: () -> Unit = {}
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val estado = viewModel.estado

    LaunchedEffect(Unit) { viewModel.cargar() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Mis reseñas", fontWeight = FontWeight.SemiBold) },
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

        LazyColumn(
            Modifier.padding(pv).fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Resumen de valoraciones
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(0.4f)) {
                            Text(
                                if (estado.totales > 0) "${estado.media}" else "—",
                                fontSize = 42.sp, fontWeight = FontWeight.Bold, color = verde
                            )
                            Row {
                                repeat(5) { i ->
                                    val activa = i < estado.media.toInt()
                                    Icon(
                                        Icons.Default.Star, null,
                                        tint = if (activa) Color(0xFFEAB308) else gris.copy(0.3f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text("${estado.totales} reseñas", color = gris, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(0.6f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (puntos in 5 downTo 1) {
                                val count = estado.distribucion[puntos] ?: 0
                                val frac = if (estado.totales > 0) count.toFloat() / estado.totales else 0f
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("$puntos", fontSize = 12.sp, color = gris, modifier = Modifier.width(12.dp))
                                    Icon(Icons.Default.Star, null, tint = Color(0xFFEAB308), modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(6.dp))
                                    LinearProgressIndicator(
                                        progress = { frac },
                                        color = verde,
                                        trackColor = verde.copy(0.12f),
                                        modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("$count", fontSize = 11.sp, color = gris, modifier = Modifier.width(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (estado.resenas.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Star, null, tint = gris, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Aún no tienes reseñas", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Cuando tus clientes te valoren tras completar un trabajo, sus opiniones aparecerán aquí.",
                            color = gris, fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(estado.resenas) { r -> ResenaCard(r) }
            }
        }
    }
}

@Composable
private fun ResenaCard(r: Resena) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val sdf = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(verde.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        r.nombreUsuario.take(1).ifBlank { "?" }.uppercase(),
                        color = verde, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.nombreUsuario.ifBlank { "Cliente" }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(sdf.format(Date(r.fecha)), color = gris, fontSize = 11.sp)
                }
                Row {
                    repeat(5) { i ->
                        Icon(
                            Icons.Default.Star, null,
                            tint = if (i < r.puntuacion) Color(0xFFEAB308) else gris.copy(0.3f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            if (r.comentario.isNotBlank()) {
                Text(r.comentario, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}
