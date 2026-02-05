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
            NotificacionUi(
                "Nuevo presupuesto recibido",
                "EcoReformas Madrid ha enviado un presupuesto.",
                "Hoy",
                true
            ),
            NotificacionUi(
                "Actualización de proyecto",
                "El proyecto 'Aerotermia' pasó a 'En curso'.",
                "Ayer",
                false
            ),
            NotificacionUi(
                "Nueva valoración",
                "Has recibido una respuesta a tu reseña.",
                "Hace 3 días",
                false
            )
        )
    }
    val hayNoLeidas = notificaciones.any { it.noLeida }

    Scaffold(
        containerColor = fondo,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White),
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(verde),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Home,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "ZeroHaus",
                                color = verde,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )
                        }
                        Text("Usuario Demo", color = grisTexto, fontSize = 12.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { mostrarSubirCertificado = true }) {
                        Icon(
                            Icons.Default.AddCircle,
                            contentDescription = "Subir certificado",
                            tint = Color(0xFF111827)
                        )
                    }

                    Box {
                        IconButton(onClick = { mostrarNotificaciones = true }) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Notificaciones",
                                tint = Color(0xFF111827)
                            )
                        }
                        if (hayNoLeidas) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-10).dp, y = 10.dp)
                            )
                        }
                    }

                    IconButton(onClick = { mostrarConfig = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Configuración",
                            tint = Color(0xFF111827)
                        )
                    }

                    IconButton(onClick = onCerrarSesion) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "Cerrar sesión",
                            tint = Color(0xFF111827)
                        )
                    }

                    Spacer(Modifier.width(4.dp))
                }
            )
        }
    ) { pv ->
        Column(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Bienvenido, Usuario",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF111827)
                    )
                    Text(
                        "Gestiona la eficiencia energética de tu hogar",
                        color = grisTexto,
                        fontSize = 12.sp
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = verde),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Tu vivienda",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                "Mi vivienda principal",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 46.dp, height = 42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("D", fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "Consumo estimado",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                                Text(
                                    "145 kWh/año",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "Emisiones",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                                Text(
                                    "32 kg CO₂/año",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = onVerUltimoInforme,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(
                                    alpha = 0.18f
                                )
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Ver último informe  →",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Text(
                    "Acciones",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color(0xFF111827)
                )

                Tarjeta(
                    icono = Icons.Default.Add,
                    colorFondoIcono = Color(0xFFD1FAE5),
                    colorIcono = Color(0xFF059669),
                    titulo = "Nuevo preestudio",
                    subtitulo = "Analiza tu vivienda",
                    borde = bordeSuave,
                    onClick = onNuevoPreestudio
                )

                Tarjeta(
                    icono = Icons.Default.Place,
                    colorFondoIcono = Color(0xFFDBEAFE),
                    colorIcono = Color(0xFF2563EB),
                    titulo = "Buscar técnicos",
                    subtitulo = "Encuentra profesionales",
                    borde = bordeSuave,
                    onClick = onBuscarTecnicos
                )

                Tarjeta(
                    icono = Icons.Default.Menu,
                    colorFondoIcono = Color(0xFFEDE9FE),
                    colorIcono = Color(0xFF7C3AED),
                    titulo = "Mis proyectos",
                    subtitulo = "Gestiona reformas",
                    borde = bordeSuave,
                    onClick = onMisProyectos
                )

                Tarjeta(
                    icono = Icons.Default.Star,
                    colorFondoIcono = Color(0xFFFFEDD5),
                    colorIcono = Color(0xFFEA580C),
                    titulo = "Rankings",
                    subtitulo = "Mejores técnicos",
                    borde = bordeSuave,
                    onClick = onRankings
                )

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF2563EB)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Consejo de eficiencia energética",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Color(0xFF1E3A8A)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Mejorar el aislamiento de ventanas puede reducir el\nconsumo energético hasta un 25%. Consulta con un\ntécnico certificado para evaluar tu vivienda.",
                            color = Color(0xFF1D4ED8),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
            }
        }
    }

    if (mostrarConfig) {
        AlertDialog(
            onDismissRequest = { mostrarConfig = false },
            title = { Text("Configuración") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Modo oscuro", modifier = Modifier.weight(1f))
                        Switch(checked = modoOscuro, onCheckedChange = { modoOscuro = it })
                    }

                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(10.dp))
                                .clickable { abrirIdioma = true }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Idioma", modifier = Modifier.weight(1f))
                            Text(idioma, color = Color(0xFF111827), fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Color(0xFF6B7280)
                            )
                        }

                        DropdownMenu(
                            expanded = abrirIdioma,
                            onDismissRequest = { abrirIdioma = false }) {
                            listOf(
                                "Español",
                                "Inglés"
                            ).forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = { idioma = opt; abrirIdioma = false }
                                )
                            }
                        }
                    }

                    Text("Notificaciones", fontWeight = FontWeight.SemiBold)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sonidos", modifier = Modifier.weight(1f))
                        Switch(checked = sonidos, onCheckedChange = { sonidos = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Push", modifier = Modifier.weight(1f))
                        Switch(checked = push, onCheckedChange = { push = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Email", modifier = Modifier.weight(1f))
                        Switch(checked = email, onCheckedChange = { email = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Generales", modifier = Modifier.weight(1f))
                        Switch(checked = generales, onCheckedChange = { generales = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { mostrarConfig = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) { Text("Guardar", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { mostrarConfig = false }) { Text("Cerrar") }
            }
        )
    }

    if (mostrarNotificaciones) {
        AlertDialog(
            onDismissRequest = { mostrarNotificaciones = false },
            title = { Text("Notificaciones") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(notificaciones) { n ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            n.titulo,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (n.noLeida) {
                                            Box(
                                                Modifier
                                                    .size(9.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFEF4444))
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(n.detalle, color = Color(0xFF6B7280), fontSize = 12.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(n.fecha, color = Color(0xFF9CA3AF), fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            val nuevas = notificaciones.map { it.copy(noLeida = false) }
                            notificaciones.clear()
                            notificaciones.addAll(nuevas)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Marcar todas como leídas")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { mostrarNotificaciones = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) { Text("Cerrar", color = Color.White) }
            }
        )
    }

    if (mostrarSubirCertificado) {
        AlertDialog(
            onDismissRequest = { mostrarSubirCertificado = false },
            title = { Text("Subir certificado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nombreCert,
                        onValueChange = { nombreCert = it },
                        label = { Text("Nombre del certificado") },
                        placeholder = { Text("Ej: Certificado de instalador") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = tipoCert,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo de certificado") },
                            trailingIcon = {
                                IconButton(onClick = { abrirTipoCert = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = abrirTipoCert,
                            onDismissRequest = { abrirTipoCert = false }) {
                            listOf(
                                "Instalación",
                                "Auditoría",
                                "Energías renovables",
                                "Certificación"
                            ).forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = { tipoCert = opt; abrirTipoCert = false }
                                )
                            }
                        }
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF1F1F1))
                            .border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = null,
                            tint = Color(0xFF6B7280)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Pulsa para seleccionar o arrastra el archivo",
                            color = Color(0xFF6B7280),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "PDF, JPG, PNG · Máx. 10MB",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp
                        )
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) { Text("Subir", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { mostrarSubirCertificado = false }) { Text("Cancelar") }
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, borde)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colorFondoIcono),
                contentAlignment = Alignment.Center
            ) {
                Icon(icono, contentDescription = null, tint = colorIcono)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    titulo,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color(0xFF111827)
                )
                Spacer(Modifier.height(2.dp))
                Text(subtitulo, color = Color(0xFF6B7280), fontSize = 12.sp)
            }
        }
    }
}
