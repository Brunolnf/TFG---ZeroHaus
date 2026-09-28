package com.example.zerohaus.UserInterface

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.Util.rememberHayConexion
import kotlinx.coroutines.delay

/**
 * Envuelve toda la app y, sin conexión, muestra una franja arriba que
 * desplaza el contenido (no lo tapa). Espera un momento antes de mostrarla
 * para que un cambio de red (wifi → datos) no la haga parpadear.
 */
@Composable
fun ConAvisoSinConexion(contenido: @Composable () -> Unit) {
    val hayConexion by rememberHayConexion()
    var mostrar by remember { mutableStateOf(false) }
    LaunchedEffect(hayConexion) {
        if (hayConexion) mostrar = false
        else { delay(1_500); mostrar = true }
    }

    Column(Modifier.fillMaxSize()) {
        if (mostrar) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    Icon(
                        Icons.Default.CloudOff, null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        LocalCadenas.current.sinConexion,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
        // La franja ya ocupa la barra de estado: las pantallas no deben volver a reservarla
        Box(
            Modifier
                .weight(1f)
                .then(if (mostrar) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)
        ) {
            contenido()
        }
    }
}
