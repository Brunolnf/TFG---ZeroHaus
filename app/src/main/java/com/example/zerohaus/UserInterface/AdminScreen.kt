package com.example.zerohaus.UserInterface

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Modelos.Certificado
import com.example.zerohaus.Modelos.Usuario
import com.example.zerohaus.Util.AdminConfig
import com.example.zerohaus.ViewModel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.*

private val VERDE = Color(0xFF16A34A)
private val ROJO = Color(0xFFDC2626)
private val NARANJA = Color(0xFFD97706)
private val AZUL = Color(0xFF2563EB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    onCerrarSesion: () -> Unit
) {
    LaunchedEffect(Unit) { viewModel.cargar() }

    val context = LocalContext.current
    var tabSeleccionado by remember { mutableIntStateOf(0) }
    var mostrarCrear by remember { mutableStateOf(false) }
    var usuarioEditar by remember { mutableStateOf<Usuario?>(null) }
    var usuarioConfirmarEliminar by remember { mutableStateOf<Usuario?>(null) }
    var certRechazarDialog by remember { mutableStateOf<Certificado?>(null) }
    var motivoRechazo by remember { mutableStateOf("") }

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(viewModel.mensaje.value) {
        viewModel.mensaje.value?.let {
            snackbarHost.showSnackbar(it)
            viewModel.limpiarMensaje()
        }
    }
    LaunchedEffect(viewModel.error.value) {
        viewModel.error.value?.let {
            snackbarHost.showSnackbar(it)
            viewModel.limpiarError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Panel de administración", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "${viewModel.usuarios.size} usuarios",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.cargar() }) {
                        Icon(Icons.Default.Refresh, "Recargar", tint = Color.White)
                    }
                    IconButton(onClick = onCerrarSesion) {
                        Icon(Icons.Default.ExitToApp, "Cerrar sesión", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VERDE,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { mostrarCrear = true },
                containerColor = VERDE,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Nuevo usuario", fontWeight = FontWeight.SemiBold) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { pv ->
        Column(Modifier.fillMaxSize().padding(pv)) {

            // ── Pestañas ──
            val pendientes = viewModel.certificadosPendientes.size
            TabRow(
                selectedTabIndex = tabSeleccionado,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = VERDE
            ) {
                Tab(
                    selected = tabSeleccionado == 0,
                    onClick = { tabSeleccionado = 0 },
                    text = { Text("Usuarios (${viewModel.usuarios.size})") }
                )
                Tab(
                    selected = tabSeleccionado == 1,
                    onClick = { tabSeleccionado = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Certificados")
                            if (pendientes > 0) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ROJO)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("$pendientes", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                )
            }

            when (tabSeleccionado) {

                // ── Tab 0: Usuarios ──
                0 -> {
                    OutlinedTextField(
                        value = viewModel.filtro.value,
                        onValueChange = { viewModel.filtro.value = it },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        placeholder = { Text("Buscar por nombre o email") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VERDE, cursorColor = VERDE)
                    )
                    if (viewModel.cargando.value) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VERDE)
                        }
                    } else {
                        val lista = viewModel.usuariosFiltrados()
                        if (lista.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Sin resultados", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(lista, key = { it.uid }) { u ->
                                    UsuarioCard(
                                        usuario = u,
                                        esAdminProtegido = AdminConfig.esAdmin(u.email),
                                        onEditar = { usuarioEditar = u },
                                        onBloquear = { viewModel.toggleBloqueo(u) },
                                        onEliminar = { usuarioConfirmarEliminar = u }
                                    )
                                }
                                item { Spacer(Modifier.height(80.dp)) }
                            }
                        }
                    }
                }

                // ── Tab 1: Certificados pendientes ──
                1 -> {
                    if (viewModel.cargandoCerts.value) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VERDE)
                        }
                    } else if (viewModel.certificadosPendientes.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CheckCircle, null, tint = VERDE.copy(0.4f), modifier = Modifier.size(52.dp))
                                Text("No hay certificados pendientes", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                OutlinedButton(onClick = { viewModel.cargarCertificadosPendientes() }, shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Actualizar")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(viewModel.certificadosPendientes, key = { it.id }) { cert ->
                                CertificadoAdminCard(
                                    cert = cert,
                                    onAprobar = { viewModel.aprobarCertificado(cert) },
                                    onRechazar = { certRechazarDialog = cert; motivoRechazo = "" },
                                    onVerArchivo = {
                                        if (cert.urlArchivo.isNotBlank()) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cert.urlArchivo)))
                                        }
                                    }
                                )
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }

    // Diálogo rechazo de certificado
    certRechazarDialog?.let { cert ->
        AlertDialog(
            onDismissRequest = { certRechazarDialog = null },
            icon = { Icon(Icons.Default.Cancel, null, tint = ROJO) },
            title = { Text("Rechazar certificado", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Certificado: ${cert.nombre}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Técnico: ${cert.tecnicoNombre.ifBlank { "—" }}", fontSize = 13.sp)
                    OutlinedTextField(
                        value = motivoRechazo,
                        onValueChange = { motivoRechazo = it },
                        label = { Text("Motivo del rechazo (opcional)") },
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rechazarCertificado(cert, motivoRechazo.trim())
                        certRechazarDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ROJO)
                ) { Text("Rechazar", color = Color.White, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { certRechazarDialog = null }) { Text("Cancelar") }
            }
        )
    }

    // Diálogo Crear
    if (mostrarCrear) {
        DialogoCrearUsuario(
            cargando = viewModel.cargando.value,
            onCancelar = { mostrarCrear = false },
            onCrear = { nombre, email, password, tipo ->
                viewModel.crear(nombre, email, password, tipo) {
                    mostrarCrear = false
                }
            }
        )
    }

    // Diálogo Editar
    usuarioEditar?.let { u ->
        DialogoEditarUsuario(
            usuario = u,
            cargando = viewModel.cargando.value,
            onCancelar = { usuarioEditar = null },
            onGuardar = { nombre, tipo ->
                viewModel.actualizar(u.uid, nombre, tipo) {
                    usuarioEditar = null
                }
            }
        )
    }

    // Confirmación eliminar
    usuarioConfirmarEliminar?.let { u ->
        AlertDialog(
            onDismissRequest = { usuarioConfirmarEliminar = null },
            icon = { Icon(Icons.Default.Delete, null, tint = ROJO) },
            title = { Text("Eliminar usuario") },
            text = {
                Text(
                    "¿Seguro que quieres eliminar a ${u.nombre.ifBlank { u.email }}?\n\n" +
                            "Se borrarán todos sus datos: viviendas, informes, proyectos, chats, valoraciones y solicitudes."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.eliminar(u)
                        usuarioConfirmarEliminar = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ROJO)
                ) { Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { usuarioConfirmarEliminar = null }) { Text("Cancelar") }
            }
        )
    }

}

