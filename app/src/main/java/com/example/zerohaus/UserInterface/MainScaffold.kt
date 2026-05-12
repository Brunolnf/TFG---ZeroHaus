package com.example.zerohaus.UserInterface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.*
import com.example.zerohaus.Util.LocalCadenas

@Composable
fun MainScaffold(
    panelViewModel: PanelViewModel, certificadoViewModel: CertificadoViewModel, chatViewModel: ChatViewModel,
    onCerrarSesion: () -> Unit = {}, onNuevoPreestudio: () -> Unit = {}, onBuscarTecnicos: () -> Unit = {},
    onMisProyectos: () -> Unit = {}, onRankings: () -> Unit = {}, onVerUltimoInforme: () -> Unit = {},
    onPerfil: () -> Unit = {}, onPresupuestos: () -> Unit = {}, onHistorialInformes: () -> Unit = {},
    onMisViviendas: () -> Unit = {}, onChats: (String) -> Unit = {}, onGraficas: () -> Unit = {},
    onMapaTecnicos: () -> Unit = {}, onSobreApp: () -> Unit = {}, onAjustes: () -> Unit = {}
) {
    val c = LocalCadenas.current
    val verde = MaterialTheme.colorScheme.primary
    val tabs = listOf(
        "inicio" to Icons.Default.Home,
        "mensajes" to Icons.Default.MailOutline,
        "explorar" to Icons.Default.Search,
        "mas" to Icons.Default.Menu
    )
    val tabLabels = listOf(c.tabInicio, c.tabMensajes, c.tabExplorar, c.tabMas)
    var actual by remember { mutableStateOf("inicio") }
    LaunchedEffect(Unit) { chatViewModel.cargarChats() }
    val noLeidos = chatViewModel.contarNoLeidos()

    Scaffold(bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEachIndexed { idx, (ruta, icono) ->
                NavigationBarItem(
                    selected = actual == ruta,
                    onClick = { actual = ruta; if (ruta == "mensajes") chatViewModel.cargarChats() },
                    icon = {
                        if (ruta == "mensajes" && noLeidos > 0) {
                            BadgedBox(badge = {
                                Badge(containerColor = Color(0xFFEF4444)) {
                                    Text("$noLeidos", color = Color.White, fontSize = 10.sp)
                                }
                            }) { Icon(icono, ruta) }
                        } else {
                            Icon(icono, ruta)
                        }
                    },
                    label = { Text(tabLabels[idx], fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = verde,
                        selectedTextColor = verde,
                        indicatorColor = verde.copy(0.12f)
                    )
                )
            }
        }
    }) { pv ->
        Box(Modifier.padding(pv)) {
            when (actual) {
                "inicio" -> PanelScreen(panelViewModel, certificadoViewModel, onNuevoPreestudio, onMisViviendas, onMisProyectos, onPresupuestos, onHistorialInformes, onGraficas, onVerUltimoInforme, onPerfil, onAjustes)
                "mensajes" -> ChatsListScreen(chatViewModel, { actual = "inicio" }, onChats)
                "explorar" -> PantallaExplorar(onBuscarTecnicos, onMapaTecnicos, onRankings)
                "mas" -> PantallaMas(onPerfil, onAjustes, onSobreApp, onCerrarSesion)
            }
        }
    }
}

@Composable
private fun PantallaExplorar(
    onBuscarTecnicos: () -> Unit, onMapaTecnicos: () -> Unit, onRankings: () -> Unit
) {
    val c = LocalCadenas.current
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(c.explorarTitulo, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(c.explorarSubtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Opc(Icons.Default.Place, Color(0xFF2563EB), c.explorarBuscarTecnicos, c.explorarBuscarTecnicosSub, onBuscarTecnicos)
        Opc(Icons.Default.LocationOn, Color(0xFFD97706), c.explorarMapaTecnicos, c.explorarMapaTecnicosSub, onMapaTecnicos)
        Opc(Icons.Default.Star, Color(0xFFEA580C), c.explorarRankings, c.explorarRankingsSub, onRankings)
    }
}

@Composable
private fun PantallaMas(
    onPerfil: () -> Unit, onAjustes: () -> Unit,
    onSobreApp: () -> Unit, onCerrarSesion: () -> Unit
) {
    val c = LocalCadenas.current
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(c.masTitulo, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(c.masSubtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Opc(Icons.Default.Person, Color(0xFF7C3AED), c.masPerfil, c.masPerfilSub, onPerfil)
        Opc(Icons.Default.Settings, MaterialTheme.colorScheme.onSurfaceVariant, c.masAjustes, c.masAjustesSub, onAjustes)
        Opc(Icons.Default.Info, MaterialTheme.colorScheme.onSurfaceVariant, c.masSobreApp, c.masSobreAppSub, onSobreApp)
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onCerrarSesion,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFDC2626))
            )
        ) {
            Icon(Icons.Default.ExitToApp, null, tint = Color(0xFFDC2626))
            Spacer(Modifier.width(8.dp))
            Text(c.cerrarSesion, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Opc(icono: ImageVector, color: Color, titulo: String, subtitulo: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icono, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
        }
    }
}
