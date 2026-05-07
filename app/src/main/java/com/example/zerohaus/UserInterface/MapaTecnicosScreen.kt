
package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.ViewModel.TecnicosViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

// 14 ubicaciones residenciales reales por toda España (interior de manzana, no carreteras ni aeropuertos)
private val UBICACIONES_RESIDENCIALES_FALLBACK = listOf(
    LatLng(40.4291, -3.6182),  // Madrid - San Blas
    LatLng(41.3912,  2.1118),  // Barcelona - Les Corts
    LatLng(37.3624, -5.9847),  // Sevilla - Heliópolis
    LatLng(39.4901, -0.3894),  // Valencia - Marxalenes
    LatLng(43.2641, -2.9374),  // Bilbao - Irala
    LatLng(41.6488, -0.8908),  // Zaragoza - Centro
    LatLng(36.7195, -4.4128),  // Málaga - La Malagueta
    LatLng(37.1773, -3.6035),  // Granada - Camino de Ronda
    LatLng(37.9849, -1.1314),  // Murcia - Centro
    LatLng(38.3658, -0.4863),  // Alicante - Garbinet
    LatLng(43.3582, -8.4133),  // A Coruña - Os Mallos
    LatLng(42.2218, -8.7162),  // Vigo - O Calvario
    LatLng(42.8071, -1.6514),  // Pamplona - Iturrama
    LatLng(39.5705,  2.6398)   // Palma de Mallorca - Son Espanyolet
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapaTecnicosScreen(
    viewModel: TecnicosViewModel,
    onVolver: () -> Unit = {},
    onVerPerfil: (String) -> Unit = {}
) {
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

    LaunchedEffect(Unit) { viewModel.cargarTecnicos() }

    // Coordenadas reales si existen; si no, posición de fallback determinista por ID del técnico
    // (misma posición siempre para el mismo técnico, independientemente del orden de lista)
    val tecnicosConPos = remember(estado.tecnicos) {
        estado.tecnicos.map { t ->
            val pos = if (t.latitud != 0.0 || t.longitud != 0.0) {
                LatLng(t.latitud, t.longitud)
            } else {
                val idx = Math.abs(t.id.hashCode()) % UBICACIONES_RESIDENCIALES_FALLBACK.size
                UBICACIONES_RESIDENCIALES_FALLBACK[idx]
            }
            t to pos
        }
    }
    val hayUbicacionesAproximadas = estado.tecnicos.any { it.latitud == 0.0 && it.longitud == 0.0 }

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mapa de técnicos", fontWeight = FontWeight.SemiBold)
                        Text("${estado.tecnicos.size} técnicos disponibles", color = gris, fontSize = 12.sp)
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
                        Marker(
                            state = MarkerState(position = posicion),
                            title = tecnico.nombre,
                            snippet = "${tecnico.rating} ★ · ${tecnico.especialidades.joinToString(", ")}",
                            onClick = {
                                tecnicoSeleccionado = tecnico
                                false
                            }
                        )
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
                            Text("Algunas ubicaciones son aproximadas", fontSize = 12.sp, color = gris)
                        }
                    }
                }

                // Tarjeta del técnico seleccionado
                if (tecnicoSeleccionado != null) {
                    val t = tecnicoSeleccionado!!
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
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
                                    Icon(Icons.Default.Close, "Cerrar", tint = gris)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(5) { i ->
                                    Icon(
                                        Icons.Default.Star, null,
                                        tint = if (t.rating >= (i + 1) - 0.01) Color(0xFFFFC107) else Color(0xFFBDBDBD),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("${t.rating} (${t.opiniones})", color = gris, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(t.especialidades.joinToString(" · "), color = gris, fontSize = 13.sp)
                            if (t.ciudad.isNotEmpty()) {
                                Text(t.ciudad, color = gris, fontSize = 12.sp)
                            }
                            Text("${t.proyectosCompletados} proyectos completados", color = gris, fontSize = 12.sp)
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { onVerPerfil(t.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = verde),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Ver perfil completo", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
