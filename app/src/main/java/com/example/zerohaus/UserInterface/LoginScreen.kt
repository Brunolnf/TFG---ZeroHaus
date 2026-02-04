package com.example.zerohaus.UserInterface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
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
fun LoginScreen(
    onIniciarSesion: () -> Unit = {},
    onRegistrarse: () -> Unit = {},
    onOlvideContrasena: () -> Unit = {}
) {
    // Colores (muy parecidos a la maqueta)
    val verde = Color(0xFF16A34A) // verde intenso similar
    val fondo = Color(0xFFEEF8F5) // fondo verdoso claro

    var email by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }

    Scaffold(containerColor = fondo) { pv ->
        Column(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(26.dp))

            // Icono + marca
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
            Text(
                "Mejora la eficiencia energética de tu hogar",
                color = Color(0xFF2F3A3A),
                fontSize = 13.sp
            )

            Spacer(Modifier.height(18.dp))

            // Tarjeta central
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Iniciar sesión", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))

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

                    Spacer(Modifier.height(16.dp))

                    // Botón iniciar sesión
                    Button(
                        onClick = onIniciarSesion,
                        colors = ButtonDefaults.buttonColors(containerColor = verde),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Iniciar sesión", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(10.dp))

                    // Botón registrarse (outline)
                    OutlinedButton(
                        onClick = onRegistrarse,
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.dp,
                            brush = androidx.compose.ui.graphics.SolidColor(verde)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Registrarse", color = verde, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(12.dp))

                    TextButton(
                        onClick = onOlvideContrasena,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("¿Olvidaste tu contraseña?", color = Color(0xFF1F2937), fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "Al continuar, aceptas nuestros términos y condiciones",
                color = Color(0xFF6B7280),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }
    }
}
