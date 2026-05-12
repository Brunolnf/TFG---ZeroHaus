package com.example.zerohaus.UserInterface

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.RegistroViewModel
import com.example.zerohaus.Util.LocalCadenas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistroScreen(
    viewModel: RegistroViewModel,
    onRegistroExitoso: () -> Unit,
    onIniciarSesion: () -> Unit
) {
    val c = LocalCadenas.current
    val estado = viewModel.estado
    val verde = MaterialTheme.colorScheme.primary
    val fondo = Color(0xFFEEF8F5)
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val borde = Color(0xFFD1D5DB)
    var expandirTipo by remember { mutableStateOf(false) }
    var verContrasena by remember { mutableStateOf(false) }
    var verConfirmar by remember { mutableStateOf(false) }

    LaunchedEffect(estado.registroCorrecto) { if (estado.registroCorrecto) onRegistroExitoso() }

    Scaffold(containerColor = fondo) { pv ->
        Column(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(26.dp))

            ZeroHausLogo(size = 56.dp)
            Spacer(Modifier.height(10.dp))
            Text("ZeroHaus", color = verde, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(c.registroTitulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(c.registroSubtitulo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))

                    Text(c.registroNombre, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = estado.nombre,
                        onValueChange = { viewModel.cambiarNombre(it) },
                        placeholder = { Text(c.registroNombrePlaceholder) },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = gris) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, focusedBorderColor = verde),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (estado.nombre.isEmpty() && estado.error != null) {
                        Text(c.registroNombreError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }

                    Spacer(Modifier.height(12.dp))

                    Text("Email", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = estado.email,
                        onValueChange = { viewModel.cambiarEmail(it) },
                        placeholder = { Text(c.emailPlaceholder) },
                        leadingIcon = { Icon(Icons.Default.MailOutline, null, tint = gris) },
                        isError = estado.email.isNotEmpty() && !estado.emailValido,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, focusedBorderColor = verde),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (estado.email.isNotEmpty() && !estado.emailValido) {
                        Text(c.emailError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(c.registroTipoUsuario, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    val tipoDisplay = when (estado.tipoUsuario) {
                        "Técnico" -> c.tipoTecnico
                        else -> c.tipoPropietario
                    }
                    ExposedDropdownMenuBox(
                        expanded = expandirTipo,
                        onExpandedChange = { expandirTipo = it }
                    ) {
                        OutlinedTextField(
                            value = tipoDisplay, onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandirTipo) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, focusedBorderColor = verde),
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = expandirTipo,
                            onDismissRequest = { expandirTipo = false }
                        ) {
                            listOf("Propietario" to c.tipoPropietario, "Técnico" to c.tipoTecnico).forEach { (clave, etiqueta) ->
                                DropdownMenuItem(
                                    text = { Text(etiqueta) },
                                    onClick = { viewModel.cambiarTipoUsuario(clave); expandirTipo = false }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(c.contrasena, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = estado.contrasena,
                        onValueChange = { viewModel.cambiarContrasena(it) },
                        placeholder = { Text(c.contrasenaPlaceholder) },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = gris) },
                        trailingIcon = {
                            IconButton(onClick = { verContrasena = !verContrasena }) {
                                Icon(
                                    if (verContrasena) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null, tint = gris
                                )
                            }
                        },
                        visualTransformation = if (verContrasena) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = estado.contrasena.isNotEmpty() && !estado.contrasenaValida,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, focusedBorderColor = verde),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (estado.contrasena.isNotEmpty() && !estado.contrasenaValida) {
                        Text(c.contrasenaError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                    if (estado.contrasena.isNotEmpty() && estado.contrasenaValida && !estado.contrasenaNumeroLetra) {
                        Text(c.registroContrasenaNumeroLetra, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(c.registroConfirmar, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = estado.confirmarContrasena,
                        onValueChange = { viewModel.cambiarConfirmarContrasena(it) },
                        placeholder = { Text(c.registroConfirmarPlaceholder) },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = gris) },
                        trailingIcon = {
                            IconButton(onClick = { verConfirmar = !verConfirmar }) {
                                Icon(
                                    if (verConfirmar) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null, tint = gris
                                )
                            }
                        },
                        visualTransformation = if (verConfirmar) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = estado.confirmarContrasena.isNotEmpty() && estado.contrasena != estado.confirmarContrasena,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, focusedBorderColor = verde),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (estado.confirmarContrasena.isNotEmpty() && estado.contrasena != estado.confirmarContrasena) {
                        Text(c.registroContrasenasNoCoinciden, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = { viewModel.crearCuenta() },
                        enabled = estado.formularioValido && !estado.cargando,
                        colors = ButtonDefaults.buttonColors(containerColor = verde),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (estado.cargando) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(c.registroBoton, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(c.registroYaTieneCuenta, color = gris, fontSize = 12.sp)
                        TextButton(onClick = onIniciarSesion, contentPadding = PaddingValues(0.dp)) {
                            Text(c.registroIniciarSesion, color = verde, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    estado.error?.let {
                        Spacer(Modifier.height(10.dp))
                        Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2))) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(it, color = Color(0xFF991B1B), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(20.dp))
        }
    }
}
