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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.zerohaus.Modelos.Proyecto
import com.example.zerohaus.Modelos.Tarea
import com.example.zerohaus.ViewModel.ProyectosViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisProyectosScreen(
    viewModel: ProyectosViewModel,
    onVolver: () -> Unit = {}
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val fondo = MaterialTheme.colorScheme.background

    var mostrarCrear by remember { mutableStateOf(false) }
    var proyectoDetalle by remember { mutableStateOf<Proyecto?>(null) }
    var proyectoAEliminar by remember { mutableStateOf<Proyecto?>(null) }

    LaunchedEffect(Unit) { viewModel.cargarProyectos() }

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mis proyectos", fontWeight = FontWeight.SemiBold)
                        Text("${viewModel.proyectos.size} proyectos", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { mostrarCrear = true },
                containerColor = verde,
                contentColor = Color.White
            ) { Icon(Icons.Default.Add, "Nuevo proyecto") }
        }
    ) { pv ->
        if (viewModel.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = verde)
            }
        } else if (viewModel.proyectos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pv), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Assignment, null, tint = gris, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No tienes proyectos aún", color = gris, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Pulsa + para crear un proyecto", color = gris, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(pv).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(viewModel.proyectos) { p ->
                    TarjetaProyecto(
                        proyecto = p,
                        verde = verde,
                        gris = gris,
                        onToggleTarea = { idx, checked -> viewModel.toggleTarea(p.id, idx, checked) },
                        onVerDetalle = { proyectoDetalle = p },
                        onEliminar = { proyectoAEliminar = p }
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (mostrarCrear) {
        NuevoProyectoDialog(
            guardando = viewModel.guardando,
            viviendas = viewModel.viviendas.map { it.nombre },
            tecnicos = viewModel.tecnicos.map { it.nombre },
            onDismiss = { mostrarCrear = false },
            onCrear = { titulo, desc, vivienda, tecnico, tareas, fechaFin ->
                viewModel.crearProyecto(titulo, desc, vivienda, tecnico, tareas, fechaFin) { exito ->
                    if (exito) mostrarCrear = false
                }
            }
        )
    }

    proyectoDetalle?.let { p ->
        DetalleProyectoDialog(
            proyecto = p,
            onDismiss = { proyectoDetalle = null },
            onToggleTarea = { idx, checked -> viewModel.toggleTarea(p.id, idx, checked) },
            onEliminar = { proyectoAEliminar = p; proyectoDetalle = null }
        )
    }

    proyectoAEliminar?.let { p ->
        AlertDialog(
            onDismissRequest = { proyectoAEliminar = null },
            icon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFDC2626)) },
            title = { Text("Eliminar proyecto") },
            text = { Text("¿Seguro que quieres eliminar \"${p.titulo}\"? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.eliminarProyecto(p.id); proyectoAEliminar = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Eliminar", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { proyectoAEliminar = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun TarjetaProyecto(
    proyecto: Proyecto,
    verde: Color,
    gris: Color,
    onToggleTarea: (Int, Boolean) -> Unit,
    onVerDetalle: () -> Unit,
    onEliminar: () -> Unit
) {
    val borde = MaterialTheme.colorScheme.outline
    val estadoColor = when (proyecto.estado) {
        "Finalizado" -> verde
        "En curso" -> Color(0xFF2563EB)
        else -> gris
    }
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borde),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    proyecto.titulo,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AssistChip(
                    onClick = {},
                    label = { Text(proyecto.estado, fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = estadoColor.copy(0.12f),
                        labelColor = estadoColor
                    )
                )
                IconButton(onClick = onEliminar, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar proyecto",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (proyecto.descripcion.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    proyecto.descripcion,
                    color = gris,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (proyecto.viviendaNombre.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Home, null, tint = gris, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(proyecto.viviendaNombre, color = gris, fontSize = 12.sp)
                    }
                }
                if (proyecto.tecnicoNombre.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = gris, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(proyecto.tecnicoNombre, color = gris, fontSize = 12.sp)
                    }
                }
            }

            if (proyecto.fechaFinEstimada > 0) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, tint = gris, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "Fin estimado: ${sdf.format(Date(proyecto.fechaFinEstimada))}",
                        color = gris,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Progreso", fontSize = 13.sp, color = gris)
                Text(
                    "${proyecto.progreso}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = estadoColor
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { proyecto.progreso / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = estadoColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            if (proyecto.tareas.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Tareas (${proyecto.tareas.count { it.completada }}/${proyecto.tareas.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    TextButton(
                        onClick = onVerDetalle,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("Ver todo", color = estadoColor, fontSize = 12.sp)
                    }
                }
                proyecto.tareas.take(3).forEachIndexed { index, t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = t.completada,
                            onCheckedChange = { checked -> onToggleTarea(index, checked) },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF16A34A))
                        )
                        Text(
                            t.nombre,
                            fontSize = 13.sp,
                            color = if (t.completada) gris else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (proyecto.tareas.size > 3) {
                    TextButton(
                        onClick = onVerDetalle,
                        contentPadding = PaddingValues(start = 12.dp, top = 0.dp, bottom = 0.dp)
                    ) {
                        Text(
                            "+${proyecto.tareas.size - 3} tareas más",
                            color = gris,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetalleProyectoDialog(
    proyecto: Proyecto,
    onDismiss: () -> Unit,
    onToggleTarea: (Int, Boolean) -> Unit,
    onEliminar: () -> Unit
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val estadoColor = when (proyecto.estado) {
        "Finalizado" -> verde
        "En curso" -> Color(0xFF2563EB)
        else -> gris
    }
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(proyecto.titulo, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                }
                HorizontalDivider()
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(proyecto.estado) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = estadoColor.copy(0.12f),
                            labelColor = estadoColor
                        )
                    )

                    if (proyecto.descripcion.isNotEmpty()) {
                        Column {
                            Text("Descripción", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = gris)
                            Spacer(Modifier.height(4.dp))
                            Text(proyecto.descripcion, fontSize = 14.sp)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (proyecto.viviendaNombre.isNotEmpty()) {
                            Column {
                                Text("Vivienda", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = gris)
                                Text(proyecto.viviendaNombre, fontSize = 13.sp)
                            }
                        }
                        if (proyecto.tecnicoNombre.isNotEmpty()) {
                            Column {
                                Text("Técnico", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = gris)
                                Text(proyecto.tecnicoNombre, fontSize = 13.sp)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column {
                            Text("Fecha creación", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = gris)
                            Text(sdf.format(Date(proyecto.fechaCreacion)), fontSize = 13.sp)
                        }
                        if (proyecto.fechaFinEstimada > 0) {
                            Column {
                                Text("Fin estimado", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = gris)
                                Text(sdf.format(Date(proyecto.fechaFinEstimada)), fontSize = 13.sp)
                            }
                        }
                    }

                    Column {
                        Text("Progreso general", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = gris)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            LinearProgressIndicator(
                                progress = { proyecto.progreso / 100f },
                                modifier = Modifier.weight(1f).height(10.dp),
                                color = estadoColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("${proyecto.progreso}%", color = estadoColor, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (proyecto.tareas.isNotEmpty()) {
                        Column {
                            Text(
                                "Tareas (${proyecto.tareas.count { it.completada }}/${proyecto.tareas.size} completadas)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = gris
                            )
                            Spacer(Modifier.height(4.dp))
                            proyecto.tareas.forEachIndexed { index, t ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = t.completada,
                                        onCheckedChange = { checked -> onToggleTarea(index, checked) },
                                        colors = CheckboxDefaults.colors(checkedColor = verde)
                                    )
                                    Text(
                                        t.nombre,
                                        fontSize = 14.sp,
                                        color = if (t.completada) gris else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onEliminar,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFDC2626))
                        )
                    ) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Eliminar", color = Color(0xFFDC2626))
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = verde)
                    ) { Text("Cerrar", color = Color.White) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NuevoProyectoDialog(
    guardando: Boolean,
    viviendas: List<String>,
    tecnicos: List<String>,
    onDismiss: () -> Unit,
    onCrear: (String, String, String, String, List<Tarea>, Long) -> Unit
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    var titulo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var viviendaNombre by remember { mutableStateOf("") }
    var tecnicoNombre by remember { mutableStateOf("") }
    var expandidoVivienda by remember { mutableStateOf(false) }
    var expandidoTecnico by remember { mutableStateOf(false) }
    var fechaFinTexto by remember { mutableStateOf("") }
    var tareas by remember { mutableStateOf(listOf<String>()) }
    var nuevaTarea by remember { mutableStateOf("") }
    var fechaError by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.88f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Nuevo proyecto", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                }
                HorizontalDivider()
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = titulo,
                        onValueChange = { titulo = it },
                        label = { Text("Título del proyecto *") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = descripcion,
                        onValueChange = { descripcion = it },
                        label = { Text("Descripción") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = expandidoVivienda,
                            onExpandedChange = { expandidoVivienda = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = viviendaNombre,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Vivienda") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandidoVivienda) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(expanded = expandidoVivienda, onDismissRequest = { expandidoVivienda = false }) {
                                if (viviendas.isEmpty()) {
                                    DropdownMenuItem(text = { Text("Sin viviendas", color = gris) }, onClick = { expandidoVivienda = false })
                                } else {
                                    viviendas.forEach { v ->
                                        DropdownMenuItem(text = { Text(v) }, onClick = { viviendaNombre = v; expandidoVivienda = false })
                                    }
                                }
                            }
                        }
                        ExposedDropdownMenuBox(
                            expanded = expandidoTecnico,
                            onExpandedChange = { expandidoTecnico = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = tecnicoNombre,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Técnico") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandidoTecnico) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(expanded = expandidoTecnico, onDismissRequest = { expandidoTecnico = false }) {
                                if (tecnicos.isEmpty()) {
                                    DropdownMenuItem(text = { Text("Sin técnicos", color = gris) }, onClick = { expandidoTecnico = false })
                                } else {
                                    tecnicos.forEach { t ->
                                        DropdownMenuItem(text = { Text(t) }, onClick = { tecnicoNombre = t; expandidoTecnico = false })
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = fechaFinTexto,
                        onValueChange = {
                            fechaFinTexto = it
                            fechaError = false
                        },
                        label = { Text("Fin estimado (dd/MM/yyyy)") },
                        singleLine = true,
                        isError = fechaError,
                        supportingText = if (fechaError) {{ Text("Formato incorrecto. Usa dd/MM/yyyy") }} else null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { Icon(Icons.Default.CalendarToday, null, tint = gris) }
                    )

                    Text("Tareas", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                    tareas.forEachIndexed { i, t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckBoxOutlineBlank, null, tint = gris, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(t, modifier = Modifier.weight(1f), fontSize = 14.sp)
                            IconButton(
                                onClick = { tareas = tareas.toMutableList().also { it.removeAt(i) } },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, "Eliminar", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = nuevaTarea,
                            onValueChange = { nuevaTarea = it },
                            label = { Text("Nueva tarea") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (nuevaTarea.isNotBlank()) {
                                    tareas = tareas + nuevaTarea.trim()
                                    nuevaTarea = ""
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.AddCircle, null, tint = verde, modifier = Modifier.size(32.dp))
                        }
                    }
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
                            var fechaFin = 0L
                            if (fechaFinTexto.isNotBlank()) {
                                try {
                                    sdf.isLenient = false
                                    fechaFin = sdf.parse(fechaFinTexto)?.time ?: 0L
                                } catch (e: Exception) {
                                    fechaError = true
                                    return@Button
                                }
                            }
                            onCrear(
                                titulo.trim(),
                                descripcion.trim(),
                                viviendaNombre.trim(),
                                tecnicoNombre.trim(),
                                tareas.map { Tarea(nombre = it) },
                                fechaFin
                            )
                        },
                        enabled = titulo.isNotBlank() && !guardando,
                        colors = ButtonDefaults.buttonColors(containerColor = verde),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (guardando) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Crear", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
