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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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

/**
 * Logo ZeroHaus profesional.
 *
 * Construcción geométrica:
 *  - Casa = tejado a dos aguas + cuerpo, dibujado como UNA sola silueta cerrada,
 *    con esquinas inferiores redondeadas para anclar visualmente la forma.
 *  - Gradiente sutil de 2 stops (verde medio → verde oscuro) para dar
 *    profundidad sin saturar.
 *  - Hoja: forma botánica con punta superior e inferior (no teardrop), colocada
 *    como "puerta" en el centro inferior — integrada, no flotando.
 *  - Una sola nervadura central en la hoja, trazo limpio.
 *
 *  Filosofía: cero ruido visual. Sin destellos, sombras ni nervaduras laterales.
 */
@Composable
fun ZeroHausLogo(size: Dp = 32.dp, color: Color = Color(0xFF16A34A)) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val verdeOscuro = Color(0xFF065F46)
        val verdeMedio = color
        val verdeHoja = Color(0xFF86EFAC)

        // ─── 1) Silueta de la casa: tejado a dos aguas + cuerpo + esquinas inferiores redondeadas ───
        val casa = Path().apply {
            moveTo(w * 0.50f, h * 0.08f)                                            // cima del tejado
            lineTo(w * 0.92f, h * 0.42f)                                            // alero derecho
            lineTo(w * 0.92f, h * 0.85f)                                            // bajada por la derecha
            quadraticBezierTo(w * 0.92f, h * 0.92f, w * 0.85f, h * 0.92f)            // esquina inf-der redondeada
            lineTo(w * 0.15f, h * 0.92f)                                            // base de la casa
            quadraticBezierTo(w * 0.08f, h * 0.92f, w * 0.08f, h * 0.85f)            // esquina inf-izq redondeada
            lineTo(w * 0.08f, h * 0.42f)                                            // subida por la izquierda
            close()                                                                  // diagonal del tejado izquierdo
        }
        val gradiente = Brush.linearGradient(
            colors = listOf(verdeMedio, verdeOscuro),
            start = Offset(w * 0.20f, h * 0.10f),
            end = Offset(w * 0.80f, h * 0.95f)
        )
        drawPath(casa, brush = gradiente, style = Fill)

        // ─── 2) Hoja como "puerta" central: forma botánica con doble punta ───
        val hoja = Path().apply {
            moveTo(w * 0.50f, h * 0.46f)                                  // punta superior
            cubicTo(
                w * 0.74f, h * 0.55f,
                w * 0.74f, h * 0.80f,
                w * 0.50f, h * 0.89f                                       // base
            )
            cubicTo(
                w * 0.26f, h * 0.80f,
                w * 0.26f, h * 0.55f,
                w * 0.50f, h * 0.46f                                       // vuelta a la punta
            )
            close()
        }
        drawPath(hoja, color = verdeHoja, style = Fill)

        // ─── 3) Nervadura central de la hoja, un solo trazo ───
        val nervadura = Path().apply {
            moveTo(w * 0.50f, h * 0.49f)
            quadraticBezierTo(w * 0.49f, h * 0.68f, w * 0.50f, h * 0.86f)
        }
        drawPath(
            nervadura,
            color = verdeOscuro,
            style = Stroke(
                width = w * 0.018f,
                cap = StrokeCap.Round
            )
        )
    }
}
