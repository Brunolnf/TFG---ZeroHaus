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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.RegistroViewModel

@Composable
fun RegistroScreen(
    viewModel: RegistroViewModel,
    onRegistroExitoso: () -> Unit,
    onIniciarSesion: () -> Unit
) {
    val estado = viewModel.estado
    val verde = Color(0xFF16A34A)
    val fondo = Color(0xFFEEF8F5)
    val gris = Color(0xFF6B7280)
    val borde = Color(0xFFD1D5DB)
    var expandirTipo by remember { mutableStateOf(false) }

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

            // Logo
            Surface(Modifier.size(56.dp), shape = RoundedCornerShape(14.dp), color = verde) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Home, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("ZeroHaus", color = verde, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("Crea tu cuenta", color = Color(0xFF2F3A3A), fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))

            // Formulario
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Registrarse", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))

                    // Nombre
                    Text("Nombre completo", fontSize = 12.sp, color = Color(0xFF1F2937))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = estado.nombre,
                        onValueChange = { viewModel.cambiarNombre(it) },
                        placeholder = { Text("Tu nombre") },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = gris) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, focusedBorderColor = verde),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (estado.nombre.isEmpty() && estado.error != null) {
                        Text("El nombre es obligatorio", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Email
                    Text("Email", fontSize = 12.sp, color = Color(0xFF1F2937))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = estado.email,
                        onValueChange = { viewModel.cambiarEmail(it) },
                        placeholder = { Text("tu@email.com") },
                        leadingIcon = { Icon(Icons.Default.MailOutline, null, tint = gris) },
                        isError = estado.email.isNotEmpty() && !estado.emailValido,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, focusedBorderColor = verde),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (estado.email.isNotEmpty() && !estado.emailValido) {
                        Text("Introduce un email válido", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Tipo de usuario
                    Text("Tipo de usuario", fontSize = 12.sp, color = Color(0xFF1F2937))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = estado.tipoUsuario, onValueChange = {}, readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { expandirTipo = true }) {
                                    Icon(Icons.Default.ArrowDropDown, null, tint = gris)
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, focusedBorderColor = verde),
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = expandirTipo, onDismissRequest = { expandirTipo = false }) {
                            listOf("Propietario", "Técnico").forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = { viewModel.cambiarTipoUsuario(opt); expandirTipo = false }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Contraseña
                    Text("Contraseña", fontSize = 12.sp, color = Color(0xFF1F2937))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = estado.contrasena,
                        onValueChange = { viewModel.cambiarContrasena(it) },
                        placeholder = { Text("Mínimo 8 caracteres") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = gris) },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = estado.contrasena.isNotEmpty() && !estado.contrasenaValida,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, focusedBorderColor = verde),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (estado.contrasena.isNotEmpty() && !estado.contrasenaValida) {
                        Text("Mínimo 8 caracteres", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                    if (estado.contrasena.isNotEmpty() && estado.contrasenaValida && !estado.contrasenaNumeroLetra) {
                        Text("Debe contener al menos un número y una letra", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Confirmar contraseña
                    Text("Confirmar contraseña", fontSize = 12.sp, color = Color(0xFF1F2937))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = estado.confirmarContrasena,
                        onValueChange = { viewModel.cambiarConfirmarContrasena(it) },
                        placeholder = { Text("Repite la contraseña") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = gris) },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = estado.confirmarContrasena.isNotEmpty() && estado.contrasena != estado.confirmarContrasena,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, focusedBorderColor = verde),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (estado.confirmarContrasena.isNotEmpty() && estado.contrasena != estado.confirmarContrasena) {
                        Text("Las contraseñas no coinciden", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }

                    Spacer(Modifier.height(18.dp))

                    // Botón crear cuenta
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
                        Text("Crear cuenta", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(14.dp))

                    // Volver a login
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text("¿Ya tienes cuenta? ", color = gris, fontSize = 12.sp)
                        TextButton(onClick = onIniciarSesion, contentPadding = PaddingValues(0.dp)) {
                            Text("Inicia sesión", color = verde, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Error del servidor
                    estado.error?.let {
                        Spacer(Modifier.height(10.dp))
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2))
                        ) {
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