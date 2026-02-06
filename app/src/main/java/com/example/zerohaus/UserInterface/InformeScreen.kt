package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformeScreen(
    //Lambdas para desplazarme entre las pantallas
    onVolver: () -> Unit = {},
    onDescargar: () -> Unit = {},
    onCompartir: () -> Unit = {},
    onContactarTecnicos: () -> Unit = {}
) {
    //Variables de colores
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)

    //Estructura de la pantalla
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onCompartir) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir")
                    }
                    IconButton(onClick = onDescargar) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Descargar")
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

            // Datos generales sobre la vivienda
            Card(
                shape = RoundedCornerShape(16.dp),
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

            // Calificación energética segun los campos que rellenes en los formularios
            Card(
                shape = RoundedCornerShape(16.dp),
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

            // Indicadores de eficiencia y emisiones
            Card(
                shape = RoundedCornerShape(16.dp),
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
                    Text("Coste anual estimado", color = gris, fontSize = 12.sp)
                    Text("22 €/año", fontWeight = FontWeight.Medium)
                }
            }

            // Recomendaciones para el usuario sobre la vivienda que no creo que tengan
            // ninguna funcionalidad nunca es meramente informativa
            Card(
                shape = RoundedCornerShape(16.dp),
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

            // Boton para ir a la pantalla tecnicos
            Button(
                onClick = onContactarTecnicos,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = verde),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.AccountBox, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Contactar técnicos", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Preview
@Composable
fun InformePreview() {
    InformeScreen()
}
