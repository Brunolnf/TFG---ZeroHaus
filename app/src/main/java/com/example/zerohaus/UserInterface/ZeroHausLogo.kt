package com.example.zerohaus.UserInterface

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.R

fun colorEtiqueta(etiqueta: String): Color = when (etiqueta) {
    "A" -> Color(0xFF15803D)
    "B" -> Color(0xFF22C55E)
    "C" -> Color(0xFF84CC16)
    "D" -> Color(0xFFEAB308)
    "E" -> Color(0xFFF97316)
    "F" -> Color(0xFFEF4444)
    else -> Color(0xFFDC2626)
}

fun fondoEtiqueta(etiqueta: String): Color = when (etiqueta) {
    "A" -> Color(0xFFDCFCE7)
    "B" -> Color(0xFFDCFCE7)
    "C" -> Color(0xFFECFCCB)
    "D" -> Color(0xFFFEF9C3)
    "E" -> Color(0xFFFFEDD5)
    "F" -> Color(0xFFFEE2E2)
    else -> Color(0xFFFEE2E2)
}

@Composable
fun EtiquetaBadge(etiqueta: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(fondoEtiqueta(etiqueta))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            etiqueta,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            color = colorEtiqueta(etiqueta)
        )
    }
}

/**
 * Logo oficial ZeroHaus: casa con dos hojas integradas, en verde lima.
 *
 * Se renderiza desde el recurso `drawable/zerohaus_logo.webp`, generado a
 * partir del logo original del usuario (Vista Logos). El parámetro `color`
 * se mantiene en la firma por compatibilidad con los call sites existentes,
 * pero ya no se usa: el logo viene con su propio color de marca.
 */
@Composable
fun ZeroHausLogo(
    size: Dp = 32.dp,
    @Suppress("UNUSED_PARAMETER") color: Color = Color(0xFF16A34A)
) {
    Image(
        painter = painterResource(id = R.drawable.zerohaus_logo),
        contentDescription = "ZeroHaus",
        modifier = Modifier.size(size)
    )
}
