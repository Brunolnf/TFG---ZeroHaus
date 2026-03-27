
package com.example.zerohaus.UserInterface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.ChatViewModel
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    chatId: String,
    onVolver: () -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val estado = viewModel.estado
    val miUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var textoMensaje by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatId) { viewModel.abrirChat(chatId) }
    DisposableEffect(Unit) { onDispose { viewModel.cerrarChat() } }

    // Auto-scroll al último mensaje
    LaunchedEffect(estado.mensajes.size) {
        if (estado.mensajes.isNotEmpty()) {
            listState.animateScrollToItem(estado.mensajes.size - 1)
        }
    }

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = verde.copy(0.12f)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    estado.nombreOtro.take(1).uppercase(),
                                    color = verde,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(estado.nombreOtro, fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                }
            )
        },
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textoMensaje,
                        onValueChange = { textoMensaje = it },
                        placeholder = { Text("Escribe un mensaje...") },
                        singleLine = false,
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFFE5E7EB),
                            focusedBorderColor = verde
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (textoMensaje.isNotBlank()) {
                                viewModel.enviarMensaje(textoMensaje.trim())
                                textoMensaje = ""
                            }
                        },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(verde)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = Color.White)
                    }
                }
            }
        }
    ) { pv ->
        LazyColumn(
            modifier = Modifier.padding(pv).fillMaxSize().padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(estado.mensajes) { msg ->
                val esMio = msg.emisorUid == miUid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (esMio) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (esMio) 16.dp else 4.dp,
                            bottomEnd = if (esMio) 4.dp else 16.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (esMio) verde else Color.White
                        ),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                msg.texto,
                                color = if (esMio) Color.White else Color(0xFF111827),
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                sdf.format(Date(msg.fecha)),
                                color = if (esMio) Color.White.copy(0.7f) else gris,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
