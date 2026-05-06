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
import com.example.zerohaus.ViewModel.LoginViewModel
import com.example.zerohaus.util.LocalCadenas

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginExitoso: () -> Unit,
    onIrARegistro: () -> Unit,
    onIrARecuperar: () -> Unit
) {
    val c = LocalCadenas.current
    val estado = viewModel.estado
    val verde = Color(0xFF16A34A)
    val fondo = Color(0xFFEEF8F5)
    val gris = Color(0xFF6B7280)
    val borde = Color(0xFFD1D5DB)
    var verContrasena by remember { mutableStateOf(false) }

    LaunchedEffect(estado.loginCorrecto) {
        if (estado.loginCorrecto) onLoginExitoso()
    }

    Scaffold(containerColor = fondo) { pv ->
        Column(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            ZeroHausLogo(size = 56.dp)
            Spacer(Modifier.height(10.dp))
            Text("ZeroHaus", color = verde, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(c.loginSlogan, color = gris, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(c.loginTitulo, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFF111827))
                    Spacer(Modifier.height(18.dp))

                    Text("Email", fontSize = 12.sp, color = Color(0xFF1F2937))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = estado.email,
                        onValueChange = { viewModel.cambiarEmail(it) },
                        placeholder = { Text(c.emailPlaceholder, color = gris) },
                        leadingIcon = { Icon(Icons.Default.MailOutline, null, tint = gris) },
                        isError = estado.email.isNotEmpty() && !estado.emailValido,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = borde,
                            focusedBorderColor = verde,
                            errorBorderColor = Color(0xFFDC2626)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (estado.email.isNotEmpty() && !estado.emailValido) {
                        Text(c.emailError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(c.contrasena, fontSize = 12.sp, color = Color(0xFF1F2937))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = estado.contrasena,
                        onValueChange = { viewModel.cambiarContrasena(it) },
                        placeholder = { Text(c.contrasenaPlaceholder, color = gris) },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = gris) },
                        trailingIcon = {
                            IconButton(onClick = { verContrasena = !verContrasena }) {
                                Icon(
                                    if (verContrasena) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (verContrasena) c.ocultar else c.mostrar,
                                    tint = gris
                                )
                            }
                        },
                        visualTransformation = if (verContrasena) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = estado.contrasena.isNotEmpty() && !estado.passwordValida,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = borde,
                            focusedBorderColor = verde,
                            errorBorderColor = Color(0xFFDC2626)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (estado.contrasena.isNotEmpty() && !estado.passwordValida) {
                        Text(c.contrasenaError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    }

                    Spacer(Modifier.height(6.dp))

                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(onClick = onIrARecuperar, contentPadding = PaddingValues(0.dp)) {
                            Text(c.loginOlvidaste, color = verde, fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.iniciarSesion() },
                        enabled = estado.formularioValido && !estado.cargando,
                        colors = ButtonDefaults.buttonColors(containerColor = verde),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (estado.cargando) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(c.loginBoton, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onIrARegistro,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(verde)
                        )
                    ) {
                        Text(c.loginCrearCuenta, color = verde, fontWeight = FontWeight.SemiBold)
                    }

                    estado.error?.let {
                        Spacer(Modifier.height(14.dp))
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2))
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(it, color = Color(0xFF991B1B), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("© 2026 ZeroHaus", color = gris.copy(0.6f), fontSize = 11.sp)
            Spacer(Modifier.height(20.dp))
        }
    }
}
