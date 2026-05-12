package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.RankingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingsScreen(
    viewModel: RankingsViewModel,
    onVolver: () -> Unit = {},
    onVerPerfil: (String) -> Unit = {}
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val fondo = MaterialTheme.colorScheme.background
    val borde = MaterialTheme.colorScheme.outline

    LaunchedEffect(Unit) { viewModel.cargarRanking() }

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Rankings", fontWeight = FontWeight.SemiBold)
                        Text("Mejores técnicos valorados", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                }
            )
        }
    ) { pv ->
        if (viewModel.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = verde)
            }
        } else if (viewModel.ranking.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pv), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Star, null, tint = gris.copy(0.4f), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No hay técnicos registrados aún", color = gris)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { viewModel.cargarRanking() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reintentar")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(pv)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(viewModel.ranking) { index, t ->
                    val medallaColor = when (index) {
                        0 -> Color(0xFFFACC15) // Oro
                        1 -> Color(0xFF9CA3AF) // Plata
                        2 -> Color(0xFFB45309) // Bronce
                        else -> borde
                    }
                    val medallaEmoji = when (index) {
                        0 -> "🥇"
                        1 -> "🥈"
                        2 -> "🥉"
                        else -> null
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(if (index < 3) 2.dp else 1.dp, if (index < 3) medallaColor else borde),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVerPerfil(t.id) }
                    ) {
                        Row(
                            Modifier
                                .padding(14.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Posición / medalla
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .align(Alignment.Top)
                                    .border(2.dp, medallaColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (medallaEmoji != null) {
                                    Text(medallaEmoji, fontSize = 18.sp)
                                } else {
                                    Text(
                                        "${index + 1}",
                                        fontWeight = FontWeight.Bold,
                                        color = verde,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(Modifier.weight(1f)) {
                                Text(t.nombre, fontWeight = FontWeight.SemiBold)

                                // Especialidades como chips horizontales
                                if (t.especialidades.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        t.especialidades.take(4).forEach { esp ->
                                            Surface(
                                                shape = RoundedCornerShape(20.dp),
                                                color = verde.copy(alpha = 0.08f)
                                            ) {
                                                Text(
                                                    esp,
                                                    color = verde,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Ciudad
                                if (t.ciudad.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            null,
                                            tint = gris,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(t.ciudad, color = gris, fontSize = 11.sp)
                                    }
                                }

                                Spacer(Modifier.height(6.dp))

                                // Estrellas + rating
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilaEstrellas(t.rating)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "%.1f".format(t.rating),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${t.opiniones} opiniones · ${t.proyectosCompletados} proyectos",
                                    color = gris,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
