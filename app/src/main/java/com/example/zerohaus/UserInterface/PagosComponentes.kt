package com.example.zerohaus.UserInterface

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.zerohaus.Modelos.AccionPago
import com.example.zerohaus.Modelos.AvisoFlujo
import com.example.zerohaus.Modelos.EstadoCobros
import com.example.zerohaus.Modelos.EstadoPago
import com.example.zerohaus.Modelos.ErrorPago
import com.example.zerohaus.Modelos.FormatoPago
import com.example.zerohaus.Modelos.Pago
import com.example.zerohaus.Modelos.PresentacionPagos
import com.example.zerohaus.Modelos.ValidacionPago
import com.example.zerohaus.Modelos.ValidacionSolicitud
import com.example.zerohaus.Util.AppCadenas
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.Formato
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.Util.rememberHojaDePago
import com.example.zerohaus.Util.textoAccion
import com.example.zerohaus.Util.textoAvisoAccion
import com.example.zerohaus.Util.textoAvisoFlujo
import com.example.zerohaus.Util.textoCobros
import com.example.zerohaus.Util.textoConcepto
import com.example.zerohaus.Util.textoError
import com.example.zerohaus.Util.textoEstado
import com.example.zerohaus.Util.textoRetencion
import com.example.zerohaus.ViewModel.FlujoPago
import com.example.zerohaus.ViewModel.PagosEstado
import com.example.zerohaus.ViewModel.PagosViewModel

/* Piezas de la interfaz de pagos que comparten el chat y las pantallas de pagos. */

/** Importe en el formato numérico del idioma de la app; el símbolo € no cambia. */
fun importeApp(cent: Long, moneda: String): String = FormatoPago.importe(cent, moneda, Formato.locale())

fun colorEstadoPago(e: EstadoPago): Color = when (e) {
    EstadoPago.RETENIDO, EstadoPago.LIBERADO -> Color(0xFF16A34A)
    EstadoPago.TERMINADO -> Color(0xFF2563EB)
    EstadoPago.SOLICITADO, EstadoPago.PENDIENTE_PAGO -> Color(0xFFD97706)
    EstadoPago.EN_DISPUTA, EstadoPago.FALLIDO, EstadoPago.CONTRACARGO -> Color(0xFFDC2626)
    EstadoPago.CANCELADO, EstadoPago.REEMBOLSADO, EstadoPago.DESCONOCIDO -> Color(0xFF6B7280)
}

/**
 * Hoja de pago de Stripe: se llama siempre, fuera de cualquier `if` (registra un
 * launcher). Presenta la hoja cuando el flujo llega a Listo y cierra el flujo
 * cuando ya no hay nada más que contar.
 */
@Composable
fun HojaDePagoEfecto(vm: PagosViewModel) {
    val presentar = rememberHojaDePago(onResultado = vm::resultadoHoja)
    val flujo = vm.estado.flujoPago
    LaunchedEffect(flujo) {
        if (flujo is FlujoPago.Listo) {
            vm.hojaPresentada()
            presentar(flujo.datos)
        }
    }
    // Un final sin nada que decir (otro dispositivo cambió el pago): se cierra sin aviso
    LaunchedEffect(flujo) {
        if (PresentacionPagos.esFinalDeFlujo(flujo) && PresentacionPagos.avisoFlujo(flujo) == AvisoFlujo.Ninguno) vm.flujoVisto()
    }
}

/** Mensaje emergente de pagos: el final del flujo de pago, o el último error de una acción. */
fun mensajeToastPagos(c: AppCadenas, e: PagosEstado, incluirError: Boolean = true): Pair<String, ToastTipo>? {
    if (PresentacionPagos.esFinalDeFlujo(e.flujoPago)) {
        val aviso = PresentacionPagos.avisoFlujo(e.flujoPago)
        val texto = c.textoAvisoFlujo(aviso) ?: return null
        val tipo = when (aviso) {
            AvisoFlujo.Retenido -> ToastTipo.EXITO
            AvisoFlujo.HojaCerrada -> ToastTipo.INFO
            else -> ToastTipo.ERROR
        }
        return texto to tipo
    }
    if (incluirError) e.error?.let { return c.textoError(it) to ToastTipo.ERROR }
    return null
}

/** Cierra el mensaje de [mensajeToastPagos]. */
fun PagosViewModel.toastVisto() {
    if (PresentacionPagos.esFinalDeFlujo(estado.flujoPago)) flujoVisto() else errorVisto()
}

