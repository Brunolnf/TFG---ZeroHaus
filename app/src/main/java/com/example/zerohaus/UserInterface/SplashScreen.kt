
package com.example.zerohaus.UserInterface

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Util.LocalCadenas
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTerminado: () -> Unit) {
    val c = LocalCadenas.current
    var iniciar by remember { mutableStateOf(false) }
    var saltado by remember { mutableStateOf(false) }
    val escala by animateFloatAsState(targetValue = if (iniciar) 1f else 0.4f, animationSpec = tween(700, easing = EaseOutBack), label = "e")
    val opacidad by animateFloatAsState(targetValue = if (iniciar) 1f else 0f, animationSpec = tween(600), label = "o")
    val opTexto by animateFloatAsState(targetValue = if (iniciar) 1f else 0f, animationSpec = tween(500, delayMillis = 400), label = "ot")

    LaunchedEffect(Unit) {
        iniciar = true
        delay(2200)
        if (!saltado) onTerminado()
    }

    // Branding oficial: fondo blanco off (#FCFCFB) idéntico al del logo del usuario.
    val brandFondo = Color(0xFFFCFCFB)
    val brandTexto = Color(0xFF3E3E3E)
    val brandLima = Color(0xFF9FBA42)

    Box(
        modifier = Modifier.fillMaxSize().background(brandFondo)
    ) {
        // Botón Saltar
        TextButton(
            onClick = {
                if (!saltado) {
                    saltado = true
                    onTerminado()
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 12.dp, top = 8.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = brandLima)
        ) {
            Text("Saltar", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        // Contenido central — logo + wordmark + slogan, paleta del branding oficial
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .scale(escala)
                .alpha(opacidad)
        ) {
            ZeroHausLogo(size = 140.dp)
            Spacer(Modifier.height(8.dp))
            Text("ZeroHaus", color = brandTexto, fontWeight = FontWeight.Bold, fontSize = 36.sp, modifier = Modifier.alpha(opTexto))
            Spacer(Modifier.height(4.dp))
            Text(c.splashSlogan, color = brandLima, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.alpha(opTexto))
        }
    }
}
