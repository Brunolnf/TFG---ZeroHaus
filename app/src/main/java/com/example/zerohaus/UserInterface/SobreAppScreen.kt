
package com.example.zerohaus.UserInterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SobreAppScreen(onVolver: () -> Unit = {}) {
    val verde = Color(0xFF16A34A); val gris = Color(0xFF6B7280)
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text("Sobre ZeroHaus", fontWeight = FontWeight.SemiBold) }, navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } })
    }) { pv ->
        Column(Modifier.padding(pv).fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(12.dp))
            ZeroHausLogo(size = 64.dp)
            Text("ZeroHaus", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = verde); Text("Versión 1.0.0", color = gris, fontSize = 13.sp)
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("¿Qué es ZeroHaus?", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("ZeroHaus te ayuda a mejorar la eficiencia energética de tu hogar. Analiza tu vivienda, genera informes personalizados y te conecta con técnicos certificados.", color = gris, fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Funcionalidades", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    listOf("Preestudios energéticos", "Gestión de viviendas", "Búsqueda y mapa de técnicos", "Chat en tiempo real", "Sistema de presupuestos", "Gráficas de evolución", "Valoraciones y rankings").forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = verde, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(t, color = Color(0xFF374151), fontSize = 14.sp) }
                    }
                }
            }
            Text("© 2026 ZeroHaus. Todos los derechos reservados.", color = gris, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))
        }
    }
}
