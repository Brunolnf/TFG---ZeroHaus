package com.example.zerohaus.UserInterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun RegistroScreen(
    onCrearCuenta: () -> Unit = {},
    onIniciarSesion: () -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val fondo = Color(0xFFEEF8F5)

    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var tipoUsuario by remember { mutableStateOf("Propietario") }
    var contrasena by remember { mutableStateOf("") }
    var confirmarContrasena by remember { mutableStateOf("") }
    var expandirTipo by remember { mutableStateOf(false) }

    Scaffold(containerColor = fondo) { pv ->
        Column(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(26.dp))


            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(14.dp),
                color = verde
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("ZeroHaus", color = verde, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text("Crea tu cuenta", color = Color(0xFF2F3A3A), fontSize = 13.sp)

            Spacer(Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Registrarse", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))

                    Text("Nombre completo", fontSize = 12.sp, color = Color(0xFF1F2937))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        placeholder = { Text("Tu nombre", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF6B7280))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFFD1D5DB),
                            focusedBorderColor = Color(0xFF9CA3AF),
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Text("Email", fontSize = 12.sp, color = Color(0xFF1F2937))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text("tu@email.com", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.MailOutline, contentDescription = null, tint = Color(0xFF6B7280))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFFD1D5DB),
                            focusedBorderColor = Color(0xFF9CA3AF),
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Text("Tipo de usuario", fontSize = 12.sp, color = Color(0xFF1F2937))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = tipoUsuario,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { expandirTipo = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF6B7280))
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color(0xFFD1D5DB),
                                focusedBorderColor = Color(0xFF9CA3AF),
                                unfocusedContainerColor = Color.White,
                                focusedContainerColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        DropdownMenu(
                            expanded = expandirTipo,
                            onDismissRequest = { expandirTipo = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Propietario") },
                                onClick = { tipoUsuario = "Propietario"; expandirTipo = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Técnico") },
                                onClick = { tipoUsuario = "Técnico"; expandirTipo = false }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text("Contraseña", fontSize = 12.sp, color = Color(0xFF1F2937))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = contrasena,
                        onValueChange = { contrasena = it },
                        placeholder = { Text("Mínimo 8 caracteres", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF6B7280))
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFFD1D5DB),
                            focusedBorderColor = Color(0xFF9CA3AF),
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Text("Confirmar contraseña", fontSize = 12.sp, color = Color(0xFF1F2937))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = confirmarContrasena,
                        onValueChange = { confirmarContrasena = it },
                        placeholder = { Text("Repite tu contraseña", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF6B7280))
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFFD1D5DB),
                            focusedBorderColor = Color(0xFF9CA3AF),
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onCrearCuenta,
                        colors = ButtonDefaults.buttonColors(containerColor = verde),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Crear cuenta", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("¿Ya tienes cuenta? ", color = Color(0xFF6B7280), fontSize = 12.sp)
                        TextButton(
                            onClick = onIniciarSesion,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Inicia sesión", color = verde, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}
