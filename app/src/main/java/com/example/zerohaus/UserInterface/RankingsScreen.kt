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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Modelo simple para cada técnico
data class RankingTecnicoUi(
    val nombre: String,
    val especialidad: String,
    val rating: Double,
    val opiniones: Int,
    val proyectos: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingsScreen(
    //  volver a la pantalla anterior
    onVolver: () -> Unit = {}
) {
    // Colores de la pantalla
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)
    val amarillo = Color(0xFFFFC107)

    // Lista de ejemplo para que aparezcan tecnicos
    val ranking = listOf(
        RankingTecnicoUi(
            nombre = "Juan Pérez – Técnico Certificado",
            especialidad = "Aerotermia · Fotovoltaica",
            rating = 4.9,
            opiniones = 203,
            proyectos = 142
        ),
        RankingTecnicoUi(
            nombre = "EcoReformas Madrid",
            especialidad = "Aislamiento · Ventanas",
            rating = 4.8,
            opiniones = 127,
            proyectos = 85
        ),
        RankingTecnicoUi(
            nombre = "SolarPro Consulting",
            especialidad = "Fotovoltaica · Auditorías",
            rating = 4.7,
            opiniones = 92,
            proyectos = 60
        ),
        RankingTecnicoUi(
            nombre = "GreenHouse Solutions",
            especialidad = "Biomasa · Rehabilitación",
            rating = 4.6,
            opiniones = 74,
            proyectos = 51
        )
    )

    // Scaffold para top bar y contenido con fondo
    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                // Título y subtítulo
                title = {
                    Column {
                        Text("Rankings", fontWeight = FontWeight.SemiBold)
                        Text("Mejores técnicos valorados", color = gris, fontSize = 12.sp)
                    }
                },
                // Botón de volver
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { pv ->

        // Lista vertical con separación entre tarjetas
        LazyColumn(
            modifier = Modifier
                .padding(pv)          // padding que deja libre la TopAppBar
                .fillMaxSize()
                .padding(16.dp),      // margen general
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // itemsIndexed para poder usar el índice de cada elemento es decir, enumerarlos
            itemsIndexed(ranking) { index, t ->

                // Tarjeta individual de cada técnico
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, borde),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        // Número de posición del ranking
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .align(Alignment.Top)
                                .border(1.dp, borde, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontWeight = FontWeight.Bold,
                                color = verde
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        // Columna con info: nombre, especialidad, rating y stats
                        Column(Modifier.weight(1f)) {
                            Text(t.nombre, fontWeight = FontWeight.SemiBold)
                            Text(t.especialidad, color = gris, fontSize = 12.sp)

                            Spacer(Modifier.height(6.dp))

                            // Fila con estrellas y rating numérico
                            Row(verticalAlignment = Alignment.CenterVertically) {

                                // Aquí pintas 5 estrellas siempre
                                // Mas adelante añadire la funcionalidad
                                // de que se pinten las que correspondan a la puntuacion
                                repeat(5) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = amarillo,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(Modifier.width(8.dp))
                                Text("${t.rating}", fontSize = 12.sp)
                            }

                            Spacer(Modifier.height(4.dp))

                            // Resumen de actividad
                            Text(
                                "${t.opiniones} opiniones · ${t.proyectos} proyectos",
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
@Preview@Composable
fun RankingsPreview() {
    RankingsScreen()
}