/** Barra de progreso con el texto mientras el pago está en marcha. */
@Composable
fun AvisoFlujoPago(flujo: FlujoPago, modifier: Modifier = Modifier) {
    val c = LocalCadenas.current
    if (PresentacionPagos.avisoFlujo(flujo) != AvisoFlujo.EnMarcha) return
    Column(modifier.fillMaxWidth()) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            c.pagoFlujoProcesando, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

/**
 * Tarjeta de un pago con sus acciones (las de mi papel en su estado). Con
 * [detalle] añade el desglose y el historial.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaPago(
    pago: Pago,
    estado: PagosEstado,
    vm: PagosViewModel,
    detalle: Boolean = false,
    onAbrir: (() -> Unit)? = null
) {
    val c = LocalCadenas.current
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val sdf = remember(AppEstado.idioma) { Formato.fechas("dd/MM/yyyy HH:mm") }
    val acciones = estado.acciones(pago)
    val papel = estado.papel(pago)
    // Sobreviven a girar la pantalla
    var confirmando by rememberSaveable { mutableStateOf<AccionPago?>(null) }
    var disputando by rememberSaveable { mutableStateOf(false) }
    val flujo = estado.flujoPago
    val enCursoAqui = estado.accionEnCurso?.pagoId == pago.id || (flujo is FlujoPago.Preparando && flujo.pagoId == pago.id)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // Con un pago en marcha no se sale a otra pantalla: la hoja y su resultado viven en esta
        modifier = Modifier.fillMaxWidth().then(
            if (onAbrir != null && !estado.ocupado) Modifier.clickable(onClick = onAbrir) else Modifier
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pago.descripcion.ifBlank { c.pagosTitulo },
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    maxLines = if (detalle) 10 else 2, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(importeApp(pago.importeCent, pago.moneda), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            EstadoChip(c.textoEstado(pago.estado), colorEstadoPago(pago.estado))

            if (detalle) {
                PresentacionPagos.desglose(pago, papel).forEach { l ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(c.textoConcepto(l.concepto), color = gris, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(importeApp(l.cent, pago.moneda), fontSize = 13.sp)
                    }
                }
            }
            PresentacionPagos.liberacionPrevista(pago)?.let {
                Text("${c.pagoLiberacionPrevista}: ${sdf.format(java.util.Date(it))}", color = gris, fontSize = 12.sp)
            }

            if (acciones.isNotEmpty()) {
                if (PresentacionPagos.avisoRetencion(acciones)) {
                    Text(c.textoRetencion(pago.liberacionDias), color = gris, fontSize = 12.sp)
                }
                if (enCursoAqui) LinearProgressIndicator(Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    acciones.forEach { a ->
                        val principal = a.abreHojaDePago || a == AccionPago.CONFIRMAR || a == AccionPago.TERMINAR
                        val onClick = {
                            when {
                                a.abreHojaDePago -> vm.pagar(pago)
                                a == AccionPago.DISPUTAR -> disputando = true
                                else -> confirmando = a
                            }
                        }
                        val texto = c.textoAccion(a, PresentacionPagos.esReanudar(pago, a))
                        if (principal) Button(onClick = onClick, enabled = !estado.ocupado, shape = RoundedCornerShape(10.dp)) { Text(texto) }
                        else OutlinedButton(onClick = onClick, enabled = !estado.ocupado, shape = RoundedCornerShape(10.dp)) { Text(texto) }
                    }
                }
            }

            if (detalle && pago.historial.isNotEmpty()) {
                HorizontalDivider()
                Text(c.pagoHistorial, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                pago.historial.asReversed().forEach { h ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(c.textoEstado(h.estado), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        if (h.fecha > 0) Text(sdf.format(java.util.Date(h.fecha)), fontSize = 12.sp, color = gris)
                    }
                }
            }
        }
    }

    confirmando?.let { a ->
        AlertDialog(
            onDismissRequest = { confirmando = null },
            title = { Text(c.textoAccion(a), fontWeight = FontWeight.SemiBold) },
            text = { Text(c.textoAvisoAccion(a).orEmpty()) },
            confirmButton = {
                Button(onClick = {
                    confirmando = null
                    when (a) {
                        AccionPago.CANCELAR -> vm.cancelar(pago)
                        AccionPago.TERMINAR -> vm.terminar(pago)
                        AccionPago.CONFIRMAR -> vm.confirmar(pago)
                        else -> Unit
                    }
                }) { Text(c.textoAccion(a)) }
            },
            dismissButton = { TextButton(onClick = { confirmando = null }) { Text(c.cancelar) } }
        )
    }
    if (disputando) {
        DialogoMotivoDisputa(
            onEnviar = { motivo -> disputando = false; vm.disputar(pago, motivo) },
            onCancelar = { disputando = false }
        )
    }
}

@Composable
fun DialogoMotivoDisputa(onEnviar: (String) -> Unit, onCancelar: () -> Unit) {
    val c = LocalCadenas.current
    var motivo by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<ErrorPago?>(null) }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(c.pagoDisputaTitulo, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.pagoDisputaAviso, fontSize = 13.sp)
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it.take(FormatoPago.MOTIVO_DISPUTA_MAX + 1); error = null },
                    label = { Text(c.pagoMotivoDisputa) },
                    minLines = 3, maxLines = 6,
                    isError = error != null,
                    supportingText = {
                        Text(error?.let { c.textoError(it) } ?: "${motivo.trim().length}/${FormatoPago.MOTIVO_DISPUTA_MAX}")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val e = ValidacionPago.motivoDisputa(motivo)
                if (e != null) error = e else onEnviar(motivo.trim())
            }) { Text(c.pagoAccDisputar) }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text(c.cancelar) } }
    )
}

/** Diálogo del profesional: importe en euros y descripción. El mínimo y el máximo los valida el servidor. */
@Composable
fun DialogoSolicitarPago(
    solicitando: Boolean,
    errorServidor: ErrorPago?,
    onEnviar: (importeCent: Long, descripcion: String) -> Unit,
    onCancelar: () -> Unit,
    onConfigurarCobros: () -> Unit
) {
    val c = LocalCadenas.current
    var importe by rememberSaveable { mutableStateOf("") }
    var descripcion by rememberSaveable { mutableStateOf("") }
    var errorLocal by rememberSaveable { mutableStateOf<ErrorPago?>(null) }
    val error = errorLocal ?: errorServidor
    AlertDialog(
        onDismissRequest = { if (!solicitando) onCancelar() },
        title = { Text(c.pagoSolicitarTitulo, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = importe,
                    onValueChange = { importe = it.take(10); errorLocal = null },
                    label = { Text(c.pagoImporteEuros) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it.take(FormatoPago.DESCRIPCION_MAX + 1); errorLocal = null },
                    label = { Text(c.pagoDescripcion) },
                    minLines = 2, maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(c.textoError(it), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    if (it == ErrorPago.CUENTA_STRIPE_NO_ACTIVA) {
                        TextButton(onClick = onConfigurarCobros) { Text(c.cobrosConfigurar) }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = !solicitando, onClick = {
                when (val v = ValidacionPago.solicitud(importe, descripcion)) {
                    is ValidacionSolicitud.Invalida -> errorLocal = v.error
                    is ValidacionSolicitud.Valida -> onEnviar(v.importeCent, v.descripcion)
                }
            }) {
                if (solicitando) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(c.pagoEnviarSolicitud)
            }
        },
        dismissButton = { TextButton(enabled = !solicitando, onClick = onCancelar) { Text(c.cancelar) } }
    )
}

/**
 * «Configurar cobros» del profesional: estado de su cuenta de Stripe y el
 * botón que abre el alta fuera de la app. Al volver, pregunta a Stripe.
 */
@Composable
fun TarjetaCobros(estado: PagosEstado, vm: PagosViewModel) {
    val c = LocalCadenas.current
    val context = LocalContext.current
    var esperandoVuelta by remember { mutableStateOf(false) }
    var sinNavegador by remember { mutableStateOf(false) }

    LaunchedEffect(estado.enlaceCuenta) {
        val url = estado.enlaceCuenta ?: return@LaunchedEffect
        vm.enlaceAbierto()
        try {
            esperandoVuelta = true
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            esperandoVuelta = false
            sinNavegador = true
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME && esperandoVuelta) {
                esperandoVuelta = false
                vm.refrescarCuenta()
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    // Si la pantalla se va mientras se pide el enlace, no se abre el navegador más tarde
    DisposableEffect(Unit) { onDispose { vm.descartarEnlace() } }

    val cobros = PresentacionPagos.estadoCobros(estado.cuentaStripe, estado.cuentaCargada)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(c.cobrosTitulo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            c.textoCobros(cobros)?.let {
                EstadoChip(it, if (cobros == EstadoCobros.ACTIVA) Color(0xFF16A34A) else Color(0xFFD97706))
            }
            if (cobros != EstadoCobros.ACTIVA) {
                Text(c.cobrosExplicacion, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (sinNavegador) Text(c.sinAppParaAbrir, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            estado.errorCuenta?.let { Text(c.textoError(it), color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cobros != EstadoCobros.ACTIVA && cobros != EstadoCobros.DESCONOCIDO) {
                    Button(
                        onClick = { sinNavegador = false; vm.crearEnlaceCuenta() },
                        enabled = !estado.pidiendoEnlace,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (estado.pidiendoEnlace) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(if (cobros == EstadoCobros.SIN_CUENTA) c.cobrosConfigurar else c.cobrosCompletar)
                    }
                }
                OutlinedButton(onClick = { vm.refrescarCuenta() }, shape = RoundedCornerShape(10.dp)) { Text(c.cobrosActualizar) }
            }
        }
    }
}
