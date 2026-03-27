package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.zerohaus.ViewModel.TecnicosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TecnicosScreen(
    viewModel: TecnicosViewModel,
    onVolver: () -> Unit = {},
    onVerPerfil: (String) -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)
    val estado = viewModel.estado
    val filtrados = viewModel.tecnicosFiltrados()

    var mostrarFiltros by remember { mutableStateOf(false) }

    val especialidades = listOf(
        "Aislamiento", "Ventanas", "Calefacción", "Fotovoltaica", "Aerotermia",
        "Auditorías", "Rehabilitación", "Biomasa", "Certificación", "Consultoría"
    )

    LaunchedEffect(Unit) { viewModel.cargarTecnicos() }

    // Snackbar para mensajes
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(estado.mensajeExito, estado.error) {
        estado.mensajeExito?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.limpiarMensaje()
        }
        estado.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.limpiarMensaje()
        }
    }

    Scaffold(
        containerColor = fondo,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Técnicos certificados", fontWeight = FontWeight.SemiBold)
                        Text("${filtrados.size} profesionales disponibles", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.Default.ArrowBack, "Volver") }
                }
            )
        }
    ) { pv ->
        if (estado.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = verde)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(pv).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = estado.busqueda,
                            onValueChange = { viewModel.cambiarBusqueda(it) },
                            placeholder = { Text("Buscar por nombre o especialidad…") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = borde, focusedBorderColor = borde,
                                unfocusedContainerColor = Color.White, focusedContainerColor = Color.White
                            )
                        )
                        OutlinedButton(
                            onClick = { mostrarFiltros = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, borde)
                        ) {
                            Icon(Icons.Default.AccountCircle, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Filtros")
                        }
                        DropdownMenu(expanded = mostrarFiltros, onDismissRequest = { mostrarFiltros = false }) {
                            DropdownMenuItem(text = { Text("Sin filtro") }, onClick = { viewModel.cambiarFiltro(null); mostrarFiltros = false })
                            especialidades.forEach { esp ->
                                DropdownMenuItem(text = { Text(esp) }, onClick = { viewModel.cambiarFiltro(esp); mostrarFiltros = false })
                            }
                        }
                    }
                    estado.filtro?.let {
                        Spacer(Modifier.height(10.dp))
                        AssistChip(
                            onClick = { viewModel.cambiarFiltro(null) },
                            label = { Text("Filtro: $it ✕") },
                            colors = AssistChipDefaults.assistChipColors(containerColor = verde.copy(0.12f), labelColor = verde)
                        )
                    }
                }

                if (filtrados.isEmpty()) {
                    item {
                        Text("No se encontraron técnicos", color = gris, modifier = Modifier.padding(16.dp))
                    }
                }

                items(filtrados) { t ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, borde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(t.nombre, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilaEstrellas(rating = t.rating)
                                Spacer(Modifier.width(8.dp))
                                Text("${t.rating} (${t.opiniones} opiniones)", color = gris, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("${t.distanciaKm} km", color = gris, fontSize = 12.sp)
                            Text("Especialidades: ${t.especialidades.joinToString()}", color = gris, fontSize = 12.sp)
                            Text("${t.proyectosCompletados} proyectos completados", color = gris, fontSize = 12.sp)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { onVerPerfil(t.id) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, verde),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = verde)
                                ) { Text("Ver perfil", fontWeight = FontWeight.SemiBold) }
                                Button(
                                    onClick = { viewModel.solicitarPresupuesto(t) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = verde)
                                ) { Text("Solicitar presupuesto", color = Color.White, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaEstrellas(rating: Double) {
    val amarillo = Color(0xFFFFC107)
    val gris = Color(0xFFBDBDBD)
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            Icon(
                Icons.Default.Star, null,
                tint = if (rating >= (i + 1) - 0.01) amarillo else gris,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}