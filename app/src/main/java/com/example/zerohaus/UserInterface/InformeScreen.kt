package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformeScreen(
    onVolver: () -> Unit = {},
    onDescargar: () -> Unit = {},
    onCompartir: () -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Último informe", fontWeight = FontWeight.SemiBold)
                        Text("Resumen de eficiencia energética", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onCompartir) {
                        Icon(Icons.Default.Share, contentDescription = null)
                    }
                    IconButton(onClick = onDescargar) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
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

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, borde),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Vivienda", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Mi vivienda principal", fontWeight = FontWeight.Medium)
                    Text("Generado: 02/02/2026", color = gris, fontSize = 12.sp)
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, borde),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Calificación energética", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Etiqueta", color = gris, fontSize = 12.sp)
                            Text("D", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text("Estado", color = gris, fontSize = 12.sp)
                            Text("Eficiencia media", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, borde),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Indicadores", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Consumo estimado", color = gris, fontSize = 12.sp)
                            Text("145 kWh/año", fontWeight = FontWeight.Medium)
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text("Emisiones", color = gris, fontSize = 12.sp)
                            Text("32 kg CO₂/año", fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Divider()
                    Spacer(Modifier.height(10.dp))

                    Text("Coste anual estimado", color = gris, fontSize = 12.sp)
                    Text("22 €/año", fontWeight = FontWeight.Medium)
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, borde),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Recomendaciones principales", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))

                    Text("• Mejorar aislamiento de ventanas", fontWeight = FontWeight.Medium)
                    Text("Ahorro estimado: 25%", color = gris, fontSize = 12.sp)

                    Spacer(Modifier.height(10.dp))

                    Text("• Instalar aerotermia", fontWeight = FontWeight.Medium)
                    Text("Ahorro estimado: 20%", color = gris, fontSize = 12.sp)

                    Spacer(Modifier.height(10.dp))

                    Text("• Sustituir iluminación por LED", fontWeight = FontWeight.Medium)
                    Text("Ahorro estimado: 10%", color = gris, fontSize = 12.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onCompartir,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, borde)
                ) {
                    Text("Compartir", color = Color(0xFF111827), fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onDescargar,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = verde),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text("Descargar", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
