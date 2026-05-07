package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
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
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val fondo = MaterialTheme.colorScheme.background
    val borde = MaterialTheme.colorScheme.outline
    val amarillo = Color(0xFFFFC107)
    val estado = viewModel.estado
    val filtrados = viewModel.tecnicosFiltrados()

    var mostrarFiltros by remember { mutableStateOf(false) }
    var tecnicoParaPresupuesto by remember { mutableStateOf<com.example.zerohaus.Modelos.Tecnico?>(null) }
    var descripcionPresupuesto by remember { mutableStateOf("") }

    val especialidades = listOf(
        "Aislamiento", "Ventanas", "Calefacción", "Fotovoltaica", "Aerotermia",
        "Auditorías", "Rehabilitación", "Biomasa", "Certificación", "Consultoría"
    )

    LaunchedEffect(Unit) { viewModel.cargarTecnicos(forzar = true) }

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
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
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
                modifier = Modifier
                    .padding(pv)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Barra de búsqueda + botón filtros
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = estado.busqueda,
                            onValueChange = { viewModel.cambiarBusqueda(it) },
                            placeholder = { Text("Buscar por nombre o especialidad…") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = borde,
                                focusedBorderColor = verde,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        Box {
                            OutlinedButton(
                                onClick = { mostrarFiltros = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (estado.filtro != null) verde else borde),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (estado.filtro != null) verde else gris
                                )
                            ) {
                                Icon(Icons.Default.Tune, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Filtros")
                            }
                            DropdownMenu(
                                expanded = mostrarFiltros,
                                onDismissRequest = { mostrarFiltros = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sin filtro") },
                                    onClick = { viewModel.cambiarFiltro(null); mostrarFiltros = false }
                                )
                                especialidades.forEach { esp ->
                                    DropdownMenuItem(
                                        text = { Text(esp) },
                                        onClick = { viewModel.cambiarFiltro(esp); mostrarFiltros = false }
                                    )
                                }
                            }
                        }
                    }

                    estado.filtro?.let {
                        Spacer(Modifier.height(10.dp))
                        AssistChip(
                            onClick = { viewModel.cambiarFiltro(null) },
                            label = { Text("Filtro: $it  ✕") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = verde.copy(0.12f),
                                labelColor = verde
                            )
                        )
                    }
                }

                // Estado vacío
                if (filtrados.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.SearchOff,
                                    null,
                                    tint = gris.copy(0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("No se encontraron técnicos", color = gris, fontWeight = FontWeight.Medium)
                                Text("Prueba con otro término o filtro", color = gris.copy(0.7f), fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Tarjetas de técnicos
                items(filtrados) { t ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, borde),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVerPerfil(t.id) }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            // Fila superior: avatar + info + badge rating
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Avatar circular con inicial
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = CircleShape,
                                    color = verde.copy(alpha = 0.1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            t.nombre.take(1).uppercase(),
                                            color = verde,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        )
                                    }
                                }

                                // Nombre, ciudad, especialidades
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        t.nombre,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    if (t.ciudad.isNotEmpty()) {
                                        Spacer(Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.LocationOn,
                                                null,
                                                tint = gris,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(2.dp))
                                            Text(t.ciudad, color = gris, fontSize = 13.sp)
                                        }
                                    } else if (t.distanciaKm > 0.0) {
                                        Spacer(Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.LocationOn,
                                                null,
                                                tint = gris,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(2.dp))
                                            Text("${t.distanciaKm} km", color = gris, fontSize = 13.sp)
                                        }
                                    }
                                }

                                // Badge de rating (arriba a la derecha)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = amarillo.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            null,
                                            tint = amarillo,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            "%.1f".format(t.rating),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF92400E)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Chips de especialidades con scroll horizontal (max 4)
                            if (t.especialidades.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    t.especialidades.take(4).forEach { esp ->
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = verde.copy(alpha = 0.08f)
                                        ) {
                                            Text(
                                                esp,
                                                color = verde,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }

                            // Fila de estadísticas
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.StarBorder,
                                        null,
                                        tint = gris,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text("${t.opiniones} opiniones", color = gris, fontSize = 12.sp)
                                }
                                Text("·", color = gris, fontSize = 12.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        tint = gris,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text("${t.proyectosCompletados} proyectos", color = gris, fontSize = 12.sp)
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = borde, thickness = 1.dp)
                            Spacer(Modifier.height(12.dp))

                            // Botones
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { onVerPerfil(t.id) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, verde),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = verde)
                                ) {
                                    Text("Ver perfil", fontWeight = FontWeight.SemiBold)
                                }
                                Button(
                                    onClick = { tecnicoParaPresupuesto = t; descripcionPresupuesto = "" },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = verde)
                                ) {
                                    Text("Presupuesto", color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo solicitar presupuesto
    if (tecnicoParaPresupuesto != null) {
        val t = tecnicoParaPresupuesto!!
        AlertDialog(
            onDismissRequest = { tecnicoParaPresupuesto = null },
            title = { Text("Solicitar presupuesto", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Técnico: ${t.nombre}", fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = descripcionPresupuesto,
                        onValueChange = { descripcionPresupuesto = it },
                        label = { Text("Describe lo que necesitas") },
                        placeholder = { Text("Ej: Quiero instalar paneles solares en mi vivienda de 120m²") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.solicitarPresupuestoConDescripcion(
                            t,
                            descripcionPresupuesto.ifBlank { "Solicitud de presupuesto" }
                        )
                        tecnicoParaPresupuesto = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) { Text("Enviar solicitud", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { tecnicoParaPresupuesto = null }) { Text("Cancelar") }
            }
        )
    }
}
