package com.example.zerohaus.UserInterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Util.LocalCadenas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SobreAppScreen(onVolver: () -> Unit = {}) {
    val c = LocalCadenas.current
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(c.sobreTitulo, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, c.volver) } }
            )
        }
    ) { pv ->
        Column(
            Modifier
                .padding(pv)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            ZeroHausLogo(size = 64.dp)
            Text("ZeroHaus", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = verde)
            Text(c.sobreVersion, color = gris, fontSize = 13.sp)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(c.sobreQueEs, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(c.sobreDescripcion, color = gris, fontSize = 14.sp, lineHeight = 22.sp)
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(c.sobreFuncionalidades, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    listOf(
                        c.sobreFuncPreestudios,
                        c.sobreFuncViviendas,
                        c.sobreFuncBusqueda,
                        c.sobreFuncChat,
                        c.sobreFuncPresupuestos,
                        c.sobreFuncGraficas,
                        c.sobreFuncValoraciones
                    ).forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = verde, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(t, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        }
                    }
                }
            }

            Text(c.sobreCopyright, color = gris, fontSize = 12.sp)

            Spacer(Modifier.height(20.dp))
        }
    }
}
