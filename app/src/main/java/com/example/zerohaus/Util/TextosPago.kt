package com.example.zerohaus.Util

import com.example.zerohaus.Modelos.AccionPago
import com.example.zerohaus.Modelos.AvisoFlujo
import com.example.zerohaus.Modelos.ConceptoPago
import com.example.zerohaus.Modelos.EstadoCobros
import com.example.zerohaus.Modelos.ErrorPago
import com.example.zerohaus.Modelos.EstadoPago

/*
 * Texto (en el idioma de [AppCadenas]) de cada estado, error y aviso de pago.
 * Los `when` son exhaustivos: un valor nuevo en un enum no compila sin su texto.
 */

fun AppCadenas.textoEstado(e: EstadoPago): String = when (e) {
    EstadoPago.SOLICITADO -> pagoEstSolicitado
    EstadoPago.PENDIENTE_PAGO -> pagoEstPendiente
    EstadoPago.RETENIDO -> pagoEstRetenido
    EstadoPago.TERMINADO -> pagoEstTerminado
    EstadoPago.LIBERADO -> pagoEstLiberado
    EstadoPago.CANCELADO -> pagoEstCancelado
    EstadoPago.EN_DISPUTA -> pagoEstEnDisputa
    EstadoPago.REEMBOLSADO -> pagoEstReembolsado
    EstadoPago.CONTRACARGO -> pagoEstContracargo
    EstadoPago.FALLIDO -> pagoEstFallido
    EstadoPago.DESCONOCIDO -> pagoEstDesconocido
}

fun AppCadenas.textoError(e: ErrorPago): String = when (e) {
    ErrorPago.PAGOS_NO_DISPONIBLES -> pagoErrPagosNoDisponibles
    ErrorPago.IMPORTE_BAJO_MINIMO -> pagoErrImporteMinimo
    ErrorPago.IMPORTE_SOBRE_MAXIMO -> pagoErrImporteMaximo
    ErrorPago.IMPORTE_NO_VALIDO -> pagoErrImporteNoValido
    ErrorPago.DESCRIPCION_NO_VALIDA -> pagoErrDescripcionNoValida
    ErrorPago.MOTIVO_NO_VALIDO -> pagoErrMotivoNoValido
    ErrorPago.TRANSICION_NO_PERMITIDA -> pagoErrTransicion
    ErrorPago.NO_AUTORIZADO -> pagoErrNoAutorizado
    ErrorPago.BLOQUEO -> pagoErrBloqueo
    ErrorPago.CHAT_NO_VALIDO -> pagoErrChatNoValido
    ErrorPago.COBRO_NO_VALIDO -> pagoErrCobroNoValido
    ErrorPago.OPERACION_EN_CURSO -> pagoErrOperacionEnCurso
    ErrorPago.STRIPE_NO_RESPONDE -> pagoErrStripeNoResponde
    ErrorPago.EMAIL_SIN_VERIFICAR -> pagoErrEmailSinVerificar
    ErrorPago.CUENTA_STRIPE_NO_ACTIVA -> pagoErrCuentaNoActiva
    ErrorPago.PAGO_NO_ENCONTRADO -> pagoErrPagoNoEncontrado
    ErrorPago.LIMITE -> pagoErrLimite
    ErrorPago.SIN_SESION -> authErrSinSesion
    ErrorPago.RED -> errorRed
    ErrorPago.RESPUESTA_NO_VALIDA -> pagoErrRespuestaNoValida
    ErrorPago.DESCONOCIDO -> authErrGenerico
}

fun AppCadenas.textoAccion(a: AccionPago, reanudar: Boolean = false): String = when (a) {
    AccionPago.ACEPTAR -> if (reanudar) pagoAccReanudar else pagoAccAceptar
    AccionPago.REINTENTAR -> pagoAccReintentar
    AccionPago.CANCELAR -> pagoAccCancelar
    AccionPago.TERMINAR -> pagoAccTerminar
    AccionPago.CONFIRMAR -> pagoAccConfirmar
    AccionPago.DISPUTAR -> pagoAccDisputar
}

/** Texto del aviso de confirmación de las acciones que lo piden (cancelar, terminar, confirmar). */
fun AppCadenas.textoAvisoAccion(a: AccionPago): String? = when (a) {
    AccionPago.CANCELAR -> pagoAvisoCancelar
    AccionPago.TERMINAR -> pagoAvisoTerminar
    AccionPago.CONFIRMAR -> pagoAvisoConfirmar
    else -> null
}

fun AppCadenas.textoConcepto(c: ConceptoPago): String = when (c) {
    ConceptoPago.IMPORTE -> pagoImporte
    ConceptoPago.COMISION -> pagoComision
    ConceptoPago.NETO -> pagoNeto
}

fun AppCadenas.textoAvisoFlujo(a: AvisoFlujo): String? = when (a) {
    AvisoFlujo.EnMarcha -> pagoFlujoProcesando
    AvisoFlujo.Retenido -> pagoFlujoRetenido
    AvisoFlujo.CobroFallido -> pagoFlujoFallido
    AvisoFlujo.Caducado -> pagoFlujoCaducado
    AvisoFlujo.PagoCancelado -> pagoFlujoCanceladoPago
    AvisoFlujo.Confirmando -> pagoFlujoConfirmando
    AvisoFlujo.HojaCerrada -> pagoFlujoCancelado
    AvisoFlujo.ErrorHoja -> pagoFlujoErrorHoja
    is AvisoFlujo.Error -> textoError(a.error)
    AvisoFlujo.Ninguno -> null
}

fun AppCadenas.textoCobros(e: EstadoCobros): String? = when (e) {
    EstadoCobros.SIN_CUENTA -> cobrosEstSin
    EstadoCobros.PENDIENTE -> cobrosEstPendiente
    EstadoCobros.ACTIVA -> cobrosEstActiva
    EstadoCobros.RESTRINGIDA -> cobrosEstRestringida
    EstadoCobros.DESCONOCIDO -> null
}

/** «…a los {n} días…» con los días de liberación del pago. */
fun AppCadenas.textoRetencion(dias: Int): String =
    (if (dias == 1) pagoRetencionAvisoDia else pagoRetencionAviso).replace("{n}", dias.toString())
