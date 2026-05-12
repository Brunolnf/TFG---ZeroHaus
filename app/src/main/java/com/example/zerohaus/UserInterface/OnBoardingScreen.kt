
package com.example.zerohaus.UserInterface

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onCompletar: () -> Unit) {
    val verde = MaterialTheme.colorScheme.primary; val scope = rememberCoroutineScope()
    data class P(val ic: ImageVector, val cI: Color, val cF: Color, val t: String, val d: String)
    val pags = listOf(
        P(Icons.Default.Home, Color(0xFF059669), Color(0xFFD1FAE5), "Analiza tu vivienda", "Rellena los datos de tu hogar y obtén un informe energético con tu etiqueta, consumo estimado y recomendaciones personalizadas."),
        P(Icons.Default.Place, Color(0xFF2563EB), Color(0xFFDBEAFE), "Encuentra técnicos", "Busca profesionales certificados cerca de ti, consulta valoraciones reales y solicita presupuestos directamente desde la app."),
        P(Icons.Default.ShowChart, Color(0xFFDB2777), Color(0xFFFCE7F3), "Mejora y ahorra", "Compara informes, visualiza tu evolución con gráficas y gestiona proyectos de reforma para reducir tu consumo y emisiones.")
    )
    val pagerState = rememberPagerState(pageCount = { pags.size })

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { if (pagerState.currentPage < pags.size - 1) TextButton(onClick = onCompletar) { Text("Saltar", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val p = pags[page]; val vis = pagerState.currentPage == page
            val esc by animateFloatAsState(if (vis) 1f else 0.85f, tween(300), label = "e")
            Column(Modifier.fillMaxSize().scale(esc), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(120.dp).clip(RoundedCornerShape(30.dp)).background(p.cF), contentAlignment = Alignment.Center) { Icon(p.ic, null, tint = p.cI, modifier = Modifier.size(56.dp)) }
                Spacer(Modifier.height(40.dp)); Text(p.t, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp)); Text(p.d, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 24.sp, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 20.dp)) {
            repeat(pags.size) { i -> Box(Modifier.size(if (pagerState.currentPage == i) 28.dp else 8.dp, 8.dp).clip(CircleShape).background(if (pagerState.currentPage == i) verde else Color(0xFFD1D5DB))) }
        }
        Button(onClick = { if (pagerState.currentPage == pags.size - 1) onCompletar() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }, colors = ButtonDefaults.buttonColors(containerColor = verde), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 16.dp)) {
            Text(if (pagerState.currentPage == pags.size - 1) "Comenzar" else "Siguiente", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
}
