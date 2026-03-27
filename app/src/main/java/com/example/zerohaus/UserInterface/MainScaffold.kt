
package com.example.zerohaus.UserInterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.*

@Composable
fun MainScaffold(
    panelViewModel: PanelViewModel, certificadoViewModel: CertificadoViewModel, chatViewModel: ChatViewModel,
    onCerrarSesion: () -> Unit = {}, onNuevoPreestudio: () -> Unit = {}, onBuscarTecnicos: () -> Unit = {},
    onMisProyectos: () -> Unit = {}, onRankings: () -> Unit = {}, onVerUltimoInforme: () -> Unit = {},
    onPerfil: () -> Unit = {}, onPresupuestos: () -> Unit = {}, onHistorialInformes: () -> Unit = {},
    onMisViviendas: () -> Unit = {}, onChats: (String) -> Unit = {}, onGraficas: () -> Unit = {},
    onMapaTecnicos: () -> Unit = {}, onSobreApp: () -> Unit = {}, onAjustes: () -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val tabs = listOf("inicio" to Icons.Default.Home, "mensajes" to Icons.Default.MailOutline, "explorar" to Icons.Default.Search, "mas" to Icons.Default.Menu)
    var actual by remember { mutableStateOf("inicio") }
    LaunchedEffect(Unit) { chatViewModel.cargarChats() }
    val noLeidos = chatViewModel.contarNoLeidos()

    Scaffold(bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEach { (ruta, icono) ->
                NavigationBarItem(selected = actual == ruta, onClick = { actual = ruta; if (ruta == "mensajes") chatViewModel.cargarChats() },
                    icon = { if (ruta == "mensajes" && noLeidos > 0) BadgedBox(badge = { Badge(containerColor = Color(0xFFEF4444)) { Text("$noLeidos", color = Color.White, fontSize = 10.sp) } }) { Icon(icono, ruta) } else Icon(icono, ruta) },
                    label = { Text(ruta.replaceFirstChar { it.uppercase() }, fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = verde, selectedTextColor = verde, indicatorColor = verde.copy(0.12f)))
            }
        }
    }) { pv ->
        Box(Modifier.padding(pv)) {
            when (actual) {
                "inicio" -> PanelScreen(panelViewModel, certificadoViewModel, onCerrarSesion, onNuevoPreestudio, onBuscarTecnicos, onMisProyectos, onRankings, onVerUltimoInforme, onPerfil, onPresupuestos, onHistorialInformes, onMisViviendas, { actual = "mensajes" }, onGraficas, onMapaTecnicos, onAjustes)
                "mensajes" -> ChatsListScreen(chatViewModel, { actual = "inicio" }, onChats)
                "explorar" -> PantallaExplorar(onBuscarTecnicos, onMapaTecnicos, onRankings, onGraficas, onHistorialInformes)
                "mas" -> PantallaMas(onPerfil, onMisViviendas, onPresupuestos, onMisProyectos, onAjustes, onSobreApp, onCerrarSesion)
            }
        }
    }
}

@Composable
private fun PantallaExplorar(onBuscarTecnicos: () -> Unit, onMapaTecnicos: () -> Unit, onRankings: () -> Unit, onGraficas: () -> Unit, onHistorialInformes: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp)); Text("Explorar", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground); Text("Descubre herramientas y datos", color = Color(0xFF6B7280), fontSize = 15.sp); Spacer(Modifier.height(4.dp))
        Opc(Icons.Default.Place, Color(0xFF2563EB), "Buscar técnicos", "Lista completa", onBuscarTecnicos)
        Opc(Icons.Default.LocationOn, Color(0xFFD97706), "Mapa de técnicos", "Ver en mapa", onMapaTecnicos)
        Opc(Icons.Default.Star, Color(0xFFEA580C), "Rankings", "Mejores técnicos", onRankings)
        Opc(Icons.Default.ShowChart, Color(0xFFDB2777), "Gráficas de consumo", "Evolución energética", onGraficas)
        Opc(Icons.Default.Assessment, Color(0xFF059669), "Historial de informes", "Compara informes", onHistorialInformes)
    }
}

@Composable
private fun PantallaMas(onPerfil: () -> Unit, onMisViviendas: () -> Unit, onPresupuestos: () -> Unit, onMisProyectos: () -> Unit, onAjustes: () -> Unit, onSobreApp: () -> Unit, onCerrarSesion: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp)); Text("Más opciones", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground); Text("Configuración y gestión", color = Color(0xFF6B7280), fontSize = 15.sp); Spacer(Modifier.height(4.dp))
        Opc(Icons.Default.Person, Color(0xFF7C3AED), "Mi perfil", "Edita tu información", onPerfil)
        Opc(Icons.Default.Home, Color(0xFF0284C7), "Mis viviendas", "Gestiona viviendas", onMisViviendas)
        Opc(Icons.Default.Description, Color(0xFF2563EB), "Presupuestos", "Solicitudes y respuestas", onPresupuestos)
        Opc(Icons.Default.Menu, Color(0xFF7C3AED), "Mis proyectos", "Reformas en curso", onMisProyectos)
        Opc(Icons.Default.Settings, Color(0xFF6B7280), "Ajustes", "Notificaciones y preferencias", onAjustes)
        Opc(Icons.Default.Info, Color(0xFF6B7280), "Sobre ZeroHaus", "Versión e información", onSobreApp)
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onCerrarSesion, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFDC2626)))) {
            Icon(Icons.Default.ExitToApp, null, tint = Color(0xFFDC2626)); Spacer(Modifier.width(8.dp)); Text("Cerrar sesión", color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Opc(icono: ImageVector, color: Color, titulo: String, subtitulo: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(icono, null, tint = color, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface); Text(subtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}