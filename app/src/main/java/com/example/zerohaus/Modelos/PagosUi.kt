package com.example.zerohaus.Modelos

import com.example.zerohaus.ViewModel.FlujoPago
import com.example.zerohaus.ViewModel.PagosEstado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformWhile

/**
 * Corta el flujo tras el primer error. Un listener de Firestore que falla ya no
 * emite más: si el flujo siguiera abierto, quien lo recoge creería que sigue
 * escuchando y no volvería a suscribirse.
 */
fun <T> Flow<ResultadoPago<T>>.hastaElPrimerError(): Flow<ResultadoPago<T>> =
    transformWhile { emit(it); it !is ResultadoPago.Error }

/*
 * Lógica de presentación de los pagos (T-E8), pura y sin Compose: qué se
 * muestra y qué se ofrece según el estado. Las pantallas solo ponen el texto
 * (los 14 idiomas) y el dibujo. El servidor sigue siendo el único que decide.
 */

/** Resultado de validar lo que escribe el profesional al pedir un pago. */
sealed interface ValidacionSolicitud {
    data class Valida(val importeCent: Long, val descripcion: String) : ValidacionSolicitud
    data class Invalida(val error: ErrorPago) : ValidacionSolicitud
}

object ValidacionPago {

    /**
     * Importe en euros («25», «25,5», «25.50») y descripción. Solo la forma:
     * el mínimo y el máximo los valida el servidor y la app muestra su error.
     */
    fun solicitud(importeTexto: String, descripcion: String): ValidacionSolicitud {
        val cent = FormatoPago.centimosDesdeTexto(importeTexto)
        if (cent == null || FormatoPago.centimosParaEnviar(cent) == null) {
            return ValidacionSolicitud.Invalida(ErrorPago.IMPORTE_NO_VALIDO)
        }
        val texto = descripcion.trim()
        if (texto.isEmpty() || texto.length > FormatoPago.DESCRIPCION_MAX) {
            return ValidacionSolicitud.Invalida(ErrorPago.DESCRIPCION_NO_VALIDA)
        }
        return ValidacionSolicitud.Valida(cent, texto)
    }

    /** Motivo de una disputa: null si vale, o el error a mostrar. */
    fun motivoDisputa(motivo: String): ErrorPago? {
        val texto = motivo.trim()
        return if (texto.isEmpty() || texto.length > FormatoPago.MOTIVO_DISPUTA_MAX) ErrorPago.MOTIVO_NO_VALIDO else null
    }
}

/** Qué hace «Solicitar pago» ahora mismo. */
enum class DestinoSolicitar {
    /** Cuenta de Stripe activa: se abre el diálogo de importe y descripción. */
    DIALOGO,
    /** Sin cuenta activa: se lleva a «Configurar cobros». */
    COBROS,
    /** Aún no se sabe el estado de la cuenta. */
    ESPERAR
}

/** Estado de los cobros del profesional tal como se le explica. */
enum class EstadoCobros { SIN_CUENTA, PENDIENTE, ACTIVA, RESTRINGIDA, DESCONOCIDO }

/** Una línea del desglose de un pago. */
enum class ConceptoPago { IMPORTE, COMISION, NETO }

data class LineaDesglose(val concepto: ConceptoPago, val cent: Long)

/** Cómo se cuenta al usuario el flujo de pago con PaymentSheet. */
sealed interface AvisoFlujo {
    /** Se está preparando, abriendo la hoja o esperando al banco. */
    data object EnMarcha : AvisoFlujo
    /** El webhook confirmó el cobro: dinero retenido. Solo tras [FlujoPago.Resuelto]. */
    data object Retenido : AvisoFlujo
    data object CobroFallido : AvisoFlujo
    /** El cobro caducó y el servidor canceló el pago. */
    data object Caducado : AvisoFlujo
    /** Stripe dijo Completed pero el servidor aún no lo ha confirmado (pasó el límite de espera). */
    data object Confirmando : AvisoFlujo
    /** Lo canceló la otra parte (no caducó). */
    data object PagoCancelado : AvisoFlujo
    /** Cerró la hoja sin pagar: el pago sigue pendiente. */
    data object HojaCerrada : AvisoFlujo
    /** Falló la hoja de Stripe (no el servidor). */
    data object ErrorHoja : AvisoFlujo
    data class Error(val error: ErrorPago) : AvisoFlujo
    /** Resuelto en un estado que no necesita aviso (otro dispositivo, etc.). */
    data object Ninguno : AvisoFlujo
}

object PresentacionPagos {

    /** Cuentas de profesional: técnicos y empresas (el tipo que guarda la app). */
    fun esProfesional(tipoUsuario: String?): Boolean = tipoUsuario == "Técnico" || tipoUsuario == "Empresa"

    /** «Solicitar pago» en el chat: solo el profesional y con los pagos disponibles. */
    fun ofreceSolicitar(disponibles: Boolean, soyProfesional: Boolean): Boolean = disponibles && soyProfesional

    /**
     * Con `pagos_habilitados` apagado el servidor solo corta lo que abre dinero
     * nuevo (alta en Connect, solicitar, aceptar, reintentar). Por eso «Configurar
     * cobros» depende de [disponibles] y la tarjeta solo la ve un profesional.
     */
    fun mostrarCobros(disponibles: Boolean, soyProfesional: Boolean): Boolean = disponibles && soyProfesional

