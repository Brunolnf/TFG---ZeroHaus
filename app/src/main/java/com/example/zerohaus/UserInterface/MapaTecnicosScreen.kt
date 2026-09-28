
package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Util.Formato
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.ViewModel.TecnicosViewModel
import com.example.zerohaus.Util.TextosEnergia
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

/**
 * Mapa de profesionales (Google Maps). Solo se pinta una ubicación real: la
 * del profesional o la de su ciudad; nunca se inventa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapaTecnicosScreen(
    viewModel: TecnicosViewModel,
    onVolver: () -> Unit = {},
    onVerPerfil: (String) -> Unit = {}
) {
    val c = LocalCadenas.current
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val fondo = MaterialTheme.colorScheme.background
    val estado = viewModel.estado

    // Centro de España
    val españa = LatLng(40.0, -3.5)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(españa, 5.5f)
    }

    var tecnicoSeleccionado by remember { mutableStateOf<Tecnico?>(null) }

    LaunchedEffect(Unit) { viewModel.cargarTecnicos(forzar = true) }

    // Posición: la del profesional o, si no la tiene, el centro de su ciudad.
    // Sin ninguna de las dos NO se pinta (nunca se inventa una ubicación).
    // Si varios comparten la misma posición exacta, se separan ~400 m en
    // círculo para que no se solapen.
    val tecnicosConPos = remember(estado.tecnicos) {
        val posiciones = mutableMapOf<String, Int>() // clave → contador de repeticiones
        estado.tecnicos.mapNotNull { t ->
            val base = when {
                t.latitud != 0.0 || t.longitud != 0.0 -> LatLng(t.latitud, t.longitud)
                else -> com.example.zerohaus.Repositorios.RepositorioTecnicos.coordenadasDeCiudad(t.ciudad)
                    ?.let { LatLng(it.first, it.second) }
            } ?: return@mapNotNull null
            val clave = String.format(java.util.Locale.US, "%.4f,%.4f", base.latitude, base.longitude)
            val n = posiciones.getOrDefault(clave, 0)
            posiciones[clave] = n + 1
            val pos = if (n > 0) {
                val angulo = Math.toRadians(n * 72.0)
                LatLng(base.latitude + 0.004 * Math.cos(angulo), base.longitude + 0.004 * Math.sin(angulo))
            } else base
            t to pos
        }
    }
    // Profesionales que no se pueden situar (sin ciudad reconocida)
    val hayUbicacionesAproximadas = tecnicosConPos.size < estado.tecnicos.size

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(c.mapaTitulo, fontWeight = FontWeight.SemiBold)
                        Text("${estado.tecnicos.size} ${c.mapaSubtitulo}", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, c.volver)
                    }
                }
            )
        }
    ) { pv ->
        if (estado.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = verde)
            }
        } else {
            Box(Modifier.padding(pv).fillMaxSize()) {
                // Mapa de Google
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = false),
                    uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false)
                ) {
                    tecnicosConPos.forEach { (tecnico, posicion) ->
                        val esSeleccionado = tecnicoSeleccionado?.id == tecnico.id
                        MarkerComposable(
                            keys = arrayOf<Any>(tecnico.id, esSeleccionado),
                            state = MarkerState(position = posicion),
                            title = tecnico.nombre,
                            snippet = "${tecnico.rating} ★ · ${tecnico.especialidades.firstOrNull()?.let { TextosEnergia.especialidad(it, c) } ?: ""}",
                            onClick = {
                                tecnicoSeleccionado = if (esSeleccionado) null else tecnico
                                true // consume el click para no mostrar InfoWindow nativo
                            }
                        ) {
                            val burbuja  = if (esSeleccionado) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.surface
                            val txtColor = if (esSeleccionado) Color.White
                                           else MaterialTheme.colorScheme.onSurface
                            val pinColor = if (esSeleccionado) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                // Pill con icono, nombre y rating
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = burbuja,
                                    shadowElevation = if (esSeleccionado) 8.dp else 4.dp,
                                    border = if (!esSeleccionado)
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    else null
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Build, null,
                                            modifier = Modifier.size(11.dp),
                                            tint = if (esSeleccionado) Color.White
                                                   else MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            tecnico.nombre.split(" ").take(2).joinToString(" "),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = txtColor,
                                            maxLines = 1
                                        )
                                        if (tecnico.rating > 0f) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (esSeleccionado) Color.White.copy(alpha = 0.18f)
                                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            ) {
                                                Row(
                                                    Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Star, null,
                                                        modifier = Modifier.size(9.dp),
                                                        tint = if (esSeleccionado) Color(0xFFFFD700)
                                                               else Color(0xFFF59E0B)
                                                    )
                                                    Text(
                                                        Formato.numero(tecnico.rating),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (esSeleccionado) Color.White
                                                                else MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                // Triángulo puntero dibujado con Canvas
                                Canvas(Modifier.size(width = 14.dp, height = 7.dp)) {
                                    drawPath(
                                        path = Path().apply {
                                            moveTo(0f, 0f)
                                            lineTo(size.width, 0f)
                                            lineTo(size.width / 2f, size.height)
                                            close()
                                        },
                                        color = burbuja
                                    )
                                }
                                // Punto de anclaje
                                Surface(
                                    shape = CircleShape,
                                    color = pinColor,
                                    modifier = Modifier.size(7.dp)
                                ) {}
                            }
                        }
                    }
                }

                // Banner de ubicaciones aproximadas
                if (hayUbicacionesAproximadas) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = gris, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(c.mapaAproximadas, fontSize = 12.sp, color = gris)
                        }
                    }
                }

                // Tarjeta del técnico seleccionado
                if (tecnicoSeleccionado != null) {
                    val t = tecnicoSeleccionado!!
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(t.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { tecnicoSeleccionado = null }) {
                                    Icon(Icons.Default.Close, c.cerrar, tint = gris)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilaEstrellas(t.rating, tamano = 16.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("${Formato.numero(t.rating)} (${t.opiniones})", color = gris, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(t.especialidades.joinToString(" · ") { TextosEnergia.especialidad(it, c) }, color = gris, fontSize = 13.sp)
                            if (t.ciudad.isNotEmpty()) {
                                Text(t.ciudad, color = gris, fontSize = 12.sp)
                            }
                            Text("${t.opiniones} ${c.comValoraciones}", color = gris, fontSize = 12.sp)
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { onVerPerfil(t.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = verde),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
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
