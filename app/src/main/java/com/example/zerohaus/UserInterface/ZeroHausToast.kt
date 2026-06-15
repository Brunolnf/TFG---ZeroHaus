package com.example.zerohaus.UserInterface

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class ToastTipo { EXITO, ERROR, INFO }

/**
 * Toast unificado de ZeroHaus. Muestra una notificación flotante en la parte inferior
 * de la pantalla con el estilo de la app. Se oculta automáticamente tras [duracionMs].
 *
 * Uso:
 * ```kotlin
 * Box(Modifier.fillMaxSize()) {
 *     // Contenido de la pantalla
 *     Box(Modifier.align(Alignment.BottomCenter)) {
 *         ZeroToast(
 *             mensaje = estado.mensaje ?: estado.error,
 *             tipo    = if (estado.error != null) ToastTipo.ERROR else ToastTipo.EXITO,
 *             alOcultar = { viewModel.limpiarMensaje() }
 *         )
 *     }
 * }
 * ```
 */
@Composable
fun ZeroToast(
    mensaje: String?,
    tipo: ToastTipo = ToastTipo.EXITO,
    duracionMs: Long = 2800L,
    alOcultar: () -> Unit
) {
    LaunchedEffect(mensaje) {
        if (mensaje != null) {
            delay(duracionMs)
            alOcultar()
        }
    }

    AnimatedVisibility(
        visible = mensaje != null,
        enter  = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit   = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
    ) {
        if (mensaje != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .background(
                        color = when (tipo) {
                            ToastTipo.EXITO -> Color(0xFF166534)
                            ToastTipo.ERROR -> Color(0xFF991B1B)
                            ToastTipo.INFO  -> Color(0xFF1E3A5F)
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = when (tipo) {
                        ToastTipo.EXITO -> Icons.Default.CheckCircle
                        ToastTipo.ERROR -> Icons.Default.Cancel
                        ToastTipo.INFO  -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = mensaje,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