    /** La fila «Pagos» de Ajustes: con pagos disponibles, o si ya tengo alguno que ver o resolver. */
    fun mostrarEntradaAjustes(disponibles: Boolean, tengoPagos: Boolean): Boolean = disponibles || tengoPagos

    fun destinoSolicitar(e: PagosEstado): DestinoSolicitar = when {
        e.puedeSolicitar -> DestinoSolicitar.DIALOGO
        !e.cuentaCargada -> DestinoSolicitar.ESPERAR
        else -> DestinoSolicitar.COBROS
    }

    fun estadoCobros(cuenta: CuentaStripe?, cargada: Boolean): EstadoCobros = when {
        !cargada -> EstadoCobros.DESCONOCIDO
        cuenta == null -> EstadoCobros.SIN_CUENTA
        else -> when (cuenta.estado) {
            EstadoCuentaStripe.ACTIVA -> EstadoCobros.ACTIVA
            EstadoCuentaStripe.PENDIENTE -> EstadoCobros.PENDIENTE
            EstadoCuentaStripe.RESTRINGIDA -> EstadoCobros.RESTRINGIDA
            EstadoCuentaStripe.DESCONOCIDO -> EstadoCobros.DESCONOCIDO
        }
    }

    /** Los pagos de un chat que se enseñan arriba: los que siguen en marcha. [ocultos]: los ya cerrados. */
    data class Franja(val visibles: List<Pago>, val ocultos: Int)

    fun franjaChat(pagos: List<Pago>): Franja {
        val (finales, enMarcha) = pagos.partition { it.estado.esFinal }
        return Franja(Pago.ordenar(enMarcha), finales.size)
    }

    /** El aviso de retención se pone junto al botón de pagar, y solo con él. */
    fun avisoRetencion(acciones: Set<AccionPago>): Boolean = acciones.any { it.abreHojaDePago }

    /** En pendiente_pago, «aceptar» es continuar un pago ya empezado. */
    fun esReanudar(pago: Pago, accion: AccionPago): Boolean =
        accion == AccionPago.ACEPTAR && pago.estado == EstadoPago.PENDIENTE_PAGO

    /** Cancelar, terminar y confirmar piden confirmación; disputar, además, un motivo. */
    fun pideConfirmacion(accion: AccionPago): Boolean =
        accion == AccionPago.CANCELAR || accion == AccionPago.TERMINAR || accion == AccionPago.CONFIRMAR

    /** Importe para el cliente; importe, comisión y neto para el profesional. */
    fun desglose(pago: Pago, papel: PapelPago?): List<LineaDesglose> = when (papel) {
        PapelPago.PROFESIONAL -> listOf(
            LineaDesglose(ConceptoPago.IMPORTE, pago.importeCent),
            LineaDesglose(ConceptoPago.COMISION, pago.comisionCent),
            LineaDesglose(ConceptoPago.NETO, pago.netoCent)
        )
        else -> listOf(LineaDesglose(ConceptoPago.IMPORTE, pago.importeCent))
    }

    /** La fecha de liberación automática solo tiene sentido con el trabajo marcado como terminado. */
    fun liberacionPrevista(pago: Pago): Long? =
        pago.liberarEn?.takeIf { pago.estado == EstadoPago.TERMINADO && it > 0 }

    /**
     * Qué contar de [flujo]. «Retenido» solo sale con [FlujoPago.Resuelto] (el
     * snapshot con lo que decidió el servidor): tras Completed de Stripe, el
     * flujo está en Procesando y es [AvisoFlujo.EnMarcha].
     */
    fun avisoFlujo(flujo: FlujoPago): AvisoFlujo = when (flujo) {
        FlujoPago.Inactivo -> AvisoFlujo.Ninguno
        is FlujoPago.Preparando, is FlujoPago.Listo, is FlujoPago.Presentado, is FlujoPago.Procesando -> AvisoFlujo.EnMarcha
        is FlujoPago.Confirmando -> AvisoFlujo.Confirmando
        is FlujoPago.Cancelado -> AvisoFlujo.HojaCerrada
        is FlujoPago.Fallido -> flujo.error?.let { AvisoFlujo.Error(it) } ?: AvisoFlujo.ErrorHoja
        is FlujoPago.Resuelto -> when (flujo.estado) {
            EstadoPago.RETENIDO -> AvisoFlujo.Retenido
            EstadoPago.FALLIDO -> AvisoFlujo.CobroFallido
            // Caducó el cobro (lo cancela el sistema) o lo canceló la otra parte
            EstadoPago.CANCELADO -> if (flujo.actor == "sistema") AvisoFlujo.Caducado else AvisoFlujo.PagoCancelado
            else -> AvisoFlujo.Ninguno
        }
    }

    /** Un aviso que ya es el final del flujo (hay que llamar a `flujoVisto` al enseñarlo). */
    fun esFinalDeFlujo(flujo: FlujoPago): Boolean =
        flujo is FlujoPago.Cancelado || flujo is FlujoPago.Fallido || flujo is FlujoPago.Resuelto || flujo is FlujoPago.Confirmando

    /** Pagos que el administrador tiene que resolver. */
    fun disputas(pagos: Collection<Pago>): List<Pago> = Pago.ordenar(pagos.filter { it.estado == EstadoPago.EN_DISPUTA })
}

/** Decisión del administrador sobre un pago en disputa; [id] es el que espera `pagos_resolver`. */
enum class DecisionDisputa(val id: String) {
    LIBERAR("liberar"),
    REEMBOLSAR("reembolsar")
}
