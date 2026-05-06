
package com.example.zerohaus.UserInterface

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

@Composable
fun ZeroHausLogo(size: Dp = 32.dp, color: Color = Color(0xFF16A34A)) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val verde = color
        val verdeClaro = Color(0xFF4ADE80)

        // Casa: tejado triangular
        val tejado = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.1f, h * 0.45f)
            lineTo(w * 0.9f, h * 0.45f)
            close()
        }
        drawPath(tejado, verde, style = Fill)

        // Casa: cuerpo
        drawRect(
            color = verde,
            topLeft = Offset(w * 0.18f, h * 0.45f),
            size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.47f)
        )

        // Hoja ecológica dentro de la casa (símbolo eco)
        val hoja = Path().apply {
            moveTo(w * 0.38f, h * 0.78f)
            cubicTo(w * 0.38f, h * 0.55f, w * 0.62f, h * 0.50f, w * 0.62f, h * 0.50f)
            cubicTo(w * 0.62f, h * 0.50f, w * 0.55f, h * 0.65f, w * 0.38f, h * 0.78f)
        }
        drawPath(hoja, verdeClaro, style = Fill)

        // Tallo de la hoja
        drawLine(
            color = verdeClaro,
            start = Offset(w * 0.38f, h * 0.78f),
            end = Offset(w * 0.50f, h * 0.60f),
            strokeWidth = w * 0.03f,
            cap = StrokeCap.Round
        )

        // Barra inferior (base "zero")
        drawRoundRect(
            color = verdeClaro,
            topLeft = Offset(w * 0.15f, h * 0.92f),
            size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.06f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.03f)
        )
    }
}
