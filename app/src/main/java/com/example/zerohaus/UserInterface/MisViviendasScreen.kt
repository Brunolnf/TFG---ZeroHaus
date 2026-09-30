package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.zerohaus.Modelos.Vivienda
import com.example.zerohaus.Repositorios.AlgoritmoEnergetico
import com.example.zerohaus.Util.Formato
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.Util.TextosEnergia
import com.example.zerohaus.ViewModel.ViviendasViewModel

/**
 * Viviendas del propietario: listar, seleccionar la activa, editar y eliminar.
 */
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
    val c = LocalCadenas.current

    var confirmarEliminar by remember { mutableStateOf<String?>(null) }
    var viviendaEditando by remember { mutableStateOf<Vivienda?>(null) }

    LaunchedEffect(Unit) { viewModel.cargarViviendas() }

    Scaffold(
        containerColor = fondo,
        snackbarHost = {
            ZeroToast(
                mensaje   = estado.mensaje ?: estado.error,
                tipo      = if (estado.error != null) ToastTipo.ERROR else ToastTipo.EXITO,
                alOcultar = { viewModel.limpiarMensaje() }
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(c.vivTitulo, fontWeight = FontWeight.SemiBold)
                        Text("${estado.viviendas.size} ${c.vivRegistradas}", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, c.volver) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNuevoPreestudio,
                containerColor = verde,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, c.vivAnadir)
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
                    Text(c.vivVacio, color = gris, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(c.vivVacioSub, color = gris, fontSize = 13.sp)
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
                                        Text(
                                            text = v.nombre,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (esSeleccionada) {
                                            Spacer(Modifier.width(8.dp))
                                            EstadoChip(c.vivActiva, verde, fontSize = 10)
                                        }
                                    }
                                    if (v.direccion.isNotEmpty()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(v.direccion, color = gris, fontSize = 13.sp)
                                    }
                                }
                                Row {
                                    IconButton(onClick = { viviendaEditando = v }) {
                                        Icon(Icons.Default.Edit, c.vivEditar, tint = verde)
                                    }
                                    IconButton(onClick = { confirmarEliminar = v.id }) {
                                        Icon(Icons.Default.Delete, c.comEliminar, tint = Color(0xFFDC2626))
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("${v.superficie} m²", color = gris, fontSize = 13.sp)
                                Text("${c.vivAnio} ${v.anioConstruccion}", color = gris, fontSize = 13.sp)
                                Text(TextosEnergia.opcion(v.orientacion, c), color = gris, fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(TextosEnergia.opcion(v.calefaccion, c), color = gris, fontSize = 12.sp)
                                Text(TextosEnergia.opcion(v.tipoVentanas, c), color = gris, fontSize = 12.sp)
                            }
                            // Con factura de la luz: sus informes usan el precio real
                            if (v.precioLuzFactura > 0) {
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bolt, null, tint = Color(0xFFCA8A04), modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "${c.infFactTitulo} · ${Formato.formatMoneda(v.precioLuzFactura, 3)}/kWh",
                                        color = gris, fontSize = 12.sp
                                    )
                                }
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
            title = { Text(c.vivEliminarTitulo, fontWeight = FontWeight.SemiBold) },
            text = { Text(c.vivEliminarMsg) },
            confirmButton = {
                Button(
                    onClick = { viewModel.eliminarVivienda(confirmarEliminar!!); confirmarEliminar = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text(c.comEliminar, color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmarEliminar = null }) { Text(c.cancelar) }
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
    val c = LocalCadenas.current

    var nombre by remember { mutableStateOf(vivienda.nombre) }
    var superficie by remember { mutableStateOf(vivienda.superficie.toString()) }
    var anio by remember { mutableStateOf(vivienda.anioConstruccion.toString()) }
    var direccion by remember { mutableStateOf(vivienda.direccion) }
    var ventanas by remember { mutableStateOf(vivienda.tipoVentanas) }
    var aislamiento by remember { mutableStateOf(vivienda.aislamiento) }
    var calefaccion by remember { mutableStateOf(vivienda.calefaccion) }
    var acs by remember { mutableStateOf(vivienda.acs) }
    var orientacion by remember { mutableStateOf(vivienda.orientacion) }
    var provincia by remember { mutableStateOf(vivienda.provincia) }
    var iluminacion by remember { mutableStateOf(vivienda.iluminacion) }
    var tipoVivienda by remember { mutableStateOf(vivienda.tipoVivienda) }
    var refrigeracion by remember { mutableStateOf(vivienda.refrigeracion) }
    var fotovoltaica by remember { mutableStateOf(vivienda.fotovoltaica) }
    var electrodomesticos by remember { mutableStateOf(vivienda.electrodomesticos) }
    var ocupantes by remember { mutableStateOf(if (vivienda.ocupantes > 0) vivienda.ocupantes.toString() else "") }

    val optsVentanas = listOf("Vidrio simple", "Doble acristalamiento", "Triple")
    val optsAislamiento = listOf("Sin aislamiento", "Aislamiento parcial", "Aislamiento completo")
    val optsCalefaccion = listOf("Caldera de gas", "Eléctrica", "Aerotermia", "Biomasa", "Sin calefacción")
    val optsAcs = listOf("Gas", "Eléctrico", "Solar térmica", "Aerotermia", "Sin ACS")
    val optsOrientacion = listOf("Norte", "Sur", "Este", "Oeste", "Noreste", "Noroeste", "Sureste", "Suroeste")
    val optsIluminacion = AlgoritmoEnergetico.opcionesIluminacion
    val optsTipoVivienda = AlgoritmoEnergetico.opcionesTipoVivienda
    val optsRefrigeracion = AlgoritmoEnergetico.opcionesRefrigeracion
    val optsFotovoltaica = AlgoritmoEnergetico.opcionesFotovoltaica
    val optsElectrodomesticos = AlgoritmoEnergetico.opcionesElectrodomesticos

    // Todos los campos son obligatorios. Validaciones numéricas:
    //  - superficie: entero > 0
    //  - año construcción: 1800 ≤ año ≤ año actual (no futuros, no antigüedades absurdas)
    val anioActual = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    val superficieOk = superficie.toIntOrNull()?.let { it > 0 } == true
    val anioOk = anio.toIntOrNull()?.let { it in 1800..anioActual } == true
    val ocupantesOk = ocupantes.toIntOrNull()?.let { it in 1..20 } == true
    val formularioCompleto = nombre.isNotBlank() && superficieOk && anioOk && ocupantesOk
        && direccion.isNotBlank() && ventanas.isNotBlank() && aislamiento.isNotBlank()
        && calefaccion.isNotBlank() && acs.isNotBlank() && orientacion.isNotBlank()
        && provincia in AlgoritmoEnergetico.provinciasOrdenadas
        && iluminacion in optsIluminacion
        && tipoVivienda in optsTipoVivienda
        && refrigeracion in optsRefrigeracion
        && fotovoltaica in optsFotovoltaica
        && electrodomesticos in optsElectrodomesticos

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
                    Text(c.vivEditarTitulo, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, c.cerrar)
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
                        label = { Text(c.vivNombre) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = superficie,
                            onValueChange = { if (it.all(Char::isDigit)) superficie = it },
                            label = { Text(c.preSuperficie) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = anio,
                            onValueChange = { if (it.all(Char::isDigit) && it.length <= 4) anio = it },
                            label = { Text(c.preAnio) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = ocupantes,
                        onValueChange = { if (it.all(Char::isDigit) && it.length <= 2) ocupantes = it },
                        label = { Text(c.preOcupantes) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = direccion,
                        onValueChange = { direccion = it },
                        label = { Text(c.preDireccion) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column {
                        Text(c.preProvincia, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        SelectorProvincia(
                            valor = provincia,
                            onValor = { provincia = it }
                        )
                    }
                    DropdownField(c.preTipoVivienda, tipoVivienda, optsTipoVivienda) { tipoVivienda = it }
                    DropdownField(c.preVentanas, ventanas, optsVentanas) { ventanas = it }
                    DropdownField(c.preAislamiento, aislamiento, optsAislamiento) { aislamiento = it }
                    DropdownField(c.preCalefaccion, calefaccion, optsCalefaccion) { calefaccion = it }
                    DropdownField(c.preRefrigeracion, refrigeracion, optsRefrigeracion) { refrigeracion = it }
                    DropdownField(c.preAcs, acs, optsAcs) { acs = it }
                    DropdownField(c.preIluminacion, iluminacion, optsIluminacion) { iluminacion = it }
                    DropdownField(c.preElectrodomesticos, electrodomesticos, optsElectrodomesticos) { electrodomesticos = it }
                    DropdownField(c.preFotovoltaica, fotovoltaica, optsFotovoltaica) { fotovoltaica = it }
                    DropdownField(c.preOrientacion, orientacion, optsOrientacion) { orientacion = it }
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
                    ) { Text(c.cancelar) }
                    Button(
                        onClick = {
                            onGuardar(
                                vivienda.copy(
                                    nombre = nombre.trim(),
                                    superficie = superficie.toIntOrNull() ?: vivienda.superficie,
                                    anioConstruccion = anio.toIntOrNull() ?: vivienda.anioConstruccion,
                                    direccion = direccion.trim(),
                                    provincia = provincia,
                                    tipoVentanas = ventanas,
                                    aislamiento = aislamiento,
                                    calefaccion = calefaccion,
                                    acs = acs,
                                    iluminacion = iluminacion,
                                    orientacion = orientacion,
                                    tipoVivienda = tipoVivienda,
                                    refrigeracion = refrigeracion,
                                    fotovoltaica = fotovoltaica,
                                    ocupantes = ocupantes.toIntOrNull() ?: vivienda.ocupantes,
                                    electrodomesticos = electrodomesticos
                                )
                            )
                        },
                        enabled = formularioCompleto,
                        colors = ButtonDefaults.buttonColors(containerColor = verde),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(c.guardar, color = Color.White) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(label: String, valor: String, opciones: List<String>, onSelect: (String) -> Unit) {
    val c = LocalCadenas.current
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = TextosEnergia.opcion(valor, c),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            opciones.forEach { op ->
                DropdownMenuItem(
                    text = { Text(TextosEnergia.opcion(op, c)) },
                    onClick = { onSelect(op); expanded = false }
                )
            }
        }
    }
}
