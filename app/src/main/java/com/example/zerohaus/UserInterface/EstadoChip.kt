package com.example.zerohaus.UserInterface

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding

/**
 * Chip informativo (no clickable). Sustituye a AssistChip cuando sólo se quiere
 * mostrar un estado coloreado, sin la apariencia de botón.
 */
@Composable
fun EstadoChip(
    texto: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: Int = 11
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Text(
            text = texto,
            color = color,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