@Composable
private fun UsuarioCard(
    usuario: Usuario,
    esAdminProtegido: Boolean,
    onEditar: () -> Unit,
    onBloquear: () -> Unit,
    onEliminar: () -> Unit
) {
    var menuExpandido by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            val (colorAvatar, inicial) = avatarColor(usuario)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colorAvatar.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(inicial, color = colorAvatar, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        usuario.nombre.ifBlank { "(sin nombre)" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (esAdminProtegido) {
                        Spacer(Modifier.width(6.dp))
                        EtiquetaMini("ADMIN", VERDE)
                    }
                }
                Text(
                    usuario.email,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EtiquetaMini(usuario.tipoUsuario, AZUL)
                    if (usuario.bloqueado) EtiquetaMini("Bloqueado", NARANJA)
                    else EtiquetaMini("Activo", VERDE)
                }
            }

            // Menú de acciones
            Box {
                IconButton(
                    onClick = { menuExpandido = true },
                    enabled = !esAdminProtegido
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        "Acciones",
                        tint = if (esAdminProtegido)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenu(
                    expanded = menuExpandido,
                    onDismissRequest = { menuExpandido = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = AZUL) },
                        onClick = { menuExpandido = false; onEditar() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (usuario.bloqueado) "Desbloquear" else "Bloquear") },
                        leadingIcon = {
                            Icon(
                                if (usuario.bloqueado) Icons.Default.LockOpen else Icons.Default.Lock,
                                null,
                                tint = NARANJA
                            )
                        },
                        onClick = { menuExpandido = false; onBloquear() }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar", color = ROJO) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = ROJO) },
                        onClick = { menuExpandido = false; onEliminar() }
                    )
                }
            }
        }
    }
}

