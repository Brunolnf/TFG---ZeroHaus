
package com.example.zerohaus.UserInterface

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTerminado: () -> Unit) {
    var iniciar by remember { mutableStateOf(false) }
    val escala by animateFloatAsState(targetValue = if (iniciar) 1f else 0.4f, animationSpec = tween(700, easing = EaseOutBack), label = "e")
    val opacidad by animateFloatAsState(targetValue = if (iniciar) 1f else 0f, animationSpec = tween(600), label = "o")
    val opTexto by animateFloatAsState(targetValue = if (iniciar) 1f else 0f, animationSpec = tween(500, delayMillis = 400), label = "ot")

    LaunchedEffect(Unit) { iniciar = true; delay(2200); onTerminado() }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF16A34A), Color(0xFF059669), Color(0xFF047857)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(escala).alpha(opacidad)) {
            Box(
                modifier = Modifier.size(100.dp).background(Color.White.copy(0.15f), RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center
            ) {
                ZeroHausLogo(size = 64.dp, color = Color.White)
            }
            Spacer(Modifier.height(20.dp))
            Text("ZeroHaus", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp, modifier = Modifier.alpha(opTexto))
            Spacer(Modifier.height(6.dp))
            Text("Eficiencia energética inteligente", color = Color.White.copy(0.75f), fontSize = 14.sp, modifier = Modifier.alpha(opTexto))
        }
    }
}
