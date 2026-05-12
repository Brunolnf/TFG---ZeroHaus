package com.example.zerohaus.UserInterface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.*

@Composable
fun MainScaffoldTecnico(
    panelTecnicoVM: PanelTecnicoViewModel,
    panelVM: PanelViewModel,
    certificadoVM: CertificadoViewModel,
    presupuestosVM: PresupuestosViewModel,
    chatVM: ChatViewModel,
    onCerrarSesion: () -> Unit = {},
    onPerfil: () -> Unit = {},
    onAjustes: () -> Unit = {},
    onSobreApp: () -> Unit = {},
    onChats: (String) -> Unit = {},
    onProyectos: () -> Unit = {},
    onResenas: () -> Unit = {},
    onEstadisticas: () -> Unit = {},
    onMisClientes: () -> Unit = {}
) {
    val verde = MaterialTheme.colorScheme.primary
    val tabs = listOf(
        "inicio" to Icons.Default.Home,
        "mensajes" to Icons.Default.MailOutline,
        "solicitudes" to Icons.Default.Description,
        "mas" to Icons.Default.Menu
    )
    val tabLabels = listOf("Inicio", "Mensajes", "Solicitudes", "Más")
    var actual by remember { mutableStateOf("inicio") }
    LaunchedEffect(Unit) { chatVM.cargarChats() }
    val noLeidos = chatVM.contarNoLeidos()

    Scaffold(bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEachIndexed { idx, (ruta, icono) ->
                NavigationBarItem(
                    selected = actual == ruta,
                    onClick = {
                        actual = ruta
                        if (ruta == "mensajes") chatVM.cargarChats()
                        if (ruta == "inicio") panelTecnicoVM.cargar()
                        if (ruta == "solicitudes") presupuestosVM.cargarMisSolicitudes()
                    },
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
                "inicio" -> PanelTecnicoScreen(
                    viewModel = panelTecnicoVM,
                    panelViewModel = panelVM,
                    certificadoViewModel = certificadoVM,
                    onAjustes = onAjustes,
                    onProyectos = onProyectos,
                    onResenas = onResenas,
                    onEstadisticas = onEstadisticas
                )
                "mensajes" -> ChatsListScreen(
                    viewModel = chatVM,
                    onVolver = { actual = "inicio" },
                    onAbrirChat = onChats
                )
                "solicitudes" -> PresupuestosScreen(
                    viewModel = presupuestosVM,
                    onVolver = { actual = "inicio" },
                    esTecnico = true
                )
                "mas" -> PantallaMasTecnico(
                    onPerfil = onPerfil,
                    onAjustes = onAjustes,
                    onSobreApp = onSobreApp,
                    onCerrarSesion = onCerrarSesion,
                    onMisClientes = onMisClientes
                )
            }
        }
    }
}

@Composable
private fun PantallaMasTecnico(
    onPerfil: () -> Unit,
    onAjustes: () -> Unit,
    onSobreApp: () -> Unit,
    onCerrarSesion: () -> Unit,
    onMisClientes: () -> Unit = {}
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Más", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
        Text("Tu cuenta", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))

        OpcTecnico(Icons.Default.Person, Color(0xFF7C3AED), "Perfil", "Tu cuenta en ZeroHaus", onPerfil)
        OpcTecnico(Icons.Default.People, MaterialTheme.colorScheme.primary, "Mis clientes", "Clientes con quienes trabajas", onMisClientes)
        OpcTecnico(Icons.Default.Settings, MaterialTheme.colorScheme.onSurfaceVariant, "Ajustes", "Preferencias de la app", onAjustes)
        OpcTecnico(Icons.Default.Info, MaterialTheme.colorScheme.onSurfaceVariant, "Sobre la app", "Información y versión", onSobreApp)

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCerrarSesion,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color(0xFFDC2626)))
        ) {
            Icon(Icons.Default.ExitToApp, null, tint = Color(0xFFDC2626))
            Spacer(Modifier.width(8.dp))
            Text("Cerrar sesión", color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun OpcTecnico(
    icono: ImageVector,
    color: Color,
    titulo: String,
    subtitulo: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.12f)),
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
