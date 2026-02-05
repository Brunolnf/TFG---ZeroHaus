package com.example.zerohaus.UserInterface


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisProyectosScreen(onVolver: () -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis proyectos") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { pv ->
        Column(
            modifier = Modifier
                .padding(pv)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(2) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Proyecto ${it + 1}", fontWeight = FontWeight.SemiBold)
                        Text("Estado: En curso", fontSize = 12.sp, color = Color.Gray)
                        LinearProgressIndicator(progress = 0.6f, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformeScreen(
    onVolver: () -> Unit = {},
    onContactarTecnicos: () -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = { Text("Informe energético") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
            )
        }
    ) { pv ->
        Column(
            modifier = Modifier
                .padding(pv)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Calificación energética", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFFE5E7EB), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("D", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Eficiencia media", color = gris)
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Consumo estimado", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("145 kWh/año", fontWeight = FontWeight.Medium)
                    Text("Coste aproximado: 22€/año", color = gris, fontSize = 12.sp)
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Emisiones", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("32 kg CO₂/año", fontWeight = FontWeight.Medium)
                    Text("Impacto ambiental moderado", color = gris, fontSize = 12.sp)
                }
            }

            Text("Recomendaciones", fontWeight = FontWeight.SemiBold)

            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Mejora de aislamiento", fontWeight = FontWeight.Medium)
                    Text("Ahorro estimado: 25%", color = gris, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Instalación de aerotermia", fontWeight = FontWeight.Medium)
                    Text("Ahorro estimado: 20%", color = gris, fontSize = 12.sp)
                }
            }

            Button(
                onClick = onContactarTecnicos,
                colors = ButtonDefaults.buttonColors(containerColor = verde),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Contactar técnicos", color = Color.White)
            }

            Text(
                "Informe preliminar generado automáticamente",
                fontSize = 11.sp,
                color = gris,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}



