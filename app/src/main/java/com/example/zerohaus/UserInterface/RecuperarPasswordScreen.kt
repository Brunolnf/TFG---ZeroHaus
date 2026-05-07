package com.example.zerohaus.UserInterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.LoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecuperarPasswordScreen(
    viewModel: LoginViewModel,
    onVolver: () -> Unit
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val fondo = MaterialTheme.colorScheme.background

    var email by remember { mutableStateOf("") }
    var enviado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }

    val emailValido = email.contains("@") && email.substringAfter("@").contains(".")

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = fondo)
            )
        }
    ) { pv ->
        Column(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            if (enviado) {
                // ── Estado éxito ──
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = verde.copy(alpha = 0.10f),
                    modifier = Modifier.size(88.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MarkEmailRead,
                            null,
                            tint = verde,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Correo enviado",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Hemos enviado un enlace a\n$email\npara que puedas restablecer tu contraseña.",
                    color = gris,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Revisa también tu carpeta de spam.",
                    color = gris.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onVolver,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = verde),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Volver al inicio de sesión", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = {
                    enviado = false
                    error = null
                }) {
                    Text("¿No lo has recibido? Reenviar", color = verde, fontSize = 13.sp)
                }
            } else {
                // ── Formulario ──
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = verde.copy(alpha = 0.10f),
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.LockReset,
                            null,
                            tint = verde,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("¿Olvidaste tu contraseña?", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Introduce tu correo electrónico y te enviaremos un enlace para restablecerla.",
                    color = gris,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(28.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim(); error = null },
                    label = { Text("Correo electrónico") },
                    placeholder = { Text("ejemplo@correo.com") },
                    leadingIcon = { Icon(Icons.Default.MailOutline, null) },
                    singleLine = true,
                    isError = error != null || (email.isNotEmpty() && !emailValido),
                    supportingText = {
                        when {
                            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                            email.isNotEmpty() && !emailValido ->
                                Text("Introduce un correo válido", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = verde,
                        focusedLabelColor = verde,
                        cursorColor = verde
                    )
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        enviando = true
                        error = null
                        viewModel.recuperarContrasena(email) { result ->
                            enviando = false
                            result
                                .onSuccess { enviado = true }
                                .onFailure { error = it.message }
                        }
                    },
                    enabled = emailValido && !enviando,
                    colors = ButtonDefaults.buttonColors(containerColor = verde),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (enviando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Enviando…", color = Color.White, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Enviar enlace de recuperación", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onVolver) {
                    Text("Volver al inicio de sesión", color = gris)
                }
            }
        }
    }
}
