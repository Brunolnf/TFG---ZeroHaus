package com.example.zerohaus.UserInterface

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Modelos.Usuario
import com.example.zerohaus.Util.AdminConfig
import com.example.zerohaus.ViewModel.AdminViewModel

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

    var mostrarCrear by remember { mutableStateOf(false) }
    var usuarioEditar by remember { mutableStateOf<Usuario?>(null) }
    var usuarioConfirmarEliminar by remember { mutableStateOf<Usuario?>(null) }
    var usuarioConfirmarBorrarDefinitivo by remember { mutableStateOf<Usuario?>(null) }

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

            // Buscador
            OutlinedTextField(
                value = viewModel.filtro.value,
                onValueChange = { viewModel.filtro.value = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Buscar por nombre o email") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VERDE,
                    cursorColor = VERDE
                )
            )

            if (viewModel.cargando.value && viewModel.usuarios.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VERDE)
                }
            } else {
                val lista = viewModel.usuariosFiltrados()
                if (lista.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Sin resultados",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                onEliminar = { usuarioConfirmarEliminar = u },
                                onRestaurar = { viewModel.restaurar(u) },
                                onBorrarDefinitivo = { usuarioConfirmarBorrarDefinitivo = u }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) } // espacio para el FAB
                    }
                }
            }
        }
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

    // Confirmación eliminar (soft delete, reversible)
    usuarioConfirmarEliminar?.let { u ->
        AlertDialog(
            onDismissRequest = { usuarioConfirmarEliminar = null },
            icon = { Icon(Icons.Default.Warning, null, tint = ROJO) },
            title = { Text("Eliminar usuario") },
            text = {
                Text(
                    "¿Seguro que quieres eliminar a ${u.nombre.ifBlank { u.email }}? " +
                            "No podrá iniciar sesión. Podrás restaurarlo más tarde."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.eliminar(u)
                        usuarioConfirmarEliminar = null
                    }
                ) { Text("Eliminar", color = ROJO, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { usuarioConfirmarEliminar = null }) { Text("Cancelar") }
            }
        )
    }

    // Confirmación borrado DEFINITIVO (irreversible, requiere doble verificación)
    usuarioConfirmarBorrarDefinitivo?.let { u ->
        var confirmText by remember { mutableStateOf("") }
        val palabraClave = "ELIMINAR"
        val confirmado = confirmText.trim().equals(palabraClave, ignoreCase = false)

        AlertDialog(
            onDismissRequest = { usuarioConfirmarBorrarDefinitivo = null },
            icon = { Icon(Icons.Default.Delete, null, tint = ROJO) },
            title = { Text("Eliminar definitivamente", color = ROJO, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Esta acción es IRREVERSIBLE. Se borrarán de forma definitiva:\n" +
                                "  • La cuenta de Firebase Auth\n" +
                                "  • Todos los datos de ${u.nombre.ifBlank { u.email }} en Firestore\n" +
                                "  • Viviendas, informes, proyectos, certificados, chats, reseñas…\n\n" +
                                "La cuenta NO podrá ser restaurada."
                    )
                    Text(
                        "Escribe \"$palabraClave\" para confirmar:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ROJO,
                            cursorColor = ROJO
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = confirmado,
                    onClick = {
                        viewModel.eliminarDefinitivamente(u)
                        usuarioConfirmarBorrarDefinitivo = null
                    }
                ) {
                    Text(
                        "Borrar para siempre",
                        color = if (confirmado) ROJO else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { usuarioConfirmarBorrarDefinitivo = null }) { Text("Cancelar") }
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
    onEliminar: () -> Unit,
    onRestaurar: () -> Unit,
    onBorrarDefinitivo: () -> Unit
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
                    when {
                        usuario.eliminado -> EtiquetaMini("Eliminado", ROJO)
                        usuario.bloqueado -> EtiquetaMini("Bloqueado", NARANJA)
                        else -> EtiquetaMini("Activo", VERDE)
                    }
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
                    if (usuario.eliminado) {
                        DropdownMenuItem(
                            text = { Text("Restaurar") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null, tint = VERDE) },
                            onClick = { menuExpandido = false; onRestaurar() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Eliminar definitivamente", color = ROJO) },
                            leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = ROJO) },
                            onClick = { menuExpandido = false; onBorrarDefinitivo() }
                        )
                    } else {
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
