package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontStyle
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
    onVolver: () -> Unit = {},
    onContactar: (tecnicoUid: String, tecnicoNombre: String) -> Unit = { _, _ -> }
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
                title = {
                    Column {
                        Text("Perfil del técnico", fontWeight = FontWeight.SemiBold)
                        if (estado.tecnico != null) {
                            Text(estado.tecnico!!.nombre, color = gris, fontSize = 12.sp)
                        }
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
        } else if (estado.tecnico == null) {
            Box(Modifier.fillMaxSize().padding(pv), contentAlignment = Alignment.Center) {
                Text("Técnico no encontrado", color = gris)
            }
        } else {
            val t = estado.tecnico!!
            LazyColumn(
                modifier = Modifier
                    .padding(pv)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // ---- HERO CARD ----
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, borde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Avatar 80dp
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                color = verde.copy(alpha = 0.12f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        t.nombre.take(1).uppercase(),
                                        color = verde,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 32.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Nombre
                            Text(t.nombre, fontWeight = FontWeight.Bold, fontSize = 22.sp)

                            // Ciudad
                            if (t.ciudad.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        null,
                                        tint = gris,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(t.ciudad, color = gris, fontSize = 14.sp)
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Estrellas + número de rating
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilaEstrellas(t.rating, tamano = 22.dp)
                                Text(
                                    "%.1f".format(t.rating),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text("${t.opiniones} valoraciones", color = gris, fontSize = 13.sp)

                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = borde, thickness = 1.dp)
                            Spacer(Modifier.height(16.dp))

                            // Fila de estadísticas
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem(
                                    valor = "${t.proyectosCompletados}",
                                    label = "Proyectos",
                                    verde = verde,
                                    gris = gris
                                )
                                // Divisor vertical
                                Box(
                                    Modifier
                                        .height(40.dp)
                                        .width(1.dp)
                                        .background(borde)
                                )
                                StatItem(
                                    valor = "${t.opiniones}",
                                    label = "Opiniones",
                                    verde = verde,
                                    gris = gris
                                )
                            }
                        }
                    }
                }

                // ---- DESCRIPCIÓN ----
                if (t.descripcion.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, borde),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                // Borde izquierdo verde (3dp)
                                Box(
                                    Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .background(verde)
                                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                                )
                                Text(
                                    t.descripcion,
                                    color = Color(0xFF374151),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(14.dp),
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }

                // ---- ESPECIALIDADES ----
                if (t.especialidades.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, borde),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text("Especialidades", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    t.especialidades.forEach { esp ->
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = verde.copy(alpha = 0.08f)
                                        ) {
                                            Text(
                                                esp,
                                                color = verde,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- BOTONES DE CONTACTO ----
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onContactar(t.uid, t.nombre) },
                            colors = ButtonDefaults.buttonColors(containerColor = verde),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Chat, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Contactar", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        if (t.telefono.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { /* Llamar — manejado externamente si se necesita */ },
                                border = BorderStroke(1.dp, verde),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = verde),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Phone, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Llamar", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // ---- BOTÓN VALORAR ----
                if (estado.puedeValorar && !estado.yaValorado) {
                    item {
                        OutlinedButton(
                            onClick = { mostrarFormResena = true },
                            border = BorderStroke(1.dp, verde),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = verde)
                        ) {
                            Icon(Icons.Default.Star, null, tint = verde, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Escribir valoración", fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else if (!estado.yaValorado && !estado.cargando) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
                            border = BorderStroke(1.dp, borde),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Info, null, tint = gris, modifier = Modifier.size(18.dp))
                                Text(
                                    "Para valorar a este técnico primero debes completar una reforma con él desde la sección Presupuestos.",
                                    color = gris,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // ---- ÉXITO RESEÑA ----
                if (estado.exitoResena) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5))
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = verde)
                                Spacer(Modifier.width(8.dp))
                                Text("Valoración publicada", color = Color(0xFF065F46))
                            }
                        }
                    }
                }

                // ---- SECCIÓN VALORACIONES ----
                item {
                    Text(
                        "Valoraciones (${estado.resenas.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }

                if (estado.resenas.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.StarBorder,
                                    null,
                                    tint = gris.copy(0.4f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("Aún no hay valoraciones", color = gris)
                            }
                        }
                    }
                }

                items(estado.resenas) { r ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, borde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            // Fila: avatar + nombre + fecha
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Avatar del usuario 36dp
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    color = gris.copy(alpha = 0.15f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            r.nombreUsuario.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = gris
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(r.nombreUsuario, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        sdf.format(Date(r.fecha)),
                                        color = gris,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            FilaEstrellas(r.puntuacion.toDouble(), tamano = 16.dp)
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

    // ---- DIÁLOGO RESEÑA ----
    if (mostrarFormResena) {
        val labelColor = when (puntuacion) {
            5 -> Color(0xFF16A34A)
            4 -> Color(0xFF2563EB)
            3 -> Color(0xFFD97706)
            2 -> Color(0xFFDC2626)
            else -> Color(0xFF7F1D1D)
        }
        val labelTexto = when (puntuacion) {
            5 -> "Excelente"
            4 -> "Bueno"
            3 -> "Regular"
            2 -> "Malo"
            else -> "Muy malo"
        }
        AlertDialog(
            onDismissRequest = { mostrarFormResena = false },
            title = { Text("Valorar técnico", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Puntuación", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    // Estrellas grandes (44dp) clickables
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { i ->
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (puntuacion >= i) Color(0xFFFFC107) else Color(0xFFBDBDBD),
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable { puntuacion = i }
                            )
                        }
                    }
                    // Etiqueta de valoración
                    Text(
                        labelTexto,
                        color = labelColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = comentario,
                        onValueChange = { comentario = it },
                        label = { Text("Comentario (opcional)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    if (estado.enviandoResena) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF16A34A))
                    }
                    estado.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.publicarResena(tecnicoId, puntuacion, comentario)
                        mostrarFormResena = false
                        comentario = ""
                        puntuacion = 5
                    },
                    enabled = !estado.enviandoResena,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) { Text("Publicar", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { mostrarFormResena = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun StatItem(valor: String, label: String, verde: Color, gris: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = verde)
        Spacer(Modifier.height(2.dp))
        Text(label, color = gris, fontSize = 12.sp)
    }
}
