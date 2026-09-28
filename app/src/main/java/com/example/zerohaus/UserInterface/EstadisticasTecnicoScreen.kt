package com.example.zerohaus.UserInterface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Modelos.esDestacado
import com.example.zerohaus.Modelos.esVerificado
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.ViewModel.EstadisticasTecnicoViewModel
import com.example.zerohaus.ViewModel.MejoraPerfil
import androidx.compose.material.icons.automirrored.filled.Chat

/**
 * Estadísticas del profesional: plan, reputación, actividad de los últimos
 * 30 días (visitas, chats, llamadas y conversión), posición en el directorio
 * de su ciudad y checklist para completar el perfil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstadisticasTecnicoScreen(
    viewModel: EstadisticasTecnicoViewModel,
    onVolver: () -> Unit = {},
    onSuscripcion: () -> Unit = {},
    onEditarPerfil: () -> Unit = {}
) {
    val c = LocalCadenas.current
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val estado = viewModel.estado
    val dorado = Color(0xFFF59E0B)

    LaunchedEffect(Unit) { viewModel.cargar() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(c.estTitulo, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, c.volver) }
                }
            )
        }
    ) { pv ->
        if (estado.cargando) {
            Box(Modifier.padding(pv).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .padding(pv)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Plan actual
            Card(
                colors = CardDefaults.cardColors(containerColor = verde),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(c.estTuPlan, color = Color.White.copy(0.85f), fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    val tec = estado.tecnico
                    Text(
                        when {
                            tec?.esDestacado == true -> c.estDestacado
                            tec?.esVerificado == true -> c.estVerificado
                            else -> c.estGratuito
                        },
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when {
                            tec?.esDestacado == true -> c.estDestacadoDesc
                            tec?.esVerificado == true -> c.estVerificadoDesc
                            else -> c.estGratuitoDesc
                        },
                        color = Color.White.copy(0.75f), fontSize = 12.sp
                    )
                }
            }

            if (estado.tecnico?.esVerificado != true) {
                Card(
                    onClick = onSuscripcion,
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFF92400E), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            c.estSuscribeteCta,
                            fontSize = 13.sp, color = Color(0xFF92400E), modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF92400E))
                    }
                }
            }

            // Reputación
            estado.tecnico?.let { tec ->
                Text(c.estReputacion, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricaMini("%.1f".format(tec.rating), c.estValoracionMedia, Icons.Default.Star, Color(0xFFEAB308), Modifier.weight(1f))
                    MetricaMini("${tec.opiniones}", c.estOpinionesCap, Icons.Default.Reviews, verde, Modifier.weight(1f))
                }

            }

            // Actividad de los últimos 30 días
            val mes = estado.estadisticas.ultimosDias(30)
            Text(c.estActividad, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricaMini("${mes.visitas}", c.estVisitas, Icons.Default.Visibility, verde, Modifier.weight(1f))
                MetricaMini("${mes.chats}", c.estChats, Icons.AutoMirrored.Filled.Chat, Color(0xFF2563EB), Modifier.weight(1f))
                MetricaMini("${mes.llamadas}", c.estLlamadas, Icons.Default.Call, Color(0xFF7C3AED), Modifier.weight(1f))
            }
            if (mes.visitas == 0 && mes.contactos == 0) {
                Text(c.estSinActividad, color = gris, fontSize = 13.sp)
            } else {
                if (mes.visitas > 0) {
                    val conversion = (mes.contactos * 100 / mes.visitas).coerceAtMost(100)
                    Text(
                        "${c.estConversion}: $conversion % ${c.estConversionDesc}",
                        color = gris, fontSize = 13.sp
                    )
                }
                GraficaVisitas(estado.estadisticas.serieDiaria(14).map { it.second.visitas }, c.estUltimos14, verde, gris)
            }

            // Posición en el directorio de su ciudad
            estado.posicion?.let { p ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(c.estPosicion, color = gris, fontSize = 13.sp)
                        Text(
                            "#${p.puesto} ${c.estDe} ${p.total} · ${p.ciudad}",
                            fontWeight = FontWeight.Bold, fontSize = 22.sp
                        )
                        p.puestoConDestacado?.let { mejor ->
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = dorado, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("${c.estConDestacado} #$mejor", fontSize = 13.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = onSuscripcion) { Text(c.estVerPlanes, color = verde) }
                            }
                        }
                    }
                }
            }

            // Checklist del perfil
            if (estado.tecnico != null) {
                val pendientes = estado.pendientesPerfil
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (pendientes.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = verde, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(c.estPerfilOk, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Text("${c.estPerfilCompleto} ${estado.completitudPerfil} %", fontWeight = FontWeight.SemiBold)
                            LinearProgressIndicator(
                                progress = { estado.completitudPerfil / 100f },
                                color = verde,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                            )
                            pendientes.forEach { p ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.RadioButtonUnchecked, null, tint = gris, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        when (p) {
                                            MejoraPerfil.DESCRIPCION -> c.estFaltaDescripcion
                                            MejoraPerfil.ESPECIALIDADES -> c.estFaltaEspecialidades
                                            MejoraPerfil.TELEFONO -> c.estFaltaTelefono
                                            MejoraPerfil.CIUDAD -> c.estFaltaCiudad
                                            MejoraPerfil.UBICACION -> c.estFaltaUbicacion
                                        },
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            OutlinedButton(onClick = onEditarPerfil, modifier = Modifier.fillMaxWidth()) {
                                Text(c.estEditarPerfil, color = verde)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Barras simples de visitas por día (la última barra es hoy). */
@Composable
private fun GraficaVisitas(valores: List<Int>, titulo: String, verde: Color, gris: Color) {
    val maximo = (valores.maxOrNull() ?: 0).coerceAtLeast(1)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(titulo, color = gris, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().height(90.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                valores.forEachIndexed { i, v ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(fraction = (v.toFloat() / maximo).coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (i == valores.lastIndex) verde else verde.copy(alpha = 0.45f))
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricaMini(valor: String, etiqueta: String, icono: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icono, null, tint = color, modifier = Modifier.size(20.dp))
            Text(valor, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(etiqueta, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}
