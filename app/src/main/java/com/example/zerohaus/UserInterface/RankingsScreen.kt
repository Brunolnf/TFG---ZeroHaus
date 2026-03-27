package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    onVolver: () -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)
    val amarillo = Color(0xFFFFC107)

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
                    IconButton(onClick = onVolver) { Icon(Icons.Default.ArrowBack, "Volver") }
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
                Text("No hay técnicos registrados aún", color = gris)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(pv).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(viewModel.ranking) { index, t ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, borde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(38.dp).align(Alignment.Top).border(1.dp, borde, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${index + 1}", fontWeight = FontWeight.Bold, color = verde)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.nombre, fontWeight = FontWeight.SemiBold)
                                Text(t.especialidades.joinToString(" · "), color = gris, fontSize = 12.sp)
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    repeat(5) { i ->
                                        Icon(
                                            Icons.Default.Star, null,
                                            tint = if (t.rating >= (i + 1) - 0.01) amarillo else Color(0xFFBDBDBD),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text("${t.rating}", fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("${t.opiniones} opiniones · ${t.proyectosCompletados} proyectos", color = gris, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}