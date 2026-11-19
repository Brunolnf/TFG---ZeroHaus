package com.example.zerohaus.UserInterface

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Modelos.FormatoPago
import com.example.zerohaus.Modelos.Pago
import com.example.zerohaus.Modelos.PresentacionPagos
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.Util.textoError
import com.example.zerohaus.Util.textoEstado
import com.example.zerohaus.ViewModel.PagosViewModel

/**
 * Mis pagos: como cliente y como profesional (en pestañas, solo los
 * profesionales ven las dos), y para el profesional, «Configurar cobros».
 * Sin `pagos_habilitados` no se muestra nada de pagos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagosScreen(
    viewModel: PagosViewModel,
    onVolver: () -> Unit,
    onAbrirPago: (String) -> Unit
) {
    val c = LocalCadenas.current
    val estado = viewModel.estado
    val profesional = PresentacionPagos.esProfesional(AppEstado.tipoUsuarioCache)
    var pestana by remember { mutableIntStateOf(if (profesional) 1 else 0) }

    LaunchedEffect(Unit) {
        // Los pagos que ya existen se ven siempre; solo «cobros» depende del interruptor
        viewModel.refrescarDisponibilidad()
        viewModel.cargar()
        if (PresentacionPagos.mostrarCobros(viewModel.estado.disponibles, profesional)) viewModel.cargarCuenta()
    }
    HojaDePagoEfecto(viewModel)

    val toast = mensajeToastPagos(c, estado)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            ZeroToast(
                mensaje = toast?.first,
                tipo = toast?.second ?: ToastTipo.ERROR,
                alOcultar = { viewModel.toastVisto() }
            )
        },
        topBar = {
            TopAppBar(
                title = { Text(c.pagosTitulo, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, c.volver) } }
            )
        }
    ) { pv ->
        Column(Modifier.padding(pv).fillMaxSize()) {
            if (profesional) {
                TabRow(selectedTabIndex = pestana) {
                    Tab(selected = pestana == 0, onClick = { pestana = 0 }, text = { Text(c.pagosComoCliente) })
                    Tab(selected = pestana == 1, onClick = { pestana = 1 }, text = { Text(c.pagosComoProfesional) })
                }
            }
            AvisoFlujoPago(estado.flujoPago, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            val lista = if (pestana == 1 && profesional) estado.comoProfesional else estado.comoCliente
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (PresentacionPagos.mostrarCobros(estado.disponibles, profesional) && pestana == 1) {
                    item(key = "cobros") { TarjetaCobros(estado, viewModel) }
                }
                if (estado.cargandoListas && lista.isEmpty()) {
                    item(key = "cargando") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                } else if (lista.isEmpty()) {
                    item(key = "vacio") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(estado.errorListas?.let { c.textoError(it) } ?: c.pagosVacio, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(lista, key = { it.id }) { p -> FilaPago(p) { onAbrirPago(p.id) } }
                }
            }
        }
    }
}

@Composable
private fun FilaPago(pago: Pago, onClick: () -> Unit) {
    val c = LocalCadenas.current
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(pago.descripcion.ifBlank { c.pagosTitulo }, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 2)
                EstadoChip(c.textoEstado(pago.estado), colorEstadoPago(pago.estado))
            }
            Spacer(Modifier.width(8.dp))
            Text(FormatoPago.importe(pago.importeCent, pago.moneda), fontWeight = FontWeight.Bold)
        }
    }
}

/** Detalle de un pago: desglose según mi papel, historial, liberación prevista y acciones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagoDetalleScreen(
    viewModel: PagosViewModel,
    pagoId: String,
    onVolver: () -> Unit
) {
    val c = LocalCadenas.current
    val estado = viewModel.estado

    LaunchedEffect(pagoId) {
        viewModel.refrescarDisponibilidad()
        viewModel.abrirDetalle(pagoId)
    }
    DisposableEffect(Unit) { onDispose { viewModel.cerrarDetalle() } }
    HojaDePagoEfecto(viewModel)

    val toast = mensajeToastPagos(c, estado)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            ZeroToast(
                mensaje = toast?.first,
                tipo = toast?.second ?: ToastTipo.ERROR,
                alOcultar = { viewModel.toastVisto() }
            )
        },
        topBar = {
            TopAppBar(
                title = { Text(c.pagoDetalleTitulo, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, c.volver) } }
            )
        }
    ) { pv ->
        Column(Modifier.padding(pv).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val pago = estado.detalle
            when {
                pago != null -> {
                    AvisoFlujoPago(estado.flujoPago)
                    TarjetaPago(pago, estado, viewModel, detalle = true)
                }
                estado.cargandoDetalle -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> Text(estado.errorDetalle?.let { c.textoError(it) } ?: "", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
