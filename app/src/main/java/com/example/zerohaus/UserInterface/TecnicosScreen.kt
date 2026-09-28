package com.example.zerohaus.UserInterface

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.esDestacado
import com.example.zerohaus.Modelos.esEmpresa
import com.example.zerohaus.Modelos.esVerificado
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.ViewModel.OrdenTecnicos
import com.example.zerohaus.ViewModel.TecnicosViewModel

/**
 * Directorio de profesionales: búsqueda, filtros por especialidad y orden por
 * valoración o cercanía (solo con la ubicación real del usuario).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TecnicosScreen(
    viewModel: TecnicosViewModel,
    onVolver: () -> Unit = {},
    onVerPerfil: (String) -> Unit = {}
) {
    val c = LocalCadenas.current
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val fondo = MaterialTheme.colorScheme.background
    val borde = MaterialTheme.colorScheme.outline
    val amarillo = Color(0xFFFFC107)
    val estado = viewModel.estado
    val filtrados = viewModel.tecnicosFiltrados()
    val ctx = LocalContext.current

    var mostrarFiltros by remember { mutableStateOf(false) }
    val dorado = Color(0xFFF59E0B)

    val especialidades = listOf(
        "Aislamiento", "Ventanas", "Calefacción", "Fotovoltaica", "Aerotermia",
        "Auditorías", "Rehabilitación", "Biomasa", "Certificación", "Consultoría"
    )

    // Pedir ubicación y actualizar distancias
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) obtenerUbicacion(ctx) { lat, lng -> viewModel.actualizarUbicacion(lat, lng) }
    }
    LaunchedEffect(Unit) {
        viewModel.cargarTecnicos()
        val tienePermiso = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (tienePermiso) {
            obtenerUbicacion(ctx) { lat, lng -> viewModel.actualizarUbicacion(lat, lng) }
        } else {
            locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        // Sin ubicación real no se inventa una: la lista se ordena por
        // valoración y no se muestran distancias.
    }

    Scaffold(
        containerColor = fondo,
        snackbarHost = {
            ZeroToast(
                mensaje   = estado.mensajeExito ?: estado.error,
                tipo      = if (estado.error != null) ToastTipo.ERROR else ToastTipo.EXITO,
                alOcultar = { viewModel.limpiarMensaje() }
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(c.tecDirTitulo, fontWeight = FontWeight.SemiBold)
                        Text("${filtrados.size} ${c.tecDirSubtitulo}", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, c.volver) }
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
                            placeholder = { Text(c.tecBuscarPlaceholder) },
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
                                Text(c.comFiltros)
                            }
                            DropdownMenu(
                                expanded = mostrarFiltros,
                                onDismissRequest = { mostrarFiltros = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(c.tecSinFiltro) },
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

                    // Chips de tipo de profesional
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf(
                            null to c.tecTodos,
                            Tecnico.TIPO_TECNICO to c.tecChipTecnicos,
                            Tecnico.TIPO_EMPRESA to c.tecChipEmpresas
                        ).forEach { (tipo, label) ->
                            FilterChip(
                                selected = estado.filtroTipo == tipo,
                                onClick = { viewModel.cambiarFiltroTipo(tipo) },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = verde.copy(0.15f),
                                    selectedLabelColor = verde
                                )
                            )
                        }
                    }

                    // Chips de ordenamiento
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf(
                            OrdenTecnicos.VALORACION to c.tecOrdenValoracion,
                            OrdenTecnicos.PROXIMIDAD to c.tecOrdenProximidad
                        ).forEach { (orden, label) ->
                            FilterChip(
                                selected = estado.orden == orden,
                                onClick = { viewModel.cambiarOrden(orden) },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = verde.copy(0.15f),
                                    selectedLabelColor = verde
                                )
                            )
                        }
                    }

                    estado.filtro?.let {
                        Spacer(Modifier.height(6.dp))
                        AssistChip(
                            onClick = { viewModel.cambiarFiltro(null) },
                            label = { Text("${c.tecFiltroPrefijo} $it  ✕") },
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
                                if (estado.busqueda.isBlank() && estado.filtro == null) {
                                    Text(c.tecNoCargar, color = gris, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.cargarTecnicos(forzar = true) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(c.comReintentar)
                                    }
                                } else {
                                    Text(c.tecNoEncontrados, color = gris, fontWeight = FontWeight.Medium)
                                    Text(c.tecPruebaOtro, color = gris.copy(0.7f), fontSize = 13.sp)
                                }
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
                                        if (t.nombre.isNotBlank()) {
                                            Text(
                                                t.nombre.take(1).uppercase(),
                                                color = verde,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = verde,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                }

                                // Nombre, tipo, ciudad, especialidades
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        t.nombre.ifBlank { c.tecProfesionalFallback },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (t.esEmpresa) Color(0xFF7C3AED).copy(0.12f) else verde.copy(0.1f)
                                        ) {
                                            Text(
                                                if (t.esEmpresa) c.tecEmpresaReformas else c.tecTecnicoCertificador,
                                                color = if (t.esEmpresa) Color(0xFF7C3AED) else verde,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (t.esDestacado) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = dorado.copy(0.13f)
                                            ) {
                                                Row(
                                                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(Icons.Default.Star, null, tint = dorado, modifier = Modifier.size(11.dp))
                                                    Text(c.estDestacado, color = Color(0xFF92400E), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        } else if (t.esVerificado) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF065F46).copy(0.13f)
                                            ) {
                                                Row(
                                                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF065F46), modifier = Modifier.size(11.dp))
                                                    Text(c.estVerificado, color = Color(0xFF065F46), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }
                                    // Distancia solo si conocemos ambas posiciones reales
                                    val ubicConocida = estado.latUsuario != 0.0 && viewModel.tieneUbicacionConocida(t)
                                    if (t.ciudad.isNotEmpty() || ubicConocida) {
                                        Spacer(Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.LocationOn, null, tint = gris, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(2.dp))
                                            val textoUbic = buildString {
                                                if (t.ciudad.isNotEmpty()) append(t.ciudad)
                                                if (ubicConocida) {
                                                    if (t.ciudad.isNotEmpty()) append(" · ")
                                                    // distanciaKm == 0 con coords conocidas = mismo punto que el
                                                    // usuario; mostramos "<1 km" en vez de "0.0 km" o esconderla.
                                                    append(
                                                        if (t.distanciaKm < 1.0) "<1 km"
                                                        else "${t.distanciaKm} km"
                                                    )
                                                }
                                            }
                                            Text(textoUbic, color = gris, fontSize = 13.sp)
                                        }
                                    }
                                }

                                // Badge de rating (arriba a la derecha)
                                if (t.opiniones > 0) {
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
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = gris.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            c.tecNuevo,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp,
                                            color = gris
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
                                    Text("${t.opiniones} ${c.comOpiniones}", color = gris, fontSize = 12.sp)
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = borde, thickness = 1.dp)
                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = { onVerPerfil(t.id) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = verde)
                            ) {
                                Text(c.comVerPerfilCompleto, color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.RequiresPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
private fun obtenerUbicacion(context: android.content.Context, onResult: (Double, Double) -> Unit) {
    try {
        val client = LocationServices.getFusedLocationProviderClient(context)
        // Intentar primero con lastLocation (rápido)
        client.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                onResult(loc.latitude, loc.longitude)
            } else {
                // En emulador lastLocation suele ser null — pedir ubicación fresca
                val request = com.google.android.gms.location.CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setDurationMillis(5000)
                    .build()
                client.getCurrentLocation(request, null)
                    .addOnSuccessListener { freshLoc ->
                        if (freshLoc != null) onResult(freshLoc.latitude, freshLoc.longitude)
                    }
            }
        }.addOnFailureListener {
            // Fallback directo a getCurrentLocation
            val request = com.google.android.gms.location.CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setDurationMillis(5000)
                .build()
            client.getCurrentLocation(request, null)
                .addOnSuccessListener { freshLoc ->
                    if (freshLoc != null) onResult(freshLoc.latitude, freshLoc.longitude)
                }
        }
    } catch (_: Exception) {}
}
