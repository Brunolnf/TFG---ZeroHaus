package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.PerfilTecnicoViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilTecnicoScreen(
    viewModel: PerfilTecnicoViewModel,
    tecnicoId: String,
    onVolver: () -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)
    val amarillo = Color(0xFFFFC107)
    val estado = viewModel.estado
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    var mostrarFormResena by remember { mutableStateOf(false) }
    var puntuacion by remember { mutableIntStateOf(5) }
    var comentario by remember { mutableStateOf("") }

    LaunchedEffect(tecnicoId) { viewModel.cargarTecnico(tecnicoId) }

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = { Text("Perfil del técnico", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }
            )
        }
    ) { pv ->
        if (estado.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = verde) }
        } else if (estado.tecnico == null) {
            Box(Modifier.fillMaxSize().padding(pv), contentAlignment = Alignment.Center) { Text("Técnico no encontrado", color = gris) }
        } else {
            val t = estado.tecnico!!
            LazyColumn(
                modifier = Modifier.padding(pv).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info del técnico
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, borde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(t.nombre, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(5) { i ->
                                    Icon(Icons.Default.Star, null, tint = if (t.rating >= (i + 1) - 0.01) amarillo else Color(0xFFBDBDBD), modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("${t.rating} (${t.opiniones} opiniones)", color = gris)
                            }
                            Spacer(Modifier.height(12.dp))
                            if (t.descripcion.isNotEmpty()) {
                                Text(t.descripcion, color = gris, fontSize = 14.sp)
                                Spacer(Modifier.height(12.dp))
                            }
                            Text("Especialidades", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                t.especialidades.forEach { esp ->
                                    AssistChip(onClick = {}, label = { Text(esp, fontSize = 12.sp) }, colors = AssistChipDefaults.assistChipColors(containerColor = verde.copy(0.1f), labelColor = verde))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("${t.proyectosCompletados} proyectos completados", color = gris, fontSize = 13.sp)
                            Text("${t.distanciaKm} km de distancia", color = gris, fontSize = 13.sp)
                            if (t.telefono.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Phone, null, tint = gris, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(t.telefono, color = gris, fontSize = 13.sp)
                                }
                            }
                            if (t.emailContacto.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MailOutline, null, tint = gris, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(t.emailContacto, color = gris, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // Botón escribir reseña
                if (!estado.yaValorado) {
                    item {
                        Button(onClick = { mostrarFormResena = true }, colors = ButtonDefaults.buttonColors(containerColor = verde), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Star, null); Spacer(Modifier.width(8.dp))
                            Text("Escribir valoración", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (estado.exitoResena) {
                    item {
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5))) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = verde); Spacer(Modifier.width(8.dp))
                                Text("Valoración publicada", color = Color(0xFF065F46))
                            }
                        }
                    }
                }

                // Reseñas
                item { Text("Valoraciones (${estado.resenas.size})", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                if (estado.resenas.isEmpty()) { item { Text("Aún no hay valoraciones", color = gris) } }

                items(estado.resenas) { r ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, borde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(r.nombreUsuario, fontWeight = FontWeight.SemiBold)
                                Text(sdf.format(Date(r.fecha)), color = gris, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row {
                                repeat(5) { i -> Icon(Icons.Default.Star, null, tint = if (r.puntuacion >= i + 1) amarillo else Color(0xFFBDBDBD), modifier = Modifier.size(16.dp)) }
                            }
                            if (r.comentario.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(r.comentario, color = Color(0xFF374151), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogo reseña
    if (mostrarFormResena) {
        AlertDialog(
            onDismissRequest = { mostrarFormResena = false },
            title = { Text("Valorar técnico", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Puntuación", fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..5).forEach { i ->
                            IconButton(onClick = { puntuacion = i }) {
                                Icon(Icons.Default.Star, null, tint = if (puntuacion >= i) amarillo else Color(0xFFBDBDBD), modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    OutlinedTextField(value = comentario, onValueChange = { comentario = it }, label = { Text("Comentario (opcional)") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), minLines = 3)
                    if (estado.enviandoResena) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = verde) }
                    estado.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.publicarResena(tecnicoId, puntuacion, comentario); mostrarFormResena = false; comentario = ""; puntuacion = 5 }, enabled = !estado.enviandoResena, colors = ButtonDefaults.buttonColors(containerColor = verde)) { Text("Publicar", color = Color.White) }
            },
            dismissButton = { OutlinedButton(onClick = { mostrarFormResena = false }) { Text("Cancelar") } }
        )
    }
}