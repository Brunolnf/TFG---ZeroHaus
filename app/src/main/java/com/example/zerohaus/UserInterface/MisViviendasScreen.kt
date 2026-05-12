package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.zerohaus.Modelos.Vivienda
import com.example.zerohaus.ViewModel.ViviendasViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisViviendasScreen(
    viewModel: ViviendasViewModel,
    onVolver: () -> Unit = {},
    onNuevoPreestudio: () -> Unit = {}
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val fondo = MaterialTheme.colorScheme.background
    val borde = MaterialTheme.colorScheme.outline
    val estado = viewModel.estado

    var confirmarEliminar by remember { mutableStateOf<String?>(null) }
    var viviendaEditando by remember { mutableStateOf<Vivienda?>(null) }

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
                    Text("No tienes viviendas registradas", color = gris, fontWeight = FontWeight.SemiBold)
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
                            containerColor = if (esSeleccionada) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
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
                                    if (v.direccion.isNotEmpty()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(v.direccion, color = gris, fontSize = 13.sp)
                                    }
                                }
                                Row {
                                    IconButton(onClick = { viviendaEditando = v }) {
                                        Icon(Icons.Default.Edit, "Editar", tint = verde)
                                    }
                                    IconButton(onClick = { confirmarEliminar = v.id }) {
                                        Icon(Icons.Default.Delete, "Eliminar", tint = Color(0xFFDC2626))
                                    }
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
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (confirmarEliminar != null) {
        AlertDialog(
            onDismissRequest = { confirmarEliminar = null },
            title = { Text("Eliminar vivienda", fontWeight = FontWeight.SemiBold) },
            text = { Text("¿Estás seguro? Se perderán todos los datos asociados.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.eliminarVivienda(confirmarEliminar!!); confirmarEliminar = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Eliminar", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmarEliminar = null }) { Text("Cancelar") }
            }
        )
    }

    viviendaEditando?.let { v ->
        EditarViviendaDialog(
            vivienda = v,
            onDismiss = { viviendaEditando = null },
            onGuardar = { actualizada ->
                viewModel.guardarVivienda(actualizada)
                viviendaEditando = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditarViviendaDialog(
    vivienda: Vivienda,
    onDismiss: () -> Unit,
    onGuardar: (Vivienda) -> Unit
) {
    val verde = MaterialTheme.colorScheme.primary

    var nombre by remember { mutableStateOf(vivienda.nombre) }
    var superficie by remember { mutableStateOf(vivienda.superficie.toString()) }
    var anio by remember { mutableStateOf(vivienda.anioConstruccion.toString()) }
    var direccion by remember { mutableStateOf(vivienda.direccion) }
    var ventanas by remember { mutableStateOf(vivienda.tipoVentanas) }
    var aislamiento by remember { mutableStateOf(vivienda.aislamiento) }
    var calefaccion by remember { mutableStateOf(vivienda.calefaccion) }
    var acs by remember { mutableStateOf(vivienda.acs) }
    var orientacion by remember { mutableStateOf(vivienda.orientacion) }

    val optsVentanas = listOf("Vidrio simple", "Doble acristalamiento", "Triple")
    val optsAislamiento = listOf("Sin aislamiento", "Aislamiento parcial", "Aislamiento completo")
    val optsCalefaccion = listOf("Caldera de gas", "Eléctrica", "Aerotermia", "Biomasa", "Sin calefacción")
    val optsAcs = listOf("Gas", "Eléctrico", "Solar térmica", "Aerotermia", "Sin ACS")
    val optsOrientacion = listOf("Norte", "Sur", "Este", "Oeste", "Noreste", "Noroeste", "Sureste", "Suroeste")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Editar vivienda", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Cerrar")
                    }
                }

                HorizontalDivider()

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = superficie,
                            onValueChange = { if (it.all(Char::isDigit)) superficie = it },
                            label = { Text("Superficie (m²)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = anio,
                            onValueChange = { if (it.all(Char::isDigit) && it.length <= 4) anio = it },
                            label = { Text("Año construcción") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = direccion,
                        onValueChange = { direccion = it },
                        label = { Text("Dirección") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownField("Tipo de ventanas", ventanas, optsVentanas) { ventanas = it }
                    DropdownField("Aislamiento", aislamiento, optsAislamiento) { aislamiento = it }
                    DropdownField("Calefacción", calefaccion, optsCalefaccion) { calefaccion = it }
                    DropdownField("ACS", acs, optsAcs) { acs = it }
                    DropdownField("Orientación", orientacion, optsOrientacion) { orientacion = it }
                }

                HorizontalDivider()

                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Cancelar") }
                    Button(
                        onClick = {
                            onGuardar(
                                vivienda.copy(
                                    nombre = nombre.trim(),
                                    superficie = superficie.toIntOrNull() ?: vivienda.superficie,
                                    anioConstruccion = anio.toIntOrNull() ?: vivienda.anioConstruccion,
                                    direccion = direccion.trim(),
                                    tipoVentanas = ventanas,
                                    aislamiento = aislamiento,
                                    calefaccion = calefaccion,
                                    acs = acs,
                                    orientacion = orientacion
                                )
                            )
                        },
                        enabled = nombre.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = verde),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Guardar", color = Color.White) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(label: String, valor: String, opciones: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = valor,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            opciones.forEach { op ->
                DropdownMenuItem(
                    text = { Text(op) },
                    onClick = { onSelect(op); expanded = false }
                )
            }
        }
    }
}
