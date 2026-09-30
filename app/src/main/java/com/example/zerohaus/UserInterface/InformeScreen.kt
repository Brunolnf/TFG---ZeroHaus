
package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Modelos.Especialidades
import com.example.zerohaus.Repositorios.ErrorIA
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.Formato
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.Util.TextosEnergia
import com.example.zerohaus.ViewModel.InformeViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Informe energético: etiqueta, indicadores, recomendaciones con su ahorro,
 * consejos personalizados con IA y simulador «¿qué pasa si…?».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformeScreen(
    viewModel: InformeViewModel,
    onVolver: () -> Unit = {},
    /** Abre el directorio; con una especialidad, ya filtrado por ella. */
    onContactarTecnicos: (especialidad: String?) -> Unit = {}
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val borde = MaterialTheme.colorScheme.outline
    val informe = viewModel.informe
    val c = LocalCadenas.current
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) { if (informe == null) viewModel.cargarUltimoInforme() }
    LaunchedEffect(informe?.id) { viewModel.cargarViviendaDelInforme() }
    LaunchedEffect(informe?.id, AppEstado.idioma) { viewModel.cargarSugerenciasGuardadas() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(c.infTitulo, fontWeight = FontWeight.SemiBold)
                        Text(c.infSubtitulo, color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, c.volver) }
                },
                actions = {
                    if (informe != null) {
                        IconButton(onClick = { compartirInforme(ctx, informe) }) {
                            Icon(Icons.Default.Share, c.comCompartir)
                        }
                    }
                }
            )
        }
    ) { pv ->
        if (viewModel.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = verde)
            }
        } else if (informe == null) {
            Box(Modifier.fillMaxSize().padding(pv), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Assessment, null, tint = gris, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(c.infVacio, color = gris)
                    Text(c.infVacioSub, color = gris, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.cargarUltimoInforme() }) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(c.comReintentar)
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .padding(pv)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Vivienda
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, borde),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(c.infVivienda, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(informe.nombreVivienda, fontWeight = FontWeight.Medium)
                        Text(
                            "${c.infGenerado}: ${sdf.format(Date(informe.fechaGeneracion))}",
                            color = gris, fontSize = 12.sp
                        )
                    }
                }

                // Calificación
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, borde),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(c.infCalificacion, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(c.histEtiqueta, color = gris, fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                EtiquetaBadge(informe.etiqueta)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(c.infEstado, color = gris, fontSize = 12.sp)
                                Text(TextosEnergia.estado(informe.etiqueta, c), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                // Indicadores
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, borde),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(c.infIndicadores, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(c.histConsumo, color = gris, fontSize = 12.sp)
                                Text(Formato.formatEnergiaAnual(informe.consumoEstimado), fontWeight = FontWeight.Medium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(c.histEmisiones, color = gris, fontSize = 12.sp)
                                Text(Formato.formatEmisionesAnual(informe.emisiones), fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(c.infCosteAnual, color = gris, fontSize = 12.sp)
                                Text(Formato.formatMonedaAnual(informe.costeAnual), fontWeight = FontWeight.Medium)
                            }
                            if (informe.consumoPorM2 > 0) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(c.infPorM2, color = gris, fontSize = 12.sp)
                                    Text(Formato.formatIntensidad(informe.consumoPorM2), fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                // Recomendaciones
                if (informe.recomendaciones.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, borde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(c.infRecomendaciones, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(10.dp))
                            informe.recomendaciones.forEachIndexed { i, r ->
                                Text("• ${TextosEnergia.recomendacion(r.titulo, c)}", fontWeight = FontWeight.Medium)
                                val detalle = if (r.ahorroEuros > 0)
                                    "${c.infAhorroEstimado}: ${Formato.formatMonedaAnual(r.ahorroEuros, 0)} (${r.ahorroEstimado}%)"
                                else
                                    "${c.infAhorroEstimado}: ${r.ahorroEstimado}%"
                                Text(detalle, color = gris, fontSize = 12.sp)
                                // Mejoras que hace un profesional: acceso directo al
                                // directorio filtrado por su especialidad
                                Especialidades.paraRecomendacion(r.titulo)?.let { esp ->
                                    TextButton(
                                        onClick = { onContactarTecnicos(esp) },
                                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Search, null, tint = verde, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "${c.infBuscarProfesionales}: ${TextosEnergia.especialidad(esp, c)}",
                                            color = verde, fontSize = 13.sp
                                        )
                                    }
                                }
                                if (i < informe.recomendaciones.lastIndex) Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }

                // Consejos personalizados con IA (Gemini, en el servidor)
                SugerenciasIACard(viewModel, verde, gris)

                // Simulador "¿qué pasa si…?" (solo si la vivienda del informe sigue existiendo)
                if (viewModel.vivienda != null) {
                    SimuladorMejorasCard(viewModel, verde, gris, borde)
                }

                // Botones
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    OutlinedButton(
                        onClick = { informe?.let { compartirInforme(ctx, it) } },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, verde)
                    ) {
                        Icon(Icons.Default.Share, null, tint = verde, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(c.comCompartir, color = verde, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { onContactarTecnicos(null) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = verde),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.AccountBox, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(c.infProfesionales, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

}

/**
 * "¿Qué pasa si…?": el usuario marca mejoras y ve al momento la etiqueta
 * resultante, el ahorro anual, la inversión orientativa y la amortización.
 * Todo se recalcula en local con AlgoritmoEnergetico.simular (sin red).
 */
@Composable
private fun SimuladorMejorasCard(
    viewModel: InformeViewModel,
    verde: Color,
    gris: Color,
    borde: Color
) {
    val c = LocalCadenas.current
    val mejoras = viewModel.mejoras
    val sim = viewModel.simulacion()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, verde.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, null, tint = verde, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(c.simTitulo, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))

            if (mejoras.isEmpty()) {
                Text(c.simSinMejoras, color = gris, fontSize = 13.sp)
                return@Column
            }
            Text(c.simSub, color = gris, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))

            mejoras.forEach { m ->
                val marcada = m.titulo in viewModel.seleccionadas
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.alternarMejora(m.titulo) }
                        .padding(vertical = 2.dp)
                ) {
                    Checkbox(
                        checked = marcada,
                        onCheckedChange = { viewModel.alternarMejora(m.titulo) },
                        colors = CheckboxDefaults.colors(checkedColor = verde)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(TextosEnergia.recomendacion(m.titulo, c), fontSize = 14.sp)
                        Text("~${Formato.formatMoneda(m.inversion, 0)}", color = gris, fontSize = 12.sp)
                    }
                }
            }

            if (sim != null && viewModel.seleccionadas.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = borde)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(c.simNuevaEtiqueta, color = gris, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EtiquetaBadge(sim.etiquetaActual)
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward, null, tint = gris,
                            modifier = Modifier.padding(horizontal = 6.dp).size(18.dp)
                        )
                        EtiquetaBadge(sim.etiquetaNueva)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DatoSimulacion(c.simAhorroAnual, Formato.formatMonedaAnual(sim.ahorroEuros, 0), gris, verde)
                    DatoSimulacion(c.simInversion, Formato.formatMoneda(sim.inversion, 0), gris, null, Alignment.End)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DatoSimulacion(
                        c.simAmortizacion,
                        sim.amortizacionAnios?.let { "${Formato.numero(it)} ${c.simAnios}" } ?: "—",
                        gris, null
                    )
                    DatoSimulacion(c.simCo2Evitado, Formato.formatEmisionesAnual(sim.ahorroCo2, 0), gris, null, Alignment.End)
                }
                Spacer(Modifier.height(10.dp))
                Text(c.simAviso, color = gris, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SugerenciasIACard(viewModel: InformeViewModel, verde: Color, gris: Color) {
    val c = LocalCadenas.current
    val morado = Color(0xFF7C3AED)
    val sug = viewModel.sugerencias

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, morado.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = morado, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(c.iaTitulo, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (sug != null && !viewModel.generandoIA) {
                    TextButton(onClick = { viewModel.generarSugerencias(regenerar = true) }) {
                        Text(c.iaRegenerar, color = morado, fontSize = 13.sp)
                    }
                }
            }

            when {
                viewModel.generandoIA -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = morado, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(c.iaGenerando, color = gris, fontSize = 13.sp)
                }
                sug == null -> {
                    Text(c.iaDescripcion, color = gris, fontSize = 13.sp)
                    Button(
                        onClick = { viewModel.generarSugerencias() },
                        colors = ButtonDefaults.buttonColors(containerColor = morado),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(c.iaGenerar, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                else -> {
                    if (sug.resumen.isNotBlank()) Text(sug.resumen, fontSize = 14.sp)
                    sug.consejos.forEach { consejo ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(morado.copy(alpha = 0.06f))
                                .padding(10.dp)
                        ) {
                            Text(consejo.titulo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(consejo.detalle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val (txtPrioridad, colorPrioridad) = when (consejo.prioridad) {
                                    "alta" -> c.iaPrioridadAlta to Color(0xFFDC2626)
                                    "baja" -> c.iaPrioridadBaja to gris
                                    else -> c.iaPrioridadMedia to Color(0xFFD97706)
                                }
                                EstadoChip(txtPrioridad, colorPrioridad, fontSize = 10)
                                EstadoChip(
                                    when (consejo.coste) {
                                        "bajo" -> c.iaCosteBajo
                                        "alto" -> c.iaCosteAlto
                                        else -> c.iaCosteMedio
                                    },
                                    verde, fontSize = 10
                                )
                            }
                        }
                    }
                    if (sug.habitos.isNotEmpty()) {
                        Text(c.iaHabitos, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        sug.habitos.forEach { Text("• $it", fontSize = 13.sp) }
                    }
                    Text(c.iaAviso, color = gris, fontSize = 11.sp)
                }
            }

            viewModel.errorIA?.let { e ->
                Text(
                    when (e) {
                        ErrorIA.LIMITE_DIARIO -> c.iaErrorLimite
                        ErrorIA.SIN_CONEXION -> c.errorRed
                        ErrorIA.NO_DISPONIBLE -> c.iaErrorNoDisponible
                    },
                    color = MaterialTheme.colorScheme.error, fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun DatoSimulacion(
    etiqueta: String,
    valor: String,
    gris: Color,
    colorValor: Color?,
    alineacion: Alignment.Horizontal = Alignment.Start
) {
    Column(horizontalAlignment = alineacion) {
        Text(etiqueta, color = gris, fontSize = 12.sp)
        Text(
            valor,
            fontWeight = FontWeight.SemiBold,
            color = colorValor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

