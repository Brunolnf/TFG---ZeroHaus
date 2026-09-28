
package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.ViewModel.ChatViewModel
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

private fun formatTimestampChat(ts: Long, hoyTxt: String, ayerTxt: String): String {
    val hoy = Calendar.getInstance()
    val msg = Calendar.getInstance().apply { timeInMillis = ts }
    val sdfHora = SimpleDateFormat("HH:mm", Locale.getDefault())
    val sdfFecha = SimpleDateFormat("dd MMM", Locale.getDefault())
    return when {
        hoy.get(Calendar.YEAR) == msg.get(Calendar.YEAR) &&
        hoy.get(Calendar.DAY_OF_YEAR) == msg.get(Calendar.DAY_OF_YEAR) ->
            "$hoyTxt ${sdfHora.format(Date(ts))}"
        hoy.get(Calendar.YEAR) == msg.get(Calendar.YEAR) &&
        hoy.get(Calendar.DAY_OF_YEAR) - msg.get(Calendar.DAY_OF_YEAR) == 1 ->
            "$ayerTxt ${sdfHora.format(Date(ts))}"
        else -> sdfFecha.format(Date(ts))
    }
}

private val mediaEmojis = setOf("📷", "📎")

/**
 * Lista de conversaciones del usuario, con el último mensaje y los no leídos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    viewModel: ChatViewModel,
    onVolver: () -> Unit = {},
    onAbrirChat: (String) -> Unit = {}
) {
    val c = LocalCadenas.current
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val fondo = MaterialTheme.colorScheme.background
    val borde = MaterialTheme.colorScheme.outline
    val estado = viewModel.listaEstado
    val miUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    LaunchedEffect(Unit) { viewModel.cargarChats() }

    Scaffold(
        containerColor = fondo,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(c.tabMensajes, fontWeight = FontWeight.SemiBold)
                        Text("${estado.chats.size} ${c.cliConversaciones}", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, c.volver) }
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
                    Text(c.chatlNoConversaciones, color = gris)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        c.chatlVacioSub,
                        color = gris, fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(pv).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(estado.chats) { chat ->
                    val nombreOtro = chat.nombresParticipantes.entries
                        .firstOrNull { it.key != miUid }?.value ?: c.chatNombreFallback
                    val noLeidos = chat.noLeidosPor[miUid] ?: 0
                    val esMedia = mediaEmojis.any { chat.ultimoMensaje.startsWith(it) }

                    Card(
                        onClick = { onAbrirChat(chat.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            if (noLeidos > 0) 1.5.dp else 1.dp,
                            if (noLeidos > 0) verde.copy(0.4f) else borde
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar con indicador de no leídos
                            Box {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = CircleShape,
                                    color = verde.copy(0.12f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            nombreOtro.take(1).uppercase(),
                                            color = verde,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        )
                                    }
                                }
                                if (noLeidos > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = verde,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .align(Alignment.TopEnd)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                if (noLeidos > 9) "9+" else "$noLeidos",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(Modifier.weight(1f)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        nombreOtro,
                                        fontWeight = if (noLeidos > 0) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (chat.fechaUltimoMensaje > 0) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            formatTimestampChat(chat.fechaUltimoMensaje, c.comHoy, c.comAyer),
                                            color = if (noLeidos > 0) verde else gris,
                                            fontSize = 11.sp,
                                            fontWeight = if (noLeidos > 0) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    chat.ultimoMensaje.ifEmpty { c.chatSinMensajes },
                                    color = if (noLeidos > 0) MaterialTheme.colorScheme.onSurface else gris,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (noLeidos > 0) FontWeight.Medium else FontWeight.Normal,
                                    fontStyle = if (esMedia) FontStyle.Italic else FontStyle.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
