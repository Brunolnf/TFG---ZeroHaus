package com.example.zerohaus.UserInterface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.RegistroViewModel

@Composable
fun RegistroScreen(
    onRegistroCorrecto: () -> Unit,
    onVolverLogin: () -> Unit,
    viewModel: RegistroViewModel
) {
    val nombre by viewModel.nombre.collectAsState()
    val email by viewModel.email.collectAsState()
    val tipoUsuario by viewModel.tipoUsuario.collectAsState()
    val password by viewModel.password.collectAsState()
    val confirmarPassword by viewModel.confirmarPassword.collectAsState()
    val registroCorrecto by viewModel.registroCorrecto.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(registroCorrecto) {
        if (registroCorrecto) {
            onRegistroCorrecto()
            viewModel.limpiarEstado()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2FBF7))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Crear cuenta",
                fontSize = 20.sp,
                color = Color(0xFF00A63E)
            )

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(24.dp)) {

                    OutlinedTextField(
                        value = nombre,
                        onValueChange = viewModel::onNombreChange,
                        label = { Text("Nombre completo") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = viewModel::onEmailChange,
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tipoUsuario,
                        onValueChange = {},
                        label = { Text("Tipo de usuario") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text("Contraseña") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmarPassword,
                        onValueChange = viewModel::onConfirmarPasswordChange,
                        label = { Text("Confirmar contraseña") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = Color.Red, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00A63E)
                        )
                    ) {
                        Text("Crear cuenta", color = Color.White)
                    }

                    Spacer(Modifier.height(12.dp))

                    TextButton(onClick = onVolverLogin) {
                        Text("¿Ya tienes cuenta? Inicia sesión")
                    }
                }
            }
        }
    }
}
