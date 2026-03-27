
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapaTecnicosScreen(
    viewModel: TecnicosViewModel,
    onVolver: () -> Unit = {},
    onVerPerfil: (String) -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val estado = viewModel.estado

    // Madrid como centro por defecto
    val madrid = LatLng(40.4168, -3.7038)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(madrid, 12f)
    }

    var tecnicoSeleccionado by remember { mutableStateOf<Tecnico?>(null) }

    LaunchedEffect(Unit) { viewModel.cargarTecnicos() }

    // Generar posiciones simuladas alrededor de Madrid para cada técnico
    val tecnicosConPos = remember(estado.tecnicos) {
        estado.tecnicos.mapIndexed { i, t ->
            val lat = 40.4168 + (i * 0.012) - 0.02 + (t.nombre.hashCode() % 100) * 0.0003
            val lng = -3.7038 + (i * 0.015) - 0.03 + (t.nombre.hashCode() % 50) * 0.0004
            t to LatLng(lat, lng)
        }
    }

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mapa de técnicos", fontWeight = FontWeight.SemiBold)
                        Text("${estado.tecnicos.size} técnicos cerca", color = gris, fontSize = 12.sp)
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

                // Tarjeta del técnico seleccionado
                if (tecnicoSeleccionado != null) {
                    val t = tecnicoSeleccionado!!
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                            Text("${t.distanciaKm} km · ${t.proyectosCompletados} proyectos", color = gris, fontSize = 12.sp)
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
