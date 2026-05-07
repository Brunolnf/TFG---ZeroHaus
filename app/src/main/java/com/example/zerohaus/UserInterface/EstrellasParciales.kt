package com.example.zerohaus.UserInterface

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun FilaEstrellas(
    rating: Double,
    tamano: Dp = 18.dp,
    colorLlena: Color = Color(0xFFFFC107),
    colorVacia: Color = Color(0xFFBDBDBD)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            val fraccion = (rating - i).coerceIn(0.0, 1.0).toFloat()
            EstrellaParcial(fraccion, tamano, colorLlena, colorVacia)
        }
    }
}

@Composable
private fun EstrellaParcial(fraccion: Float, tamano: Dp, colorLlena: Color, colorVacia: Color) {
    Box(Modifier.size(tamano)) {
        // Estrella gris de fondo (siempre completa)
        Icon(Icons.Default.Star, contentDescription = null, tint = colorVacia, modifier = Modifier.size(tamano))
        // Estrella amarilla recortada al % exacto de la izquierda
        if (fraccion > 0f) {
            Box(Modifier.size(tamano).clip(RecorteIzquierda(fraccion))) {
                Icon(Icons.Default.Star, contentDescription = null, tint = colorLlena, modifier = Modifier.size(tamano))
            }
        }
    }
}

private class RecorteIzquierda(private val fraccion: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rectangle(Rect(0f, 0f, size.width * fraccion, size.height))
}
