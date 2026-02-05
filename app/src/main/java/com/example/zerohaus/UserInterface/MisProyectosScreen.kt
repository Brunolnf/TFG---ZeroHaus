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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ProyectoUi(
    val id: String,
    val titulo: String,
    val ubicacion: String,
    val tecnico: String,
    val progreso: Int,
    val estado: String,
    val tareas: List<Pair<String, Boolean>>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisProyectosScreen(
    onVolver: () -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)

    val proyectos = listOf(
        ProyectoUi(
            id = "1",
            titulo = "Instalación aerotermia",
            ubicacion = "Mi vivienda principal",
            tecnico = "Juan Pérez",
            progreso = 65,
            estado = "En curso",
            tareas = listOf(
                "Instalación equipo" to true,
                "Conexiones" to true,
                "Pruebas" to false
            )
        ),
        ProyectoUi(
            id = "2",
            titulo = "Cambio de ventanas",
            ubicacion = "Mi vivienda principal",
            tecnico = "EcoReformas Madrid",
            progreso = 100,
            estado = "Finalizado",
            tareas = listOf(
                "Medición" to true,
                "Instalación" to true
            )
        )
    )

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mis proyectos", fontWeight = FontWeight.SemiBold)
                        Text("${proyectos.size} proyectos", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { pv ->
        LazyColumn(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(proyectos) { p ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, borde),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(p.titulo, fontWeight = FontWeight.SemiBold)
                        Text(p.ubicacion, color = gris, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Técnico: ${p.tecnico}", color = gris, fontSize = 12.sp)

                        Spacer(Modifier.height(6.dp))
                        Text("Progreso: ${p.progreso}% · ${p.estado}", color = gris, fontSize = 12.sp)

                        Spacer(Modifier.height(10.dp))
                        Text("Tareas", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))

                        p.tareas.forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (t.second) Icons.Default.CheckCircle else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (t.second) verde else Color(0xFFBDBDBD)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(t.first, fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}
