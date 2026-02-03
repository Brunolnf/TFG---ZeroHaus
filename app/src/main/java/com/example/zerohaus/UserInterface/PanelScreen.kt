package com.example.zerohaus.UserInterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*

@Composable
fun PanelScreen() {

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        item {
            Text("Bienvenido, Usuario", style = MaterialTheme.typography.headlineMedium)
            Text("Gestiona la eficiencia energética de tu hogar", color = Color.Gray)
        }

        item {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Mi vivienda principal")
                    Text("Consumo estimado: 145 kWh/año")
                    Text("Emisiones: 32 kg CO₂/año")
                    Text("Etiqueta energética: D")
                    Spacer(Modifier.height(8.dp))
                    Text("Ver último informe →", color = Color(0xFF00A63E))
                }
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Text("Acciones rápidas", style = MaterialTheme.typography.titleMedium)
        }

        items(
            listOf(
                "Nuevo preestudio",
                "Buscar técnicos",
                "Mis proyectos",
                "Rankings"
            )
        ) { accion ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(accion, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
