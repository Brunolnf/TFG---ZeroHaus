package com.example.zerohaus.UserInterface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.Formato
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.ViewModel.ClienteResumen
import com.example.zerohaus.ViewModel.MisClientesTecnicoViewModel
import java.util.*

/**
 * Clientes del profesional: personas que le han escrito, con acceso directo al chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisClientesTecnicoScreen(
    viewModel: MisClientesTecnicoViewModel,
    onVolver: () -> Unit = {},
    onAbrirChat: (String) -> Unit = {},
    mostrarVolver: Boolean = true
) {
    val c = LocalCadenas.current
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val estado = viewModel.estado

    LaunchedEffect(Unit) { viewModel.cargar() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(c.cliTitulo, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${estado.clientes.size} ${c.cliConversaciones}",
                            color = gris, fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    if (mostrarVolver) {
                        IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, c.volver) }
                    }
                }
            )
        }
    ) { pv ->
        Box(Modifier.padding(pv).fillMaxSize()) {
            when {
                estado.cargando -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                estado.clientes.isEmpty() -> {
                    Column(
                        Modifier.fillMaxSize().padding(40.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.PeopleOutline, null, tint = gris, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(c.cliVacioTitulo, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            c.cliVacioSub,
                            color = gris, fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(estado.clientes, key = { it.uid }) { c ->
                            ClienteCard(c) {
                                viewModel.abrirChatConCliente(c) { chatId -> onAbrirChat(chatId) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClienteCard(c: ClienteResumen, onAbrirChat: () -> Unit) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val sdf = remember(AppEstado.idioma) { Formato.fechas("dd/MM/yyyy") }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(verde.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    c.nombre.take(1).ifBlank { "?" }.uppercase(),
                    color = verde, fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.nombre, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (c.ultimoMensaje.isNotBlank()) {
                    Text(
                        c.ultimoMensaje,
                        color = gris, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (c.fechaUltima > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(sdf.format(Date(c.fechaUltima)), color = gris, fontSize = 10.sp)
                }
            }
            IconButton(onClick = onAbrirChat) {
                Icon(Icons.AutoMirrored.Filled.Send, LocalCadenas.current.comAbrirChat, tint = verde)
            }
        }
    }
}
