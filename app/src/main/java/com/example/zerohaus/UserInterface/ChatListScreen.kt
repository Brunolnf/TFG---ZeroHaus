
package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.ChatViewModel
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    viewModel: ChatViewModel,
    onVolver: () -> Unit = {},
    onAbrirChat: (String) -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)
    val estado = viewModel.estado
    val miUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val sdf = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    LaunchedEffect(Unit) { viewModel.cargarChats() }

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mensajes", fontWeight = FontWeight.SemiBold)
                        Text("${estado.chats.size} conversaciones", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                }
            )
        }
    ) { pv ->
        if (estado.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = verde)
            }
        } else if (estado.chats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pv), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MailOutline, null, tint = gris, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No tienes conversaciones", color = gris)
                    Spacer(Modifier.height(8.dp))
                    Text("Contacta un técnico para iniciar un chat", color = gris, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(pv).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(estado.chats) { chat ->
                    val nombreOtro = chat.nombresParticipantes.entries.firstOrNull { it.key != miUid }?.value ?: "Chat"
                    val noLeidos = chat.noLeidosPor[miUid] ?: 0

                    Card(
                        onClick = { onAbrirChat(chat.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, borde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                color = verde.copy(0.12f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        nombreOtro.take(1).uppercase(),
                                        color = verde,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(Modifier.weight(1f)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        nombreOtro,
                                        fontWeight = if (noLeidos > 0) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    if (chat.fechaUltimoMensaje > 0) {
                                        Text(
                                            sdf.format(Date(chat.fechaUltimoMensaje)),
                                            color = gris,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        chat.ultimoMensaje.ifEmpty { "Sin mensajes" },
                                        color = if (noLeidos > 0) Color(0xFF111827) else gris,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (noLeidos > 0) FontWeight.Medium else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (noLeidos > 0) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = verde,
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    "$noLeidos",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
