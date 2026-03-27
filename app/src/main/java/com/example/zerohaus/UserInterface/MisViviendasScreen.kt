
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
import com.example.zerohaus.ViewModel.ViviendasViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisViviendasScreen(
    viewModel: ViviendasViewModel,
    onVolver: () -> Unit = {},
    onNuevoPreestudio: () -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)
    val estado = viewModel.estado

    var confirmarEliminar by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(estado.mensaje, estado.error) {
        estado.mensaje?.let { snackbarHostState.showSnackbar(it); viewModel.limpiarMensaje() }
        estado.error?.let { snackbarHostState.showSnackbar(it); viewModel.limpiarMensaje() }
    }
    LaunchedEffect(Unit) { viewModel.cargarViviendas() }

    Scaffold(
        containerColor = fondo,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mis viviendas", fontWeight = FontWeight.SemiBold)
                        Text("${estado.viviendas.size} viviendas registradas", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNuevoPreestudio,
                containerColor = verde,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, "Añadir vivienda")
            }
        }
    ) { pv ->
        if (estado.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = verde)
            }
        } else if (estado.viviendas.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pv), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Home, null, tint = gris, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No tienes viviendas registradas", color = gris)
                    Spacer(Modifier.height(8.dp))
                    Text("Realiza un preestudio para añadir una", color = gris, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(pv).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(estado.viviendas) { v ->
                    val esSeleccionada = v.id == estado.viviendaSeleccionada?.id
                    Card(
                        onClick = { viewModel.seleccionarVivienda(v) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (esSeleccionada) Color(0xFFD1FAE5) else Color.White
                        ),
                        border = BorderStroke(1.dp, if (esSeleccionada) verde else borde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(v.nombre, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                        if (esSeleccionada) {
                                            Spacer(Modifier.width(8.dp))
                                            AssistChip(
                                                onClick = {},
                                                label = { Text("Activa", fontSize = 10.sp) },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = verde.copy(0.15f),
                                                    labelColor = verde
                                                )
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    if (v.direccion.isNotEmpty()) {
                                        Text(v.direccion, color = gris, fontSize = 13.sp)
                                    }
                                }
                                IconButton(onClick = { confirmarEliminar = v.id }) {
                                    Icon(Icons.Default.Delete, "Eliminar", tint = Color(0xFFDC2626))
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("${v.superficie} m²", color = gris, fontSize = 13.sp)
                                Text("Año ${v.anioConstruccion}", color = gris, fontSize = 13.sp)
                                Text(v.orientacion, color = gris, fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(v.calefaccion, color = gris, fontSize = 12.sp)
                                Text(v.tipoVentanas, color = gris, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo confirmar eliminación
    if (confirmarEliminar != null) {
        AlertDialog(
            onDismissRequest = { confirmarEliminar = null },
            title = { Text("Eliminar vivienda", fontWeight = FontWeight.SemiBold) },
            text = { Text("¿Estás seguro de que quieres eliminar esta vivienda? Se perderán todos los datos asociados.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.eliminarVivienda(confirmarEliminar!!)
                        confirmarEliminar = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Eliminar", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmarEliminar = null }) { Text("Cancelar") }
            }
        )
    }
}

