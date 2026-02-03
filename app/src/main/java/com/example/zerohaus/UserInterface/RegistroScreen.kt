package com.example.zerohaus.UserInterface

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*




@Composable
fun RegistroScreen(
    onCrearCuenta: () -> Unit,
    onLogin: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2FBF7))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFF00A63E), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "ZeroHaus",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF00A63E)
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Crea tu cuenta",
                fontSize = 14.sp,
                color = Color(0xFF6B7280)
            )

            Spacer(Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(24.dp)) {

                    Text(
                        text = "Registrarse",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(16.dp))

                    CampoRegistro(
                        "Nombre completo",
                        "Tu nombre",
                        Icons.Default.Person
                    )

                    CampoRegistro(
                        "Email",
                        "tu@email.com",
                        Icons.Default.Email
                    )

                    Text("Tipo de usuario", fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = "Propietario",
                        onValueChange = {},
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        readOnly = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFFE5E7EB),
                            focusedBorderColor = Color(0xFF00A63E)
                        )
                    )

                    Spacer(Modifier.height(16.dp))

                    CampoRegistro(
                        "Contraseña",
                        "Mínimo 8 caracteres",
                        Icons.Default.Lock
                    )

                    CampoRegistro(
                        "Confirmar contraseña",
                        "Repite tu contraseña",
                        Icons.Default.Lock
                    )

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = onCrearCuenta,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00A63E),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Crear cuenta")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            TextButton(onClick = onLogin) {
                Text(
                    text = "¿Ya tienes cuenta? Inicia sesión",
                    color = Color(0xFF00A63E)
                )
            }
        }
    }
}
@Composable
fun CampoRegistro(
    label: String,
    placeholder: String,
    icono: ImageVector
) {
    Text(label, fontSize = 14.sp)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = "",
        onValueChange = {},
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(icono, null) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color(0xFFE5E7EB),
            focusedBorderColor = Color(0xFF00A63E)
        )
    )
    Spacer(Modifier.height(16.dp))
}

