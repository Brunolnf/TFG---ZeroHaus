package com.example.zerohaus.UserInterface

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Componentes de "deleite": microanimaciones que hacen que la app se sienta
 * cuidada y cara. Reutilizables en cualquier pantalla.
 */

/** Rectángulo con brillo animado (placeholder de carga estilo Facebook/YouTube). */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val transicion = rememberInfiniteTransition(label = "shimmer")
    val x by transicion.animateFloat(
        initialValue = -400f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmerX"
    )
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val brillo = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val brush = Brush.linearGradient(
        colors = listOf(base, brillo, base),
        start = Offset(x, 0f),
        end = Offset(x + 400f, 0f)
    )
    Box(modifier.clip(shape).background(brush))
}

/** Esqueleto del panel de inicio mientras cargan los datos. */
@Composable
fun PanelSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShimmerBox(Modifier.size(44.dp), shape = CircleShape)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShimmerBox(Modifier.size(width = 150.dp, height = 18.dp))
                ShimmerBox(Modifier.size(width = 200.dp, height = 13.dp))
            }
        }
        ShimmerBox(Modifier.fillMaxWidth().height(196.dp), shape = RoundedCornerShape(20.dp))
        ShimmerBox(Modifier.size(width = 160.dp, height = 18.dp))
        repeat(5) {
            ShimmerBox(Modifier.fillMaxWidth().height(76.dp), shape = RoundedCornerShape(16.dp))
        }
    }
}

/**
 * Número que sube desde 0 hasta su valor al aparecer (count-up). Cada frame se
 * vuelve a formatear con [formato], así respeta unidades/moneda del usuario.
 */
@Composable
fun ContadorAnimado(
    valor: Double,
    formato: (Double) -> String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    var lanzar by remember { mutableStateOf(false) }
    val animado by animateFloatAsState(
        targetValue = if (lanzar) valor.toFloat() else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "contador"
    )
    LaunchedEffectUnaVez { lanzar = true }
    Text(
        text = formato(animado.toDouble()),
        color = color,
        fontWeight = fontWeight,
        fontSize = fontSize,
        modifier = modifier
    )
}

/** Dispara [accion] una sola vez cuando el composable entra en pantalla. */
@Composable
private fun LaunchedEffectUnaVez(accion: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { accion() }
}
