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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.ClienteResumen
import com.example.zerohaus.ViewModel.MisClientesTecnicoViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisClientesTecnicoScreen(
    viewModel: MisClientesTecnicoViewModel,
    onVolver: () -> Unit = {},
    onAbrirChat: (String) -> Unit = {}
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val estado = viewModel.estado

    LaunchedEffect(Unit) { viewModel.cargar() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mis clientes", fontWeight = FontWeight.SemiBold)
                        Text("${estado.clientes.size} clientes", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
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
                        Text("Aún no tienes clientes", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Cuando un cliente te escriba o te envíe una solicitud, aparecerá aquí.",
                            color = gris, fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(estado.clientes) { c -> ClienteCard(c, onAbrirChat) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClienteCard(c: ClienteResumen, onAbrirChat: (String) -> Unit) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

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
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (c.solicitudes > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(verde.copy(0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("${c.solicitudes} solicitudes", color = verde, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (c.activas > 0) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFEF3C7))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("${c.activas} activas", color = Color(0xFF92400E), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (c.fechaUltima > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(sdf.format(Date(c.fechaUltima)), color = gris, fontSize = 10.sp)
                    }
                }
            }
            if (c.chatId.isNotBlank()) {
                IconButton(onClick = { onAbrirChat(c.chatId) }) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Abrir chat", tint = verde)
                }
            }
        }
    }
}
