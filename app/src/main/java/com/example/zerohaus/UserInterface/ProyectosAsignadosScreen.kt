package com.example.zerohaus.UserInterface

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Modelos.Proyecto
import com.example.zerohaus.ViewModel.ProyectosAsignadosViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProyectosAsignadosScreen(
    viewModel: ProyectosAsignadosViewModel,
    onVolver: () -> Unit = {}
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val estado = viewModel.estado
    var detalleProyecto by remember { mutableStateOf<Proyecto?>(null) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.cargar() }
    LaunchedEffect(estado.mensaje) {
        estado.mensaje?.let { snackbar.showSnackbar(it); viewModel.limpiarMensaje() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mis proyectos", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${estado.proyectos.size} proyectos asignados",
                            color = gris, fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
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
                estado.proyectos.isEmpty() -> {
                    Column(
                        Modifier.fillMaxSize().padding(40.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Construction, null, tint = gris, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Aún no tienes proyectos asignados", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Cuando un cliente acepte tu presupuesto y cree un proyecto contigo, aparecerá aquí.",
                            color = gris, fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { viewModel.cargar() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reintentar")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(estado.proyectos) { p ->
                            ProyectoCard(p) { detalleProyecto = p }
                        }
                    }
                }
            }
        }
    }

    detalleProyecto?.let { p ->
        DialogoDetalleProyecto(
            proyecto = p,
            onCerrar = { detalleProyecto = null },
            onToggleTarea = { idx, completada ->
                viewModel.toggleTarea(p.id, idx, completada)
                detalleProyecto = null
            },
            onMarcarTerminado = {
                if (p.solicitudId.isNotBlank()) {
                    viewModel.marcarTrabajoTerminado(p.solicitudId)
                    detalleProyecto = null
                }
            }
        )
    }
}

@Composable
private fun ProyectoCard(p: Proyecto, onClick: () -> Unit) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val (colorEstado, fondoEstado) = when (p.estado) {
        "Finalizado" -> verde to verde.copy(0.12f)
        "En curso" -> Color(0xFF2563EB) to Color(0xFFDBEAFE)
        else -> Color(0xFFD97706) to Color(0xFFFEF3C7)
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.titulo, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(fondoEstado)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(p.estado, color = colorEstado, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (p.viviendaNombre.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Home, null, tint = gris, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(p.viviendaNombre, color = gris, fontSize = 13.sp)
                }
            }
            Text(p.descripcion, color = gris, fontSize = 13.sp, maxLines = 2)
            Spacer(Modifier.height(2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Progreso", fontSize = 12.sp, color = gris)
                    Text("${p.progreso}%", fontSize = 12.sp, color = verde, fontWeight = FontWeight.SemiBold)
                }
                LinearProgressIndicator(
                    progress = { p.progreso / 100f },
                    color = verde,
                    trackColor = verde.copy(0.15f),
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, null, tint = gris, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("Inicio: ${sdf.format(Date(p.fechaCreacion))}", color = gris, fontSize = 11.sp)
                if (p.fechaFinEstimada > 0) {
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.Flag, null, tint = gris, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Fin estimado: ${sdf.format(Date(p.fechaFinEstimada))}", color = gris, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun DialogoDetalleProyecto(
    proyecto: Proyecto,
    onCerrar: () -> Unit,
    onToggleTarea: (Int, Boolean) -> Unit,
    onMarcarTerminado: () -> Unit
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val puedeTerminar = proyecto.estado == "En curso" && proyecto.solicitudId.isNotBlank()

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(proyecto.titulo, fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(proyecto.descripcion, color = gris, fontSize = 13.sp)
                if (proyecto.precio > 0) {
                    Text("Importe acordado: ${"%.2f".format(proyecto.precio)} €",
                        fontSize = 13.sp, color = verde, fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider()
                Text("Tareas (${proyecto.tareas.count { it.completada }}/${proyecto.tareas.size})",
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                proyecto.tareas.forEachIndexed { idx, tarea ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = tarea.completada,
                            onCheckedChange = { nuevoEstado -> onToggleTarea(idx, nuevoEstado) },
                            colors = CheckboxDefaults.colors(checkedColor = verde)
                        )
                        Text(
                            tarea.nombre,
                            fontSize = 14.sp,
                            color = if (tarea.completada) gris else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (tarea.completada) TextDecoration.LineThrough else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (puedeTerminar) {
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = onMarcarTerminado,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Marcar trabajo terminado", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onCerrar, colors = ButtonDefaults.buttonColors(containerColor = verde)) {
                Text("Cerrar", color = Color.White)
            }
        }
    )
}
