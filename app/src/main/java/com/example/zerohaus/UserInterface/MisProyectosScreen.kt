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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.ProyectosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisProyectosScreen(
    viewModel: ProyectosViewModel,
    onVolver: () -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)

    LaunchedEffect(Unit) { viewModel.cargarProyectos() }

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mis proyectos", fontWeight = FontWeight.SemiBold)
                        Text("${viewModel.proyectos.size} proyectos", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                }
            )
        }
    ) { pv ->
        if (viewModel.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = verde)
            }
        } else if (viewModel.proyectos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pv), contentAlignment = Alignment.Center) {
                Text("No tienes proyectos aún", color = gris)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(pv).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(viewModel.proyectos) { p ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, borde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(p.titulo, fontWeight = FontWeight.SemiBold)
                            Text(p.viviendaNombre, color = gris, fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("Técnico: ${p.tecnicoNombre}", color = gris, fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))

                            // Barra de progreso real
                            LinearProgressIndicator(
                                progress = { p.progreso / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = verde,
                                trackColor = Color(0xFFE5E7EB)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("${p.progreso}% · ${p.estado}", color = gris, fontSize = 12.sp)

                            Spacer(Modifier.height(10.dp))
                            Text("Tareas", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))

                            p.tareas.forEachIndexed { index, t ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = t.completada,
                                        onCheckedChange = { checked ->
                                            viewModel.toggleTarea(p.id, index, checked)
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = verde)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(t.nombre, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}