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
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.zerohaus.app.ui.login.LoginViewModel

@Composable
fun LoginScreen(
    onLoginCorrecto: () -> Unit,
    onRegistro: () -> Unit,
    onRecuperar: () -> Unit,
    viewModel: LoginViewModel
) {

    val email by viewModel.email.collectAsState()
    val password by viewModel.password.collectAsState()
    val loginCorrecto by viewModel.loginCorrecto.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(loginCorrecto) {
        if (loginCorrecto) {
            onLoginCorrecto()
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

            Spacer(Modifier.height(48.dp))

            Text(
                text = "ZeroHaus",
                fontSize = 22.sp,
                color = Color(0xFF00A63E)
            )

            Spacer(Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(24.dp)) {

                    OutlinedTextField(
                        value = email,
                        onValueChange = viewModel::onEmailChange,
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = Color.Red, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {  },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00A63E)
                        )
                    ) {
                        Text("Iniciar sesión", color = Color.White)
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onRegistro,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Registrarse")
                    }

                    TextButton(onClick = onRecuperar) {
                        Text("¿Olvidaste tu contraseña?")
                    }
                }
            }
        }
    }
}
