package com.zerohaus.ui.pantallas.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NotificacionUi(
    val titulo: String,
    val detalle: String,
    val fecha: String,
    val noLeida: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelScreen(
    onCerrarSesion: () -> Unit = {},
    onNuevoPreestudio: () -> Unit = {},
    onBuscarTecnicos: () -> Unit = {},
    onMisProyectos: () -> Unit = {},
    onRankings: () -> Unit = {},
    onVerUltimoInforme: () -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val grisTexto = Color(0xFF6B7280)
    val bordeSuave = Color(0xFFE5E7EB)
    val fondo = Color(0xFFF6F7F9)

    var mostrarConfig by remember { mutableStateOf(false) }
    var mostrarNotificaciones by remember { mutableStateOf(false) }
    var mostrarSubirCertificado by remember { mutableStateOf(false) }

    var modoOscuro by remember { mutableStateOf(false) }
    var idioma by remember { mutableStateOf("Español") }
    var sonidos by remember { mutableStateOf(true) }
    var push by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf(false) }
    var generales by remember { mutableStateOf(true) }
    var abrirIdioma by remember { mutableStateOf(false) }

    var nombreCert by remember { mutableStateOf("") }
    var tipoCert by remember { mutableStateOf("Selecciona un tipo") }
    var abrirTipoCert by remember { mutableStateOf(false) }

    val notificaciones = remember {
        mutableStateListOf(
            NotificacionUi("Nuevo presupuesto recibido","EcoReformas Madrid ha enviado un presupuesto.","Hoy",true),
            NotificacionUi("Actualización de proyecto","El proyecto 'Aerotermia' pasó a 'En curso'.","Ayer",false),
            NotificacionUi("Nueva valoración","Has recibido una respuesta a tu reseña.","Hace 3 días",false)
        )
    }
    val hayNoLeidas = notificaciones.any { it.noLeida }

    Scaffold(
        containerColor = fondo,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(verde),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Home,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("ZeroHaus", color = verde, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        }
                        Text("Usuario Demo", color = grisTexto, fontSize = 16.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { mostrarSubirCertificado = true }) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                    Box {
                        IconButton(onClick = { mostrarNotificaciones = true }) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                        if (hayNoLeidas) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-8).dp, y = 8.dp)
                            )
                        }
                    }
                    IconButton(onClick = { mostrarConfig = true }) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = onCerrarSesion) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                }
            )
        }
    ) { pv ->
        LazyColumn(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            item {
                Spacer(Modifier.height(10.dp))
                Text("Bienvenido, Usuario", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text("Gestiona la eficiencia energética de tu hogar", color = grisTexto, fontSize = 16.sp)
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = verde),
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(22.dp)) {
                        Text("Tu vivienda", color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Mi vivienda principal",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("D", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Consumo estimado", color = Color.White.copy(0.85f), fontSize = 15.sp)
                                Text("145 kWh/año", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Emisiones", color = Color.White.copy(0.85f), fontSize = 15.sp)
                                Text("32 kg CO₂/año", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Button(
                            onClick = onVerUltimoInforme,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.18f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Text(
                                "Ver último informe →",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            item {
                Text("Acciones", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            }

            item {
                Tarjeta(Icons.Default.Add, Color(0xFFD1FAE5), Color(0xFF059669), "Nuevo preestudio", "Analiza tu vivienda", bordeSuave, onNuevoPreestudio)
            }
            item {
                Tarjeta(Icons.Default.Place, Color(0xFFDBEAFE), Color(0xFF2563EB), "Buscar técnicos", "Encuentra profesionales", bordeSuave, onBuscarTecnicos)
            }
            item {
                Tarjeta(Icons.Default.Menu, Color(0xFFEDE9FE), Color(0xFF7C3AED), "Mis proyectos", "Gestiona reformas", bordeSuave, onMisProyectos)
            }
            item {
                Tarjeta(Icons.Default.Star, Color(0xFFFFEDD5), Color(0xFFEA580C), "Rankings", "Mejores técnicos", bordeSuave, onRankings)
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (mostrarConfig) {
        AlertDialog(
            onDismissRequest = { mostrarConfig = false },
            title = { Text("Configuración", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Modo oscuro", modifier = Modifier.weight(1f), fontSize = 16.sp)
                        Switch(checked = modoOscuro, onCheckedChange = { modoOscuro = it })
                    }

                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                                .clickable { abrirIdioma = true }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Idioma", modifier = Modifier.weight(1f), fontSize = 16.sp)
                            Text(idioma, color = Color(0xFF111827), fontWeight = FontWeight.Medium, fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF6B7280))
                        }

                        DropdownMenu(expanded = abrirIdioma, onDismissRequest = { abrirIdioma = false }) {
                            listOf("Español", "Inglés").forEach { opt ->
                                DropdownMenuItem(text = { Text(opt, fontSize = 16.sp) }, onClick = { idioma = opt; abrirIdioma = false })
                            }
                        }
                    }

                    Text("Notificaciones", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sonidos", modifier = Modifier.weight(1f), fontSize = 16.sp)
                        Switch(checked = sonidos, onCheckedChange = { sonidos = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Push", modifier = Modifier.weight(1f), fontSize = 16.sp)
                        Switch(checked = push, onCheckedChange = { push = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Email", modifier = Modifier.weight(1f), fontSize = 16.sp)
                        Switch(checked = email, onCheckedChange = { email = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Generales", modifier = Modifier.weight(1f), fontSize = 16.sp)
                        Switch(checked = generales, onCheckedChange = { generales = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = { mostrarConfig = false }, colors = ButtonDefaults.buttonColors(containerColor = verde)) {
                    Text("Guardar", color = Color.White, fontSize = 16.sp)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { mostrarConfig = false }) {
                    Text("Cerrar", fontSize = 16.sp)
                }
            }
        )
    }

    if (mostrarNotificaciones) {
        AlertDialog(
            onDismissRequest = { mostrarNotificaciones = false },
            title = { Text("Notificaciones", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(notificaciones) { n ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(n.titulo, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), fontSize = 16.sp)
                                        if (n.noLeida) {
                                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(n.detalle, color = Color(0xFF6B7280), fontSize = 14.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(n.fecha, color = Color(0xFF9CA3AF), fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            val nuevas = notificaciones.map { it.copy(noLeida = false) }
                            notificaciones.clear()
                            notificaciones.addAll(nuevas)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Marcar todas como leídas", fontSize = 16.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { mostrarNotificaciones = false }, colors = ButtonDefaults.buttonColors(containerColor = verde)) {
                    Text("Cerrar", color = Color.White, fontSize = 16.sp)
                }
            }
        )
    }

    if (mostrarSubirCertificado) {
        AlertDialog(
            onDismissRequest = { mostrarSubirCertificado = false },
            title = { Text("Subir certificado", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = nombreCert,
                        onValueChange = { nombreCert = it },
                        label = { Text("Nombre del certificado", fontSize = 16.sp) },
                        placeholder = { Text("Ej: Certificado de instalador", fontSize = 14.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = tipoCert,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo de certificado", fontSize = 16.sp) },
                            trailingIcon = {
                                IconButton(onClick = { abrirTipoCert = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = abrirTipoCert, onDismissRequest = { abrirTipoCert = false }) {
                            listOf("Instalación","Auditoría","Energías renovables","Certificación").forEach { opt ->
                                DropdownMenuItem(text = { Text(opt, fontSize = 16.sp) }, onClick = { tipoCert = opt; abrirTipoCert = false })
                            }
                        }
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF1F1F1))
                            .border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF6B7280), modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Pulsa para seleccionar el archivo", color = Color(0xFF6B7280), fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("PDF, JPG, PNG · Máx. 10MB", color = Color(0xFF9CA3AF), fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        nombreCert = ""
                        tipoCert = "Selecciona un tipo"
                        mostrarSubirCertificado = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = verde)
                ) {
                    Text("Subir", color = Color.White, fontSize = 16.sp)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { mostrarSubirCertificado = false }) {
                    Text("Cancelar", fontSize = 16.sp)
                }
            }
        )
    }
}

@Preview
@Composable
fun PanelPreview() {
    PanelScreen()
}

@Composable
private fun Tarjeta(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    colorFondoIcono: Color,
    colorIcono: Color,
    titulo: String,
    subtitulo: String,
    borde: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, borde)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colorFondoIcono),
                contentAlignment = Alignment.Center
            ) {
                Icon(icono, contentDescription = null, tint = colorIcono, modifier = Modifier.size(28.dp))
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color(0xFF111827))
                Spacer(Modifier.height(4.dp))
                Text(subtitulo, color = Color(0xFF6B7280), fontSize = 15.sp)
            }
        }
    }
}
