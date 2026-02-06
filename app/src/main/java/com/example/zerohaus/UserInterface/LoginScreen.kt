package com.example.zerohaus.UserInterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    //Lambdas para navegar a otras pantallas
    onIniciarSesion: () -> Unit = {},
    onRegistrarse: () -> Unit = {},
    onOlvideContrasena: () -> Unit = {}
) {

    // Colores principales de la pantalla para no repetirlos
    val verde = Color(0xFF16A34A)
    val fondo = Color(0xFFEEF8F5)

    // Estados locales del formulario
    var email by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }

    //Scaffold estructura de la pantalla
    Scaffold(containerColor = fondo) { pv ->
        Column(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(26.dp))

            // Icono superior dentro de un contenedor verde
            // con esquinas redondeadas que en un futuro sera el logo de ZeroHaus
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

            // Nombre de la applicacion
            Text("ZeroHaus", color = verde, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

            Spacer(Modifier.height(4.dp))

            // Descripción
            Text(
                "Mejora la eficiencia energética de tu hogar",
                color = Color(0xFF2F3A3A),
                fontSize = 13.sp
            )

            Spacer(Modifier.height(18.dp))

            // Card del formulario de login
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(18.dp)) {

                    // Título del formulario
                    Text("Iniciar sesión", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                    Spacer(Modifier.height(14.dp))

                    // Etiqueta del campo email
                    Text("Email", fontSize = 12.sp, color = Color(0xFF1F2937))
                    Spacer(Modifier.height(6.dp))

                    // Campo de texto para email
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },// actualiza  al escribir
                        placeholder = { Text("tu@email.com", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.MailOutline,
                                contentDescription = null,
                                tint = Color(0xFF6B7280)
                            )
                        },
                        singleLine = true,              // obliga que sea una sola línea
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

                    // Etiqueta del campo contraseña
                    Text("Contraseña", fontSize = 12.sp, color = Color(0xFF1F2937))
                    Spacer(Modifier.height(6.dp))

                    // Campo de texto para contraseña
                    OutlinedTextField(
                        value = contrasena,
                        onValueChange = { contrasena = it },     // actualiza al escribir
                        placeholder = { Text("Mínimo 8 caracteres", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF6B7280)
                            )
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), // oculta el texto con asteriscos ***
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

                    // Botón para iniciar sesión
                    Button(
                        onClick = onIniciarSesion, // ejecuta la acción de ir a la pantalla del panel
                        colors = ButtonDefaults.buttonColors(containerColor = verde),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Iniciar sesión", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(10.dp))

                    // Botón ir a registro
                    OutlinedButton(
                        onClick = onRegistrarse, // navega a registro
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Registrarse", color = verde, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Enlace olvidé contraseña
                    TextButton(
                        onClick = onOlvideContrasena, // futura acción (API)
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("¿Olvidaste tu contraseña?", color = Color(0xFF1F2937), fontSize = 12.sp)
                    }
                }
            }


            // Texto informativo
            Text(
                "Al continuar, aceptas nuestros términos y condiciones",
                color = Color(0xFF6B7280),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }
    }
}

//Preview de la pantalla
@Preview
@Composable
fun LoginPreview() {
    LoginScreen()
}