@Composable
private fun EtiquetaMini(texto: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(texto, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CertificadoAdminCard(
    cert: Certificado,
    onAprobar: () -> Unit,
    onRechazar: () -> Unit,
    onVerArchivo: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cert.nombre, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(cert.tipo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(NARANJA.copy(0.13f)).padding(horizontal = 8.dp, vertical = 3.dp)
                ) { Text("Pendiente", color = NARANJA, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
            }
            if (cert.tecnicoNombre.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(cert.tecnicoNombre, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Subido: ${sdf.format(Date(cert.fechaSubida))}", fontSize = 11.sp, color = Color(0xFF9CA3AF))
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onVerArchivo,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ver archivo", fontSize = 12.sp)
                }
                Button(
                    onClick = onRechazar,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ROJO),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Rechazar", color = Color.White, fontSize = 12.sp)
                }
                Button(
                    onClick = onAprobar,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VERDE),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Aprobar", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun avatarColor(u: Usuario): Pair<Color, String> {
    val inicial = (u.nombre.firstOrNull() ?: u.email.firstOrNull() ?: '?').uppercaseChar().toString()
    val colores = listOf(VERDE, AZUL, NARANJA, Color(0xFF7C3AED), Color(0xFFEA580C))
    val hash = (u.uid.hashCode() and Int.MAX_VALUE) % colores.size
    return colores[hash] to inicial
}

// ────────────────────────── Diálogos ──────────────────────────

@Composable
private fun DialogoCrearUsuario(
    cargando: Boolean,
    onCancelar: () -> Unit,
    onCrear: (nombre: String, email: String, password: String, tipo: String) -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("Propietario") }
    var mostrarPwd by remember { mutableStateOf(false) }

    val valido = nombre.isNotBlank() &&
            android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
            password.length >= 8 && password.any { it.isDigit() } && password.any { it.isLetter() }

    AlertDialog(
        onDismissRequest = { if (!cargando) onCancelar() },
        title = { Text("Nuevo usuario", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VERDE, cursorColor = VERDE)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VERDE, cursorColor = VERDE)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña (min 8, con letra y número)") },
                    singleLine = true,
                    visualTransformation = if (mostrarPwd) androidx.compose.ui.text.input.VisualTransformation.None
                    else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { mostrarPwd = !mostrarPwd }) {
                            Icon(
                                if (mostrarPwd) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VERDE, cursorColor = VERDE)
                )
                SelectorTipo(tipo) { tipo = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCrear(nombre.trim(), email.trim(), password, tipo) },
                enabled = valido && !cargando,
                colors = ButtonDefaults.buttonColors(containerColor = VERDE)
            ) {
                if (cargando) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp
                    )
                } else {
                    Text("Crear")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, enabled = !cargando) { Text("Cancelar") }
        }
    )
}

@Composable
private fun DialogoEditarUsuario(
    usuario: Usuario,
    cargando: Boolean,
    onCancelar: () -> Unit,
    onGuardar: (nombre: String, tipo: String) -> Unit
) {
    var nombre by remember { mutableStateOf(usuario.nombre) }
    var tipo by remember { mutableStateOf(usuario.tipoUsuario) }
    val valido = nombre.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!cargando) onCancelar() },
        title = { Text("Editar usuario", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(usuario.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VERDE, cursorColor = VERDE)
                )
                SelectorTipo(tipo) { tipo = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { onGuardar(nombre.trim(), tipo) },
                enabled = valido && !cargando,
                colors = ButtonDefaults.buttonColors(containerColor = VERDE)
            ) {
                if (cargando) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp
                    )
                } else {
                    Text("Guardar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, enabled = !cargando) { Text("Cancelar") }
        }
    )
}

@Composable
private fun SelectorTipo(tipoActual: String, onCambiar: (String) -> Unit) {
    val opciones = listOf("Propietario", "Técnico")
    Column {
        Text("Tipo de usuario", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opciones.forEach { op ->
                val sel = op == tipoActual
                FilterChip(
                    selected = sel,
                    onClick = { onCambiar(op) },
                    label = { Text(op) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VERDE.copy(alpha = 0.15f),
                        selectedLabelColor = VERDE
                    )
                )
            }
        }
    }
}
