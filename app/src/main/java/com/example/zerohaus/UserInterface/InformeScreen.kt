
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
import com.example.zerohaus.Modelos.Vivienda
import com.example.zerohaus.Repositorios.AlgoritmoEnergetico
import com.example.zerohaus.Repositorios.DeduccionIrpf
import com.example.zerohaus.Repositorios.ErrorIA
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.Formato
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.Util.TextosEnergia
import com.example.zerohaus.ViewModel.InformeViewModel
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
    val sdf = remember(AppEstado.idioma) { Formato.fechas("dd/MM/yyyy") }
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

                // Informe hecho con el método anterior (etiqueta por consumo total):
                // se puede recalcular si la vivienda sigue existiendo
                if (informe.energiaPrimariaM2 <= 0 && viewModel.vivienda != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(c.infMetodoAnterior, color = Color(0xFF92400E), fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = viewModel::recalcular,
                                enabled = !viewModel.recalculando,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (viewModel.recalculando) {
                                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(c.infRecalcular, color = Color.White)
                            }
                        }
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
                        // El dato que decide la letra (informes con el modelo por usos)
                        if (informe.energiaPrimariaM2 > 0) {
                            Spacer(Modifier.height(10.dp))
                            Text(c.infEnergiaPrimaria, color = gris, fontSize = 12.sp)
                            Text(Formato.formatIntensidad(informe.energiaPrimariaM2), fontWeight = FontWeight.Medium)
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

                // Factura de la luz: consumo real frente al estimado y precio usado
                if (informe.consumoLuzFactura > 0) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, borde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(c.infFactTitulo, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(c.infFactReal, color = gris, fontSize = 12.sp)
                                    Text(Formato.formatEnergiaAnual(informe.consumoLuzFactura, 0), fontWeight = FontWeight.Medium)
                                }
                                if (informe.consumoLuzEstimado > 0) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(c.infFactEstimado, color = gris, fontSize = 12.sp)
                                        Text(Formato.formatEnergiaAnual(informe.consumoLuzEstimado, 0), fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                            if (informe.precioLuz > 0) {
                                Spacer(Modifier.height(10.dp))
                                Text(c.infFactPrecio, color = gris, fontSize = 12.sp)
                                Text("${Formato.formatMoneda(informe.precioLuz, 3)}/kWh", fontWeight = FontWeight.Medium)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(c.infFactNota, color = gris, fontSize = 12.sp)
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

                // Plan de reforma por etapas (pasaporte de renovación)
                viewModel.vivienda?.let { v ->
                    PlanEtapasCard(v, informe.nombreVivienda, verde, gris, borde)
                }

                // Botones
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    OutlinedButton(
                        onClick = { compartirInforme(ctx, informe) },
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
                val deduccion = sim.deduccion
                if (deduccion != null && DeduccionIrpf.vigente()) {
                    Spacer(Modifier.height(12.dp))
                    DeduccionIrpfBloque(deduccion, sim.inversion, sim.ahorroEuros, verde, gris)
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

/** Deducción del IRPF que darían las mejoras marcadas en el simulador. */
@Composable
private fun DeduccionIrpfBloque(
    d: DeduccionIrpf.Resultado,
    inversion: Double,
    ahorroAnual: Double,
    verde: Color,
    gris: Color
) {
    val c = LocalCadenas.current
    val neta = inversion - d.importe
    Column(
        Modifier
            .fillMaxWidth()
            .background(verde.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(c.irpfTitulo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("${porcentaje(d.porcentaje)} · ${Formato.formatMoneda(d.importe, 0)}", fontWeight = FontWeight.Bold, color = verde)
        }
        Text(
            when (d.motivo) {
                DeduccionIrpf.Motivo.ENERGIA_PRIMARIA -> "${c.irpfMotivoPrimaria} ${porcentaje(d.reduccionPct)}"
                DeduccionIrpf.Motivo.ETIQUETA -> c.irpfMotivoEtiqueta
                DeduccionIrpf.Motivo.DEMANDA -> "${c.irpfMotivoDemanda} ${porcentaje(d.reduccionPct)}"
            },
            color = gris, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        // Una fila por dato: en paralelo, las etiquetas largas se pisaban
        FilaValor(c.irpfInversionNeta, Formato.formatMoneda(neta, 0), gris)
        if (ahorroAnual > 0) {
            FilaValor(c.irpfAmortizacionNeta, "${Formato.numero(neta / ahorroAnual)} ${c.simAnios}", gris)
        }
        Spacer(Modifier.height(8.dp))
        Text("${c.irpfAviso} ${c.irpfObrasHasta} ${fechaCorta(DeduccionIrpf.fechaLimite)}.", color = gris, fontSize = 11.sp)
    }
}

@Composable
private fun FilaValor(etiqueta: String, valor: String, gris: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, color = gris, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(valor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** "30 %" con espacio irrompible, para que el % no salte solo a la línea siguiente. */
private fun porcentaje(valor: Int) = "$valor %"

/** "31/12/2026", siempre con cifras latinas (como el resto de números de la app). */
private fun fechaCorta(fecha: java.time.LocalDate): String =
    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(fecha)

/**
 * Plan de reforma por etapas: en cada paso, la mejora que antes se amortiza
 * sobre cómo ha quedado la vivienda. Se puede compartir en PDF.
 */
@Composable
private fun PlanEtapasCard(vivienda: Vivienda, nombreVivienda: String, verde: Color, gris: Color, borde: Color) {
    val c = LocalCadenas.current
    val ctx = LocalContext.current
    val etapas = remember(vivienda) { AlgoritmoEnergetico.planPorEtapas(vivienda) }
    if (etapas.isEmpty()) return
    val etiquetaInicial = remember(vivienda) {
        AlgoritmoEnergetico.etiquetaPara(AlgoritmoEnergetico.balance(vivienda).energiaPrimariaM2)
    }
    // Paso a partir del cual se llega a cada porcentaje de deducción (igual que en el PDF)
    val deducciones = remember(etapas) { deduccionPorPaso(etapas) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borde),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Route, null, tint = verde, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(c.planTitulo, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(c.planSub, color = gris, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))

            etapas.forEachIndexed { i, e ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 6.dp)) {
                    Box(
                        Modifier.size(26.dp).background(verde.copy(alpha = 0.15f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text("${i + 1}", color = verde, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(TextosEnergia.recomendacion(e.mejora.titulo, c), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "~${Formato.formatMoneda(e.mejora.inversion, 0)} · ${c.planAhorra} ${Formato.formatMonedaAnual(e.ahorroEuros, 0)} · " +
                                "${c.planSeAmortiza} ${Formato.numero(e.amortizacionAnios)} ${c.simAnios}",
                            color = gris, fontSize = 12.sp
                        )
                        if (deducciones[i] > 0) {
                            Text("${c.planDeduccion} ${porcentaje(deducciones[i])}", color = verde, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    EtiquetaBadge(e.etiqueta)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = borde)
            val final = etapas.last()
            Text(c.planTotal, color = gris, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                // Con peso: en idiomas largos el texto salta de línea en vez de aplastar las etiquetas
                Column(Modifier.weight(1f)) {
                    Text("${c.simInversion}: ${Formato.formatMoneda(final.inversionAcumulada, 0)}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("${c.simAhorroAnual}: ${Formato.formatMonedaAnual(final.ahorroAcumulado, 0)}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = verde)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EtiquetaBadge(etiquetaInicial)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = gris, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp))
                    EtiquetaBadge(final.etiqueta)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { compartirPlan(ctx, nombreVivienda, etiquetaInicial, etapas) },
                border = BorderStroke(1.dp, verde),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, null, tint = verde, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(c.planCompartir, color = verde)
            }
        }
    }
}

