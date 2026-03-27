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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.LoginViewModel

@Composable
fun RecuperarPasswordScreen(
    viewModel: LoginViewModel,
    onVolver: () -> Unit
) {
    val verde = Color(0xFF16A34A)
    val fondo = Color(0xFFEEF8F5)
    var email by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }

    Scaffold(containerColor = fondo) { pv ->
        Column(
            modifier = Modifier.padding(pv).fillMaxSize().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            Icon(Icons.Default.Lock, null, tint = verde, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("Recuperar contraseña", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text("Introduce tu email y te enviaremos un enlace para restablecer tu contraseña",
                color = Color(0xFF6B7280), fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("tu@email.com") },
                leadingIcon = { Icon(Icons.Default.MailOutline, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    enviando = true
                    error = null
                    mensaje = null
                    viewModel.recuperarContrasena(email) { result ->
                        enviando = false
                        result
                            .onSuccess { mensaje = "Email enviado correctamente. Revisa tu bandeja." }
                            .onFailure { error = it.message }
                    }
                },
                enabled = email.isNotEmpty() && !enviando,
                colors = ButtonDefaults.buttonColors(containerColor = verde),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (enviando) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Enviar enlace", color = Color.White)
            }

            mensaje?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = verde)
            }
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onVolver) {
                Text("Volver al inicio de sesión", color = verde)
            }
        }
    }
}