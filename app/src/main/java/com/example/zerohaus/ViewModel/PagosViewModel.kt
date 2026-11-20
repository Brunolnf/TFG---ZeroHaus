package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zerohaus.Modelos.AccionPago
import com.example.zerohaus.Modelos.AccionesPago
import com.example.zerohaus.Modelos.CuentaStripe
import com.example.zerohaus.Modelos.DatosPaymentSheet
import com.example.zerohaus.Modelos.ErrorPago
import com.example.zerohaus.Modelos.EstadoPago
import com.example.zerohaus.Modelos.Pago
import com.example.zerohaus.Modelos.PapelPago
import com.example.zerohaus.Modelos.ResultadoHojaPago
import com.example.zerohaus.Modelos.ResultadoPago
import com.example.zerohaus.Repositorios.RepositorioPagos
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Flujo de pago con PaymentSheet, del lado del cliente:
 *
 * Preparando (llamando a aceptar/reintentar) → Listo (la UI presenta la hoja)
 * → Presentado → Procesando (Stripe dijo Completed) → Resuelto (el snapshot
 * de `/pagos` ya trae lo que decidió el webhook).
 * Salidas: Cancelado (se cerró la hoja; el pago sigue en pendiente_pago) y
 * Fallido (error del servidor o de Stripe).
 *
 * [historialBase] es el tamaño del historial antes de entrar en pendiente_pago:
 * aceptar o reintentar le añade una entrada (pendiente_pago) y el webhook otra.
 * Hasta ver las dos no se da nada por resuelto, para que un snapshot atrasado
 * (el «fallido» de antes de reintentar) no se tome por el resultado. Al
 * reanudar un pago ya en pendiente_pago, esa entrada ya está: base = tamaño − 1.
 */
sealed interface FlujoPago {
    data object Inactivo : FlujoPago
    data class Preparando(val pagoId: String, val accion: AccionPago, val historialBase: Int) : FlujoPago
    data class Listo(val pagoId: String, val datos: DatosPaymentSheet, val historialBase: Int) : FlujoPago
    data class Presentado(val pagoId: String, val historialBase: Int) : FlujoPago
    /** [desdeMs]: cuándo dijo Stripe Completed, para no esperar al webhook sin límite. */
    data class Procesando(val pagoId: String, val historialBase: Int, val desdeMs: Long = 0L) : FlujoPago
    /** Pasó el límite de espera sin que el servidor confirmara: ya no bloquea; el estado llega por los listeners. */
    data class Confirmando(val pagoId: String, val historialBase: Int) : FlujoPago
    data class Cancelado(val pagoId: String) : FlujoPago
    /** [error] si falló la llamada al servidor; [mensajeStripe] si falló PaymentSheet (solo para el registro). */
    data class Fallido(val pagoId: String, val error: ErrorPago?, val mensajeStripe: String?) : FlujoPago
    /** [actor]: quién hizo el último cambio (`sistema` si caducó el cobro). */
    data class Resuelto(val pagoId: String, val estado: EstadoPago, val actor: String = "") : FlujoPago
}

/** Transiciones del [FlujoPago], puras. Lo que no encaja (un evento atrasado o repetido) deja el flujo como está. */
object FlujosPago {

    /** Máximo que se espera al webhook tras Completed. */
    const val PROCESANDO_MAX_MS = 60_000L

    /** Estados en que el pago sigue en marcha: no se empieza otro ni se ofrecen acciones. */
    fun enCurso(f: FlujoPago): Boolean =
        f is FlujoPago.Preparando || f is FlujoPago.Listo || f is FlujoPago.Presentado || f is FlujoPago.Procesando

    fun iniciar(actual: FlujoPago, pago: Pago, accion: AccionPago): FlujoPago {
        if (!accion.abreHojaDePago || enCurso(actual)) return actual
        val yaPendiente = pago.estado == EstadoPago.PENDIENTE_PAGO
        return FlujoPago.Preparando(pago.id, accion, (pago.historial.size - if (yaPendiente) 1 else 0).coerceAtLeast(0))
    }

    fun datosRecibidos(actual: FlujoPago, pagoId: String, datos: DatosPaymentSheet): FlujoPago =
        if (actual is FlujoPago.Preparando && actual.pagoId == pagoId) FlujoPago.Listo(pagoId, datos, actual.historialBase)
        else actual

    fun errorAlPreparar(actual: FlujoPago, pagoId: String, error: ErrorPago): FlujoPago =
        if (actual is FlujoPago.Preparando && actual.pagoId == pagoId) FlujoPago.Fallido(pagoId, error, null)
        else actual

    fun hojaPresentada(actual: FlujoPago): FlujoPago =
        if (actual is FlujoPago.Listo) FlujoPago.Presentado(actual.pagoId, actual.historialBase) else actual

    /** Completed no marca nada como pagado: pasa a esperar el snapshot. */
    fun resultadoHoja(actual: FlujoPago, r: ResultadoHojaPago, ahoraMs: Long = 0L): FlujoPago {
        val (pagoId, base) = when (actual) {
            is FlujoPago.Listo -> actual.pagoId to actual.historialBase
            is FlujoPago.Presentado -> actual.pagoId to actual.historialBase
            else -> return actual
        }
        return when (r) {
            ResultadoHojaPago.Completado -> FlujoPago.Procesando(pagoId, base, ahoraMs)
            ResultadoHojaPago.Cancelado -> FlujoPago.Cancelado(pagoId)
            is ResultadoHojaPago.Fallido -> FlujoPago.Fallido(pagoId, null, r.mensaje)
        }
    }

    /** Pasado el límite sin snapshot que lo resuelva, Procesando deja de bloquear (pasa a Confirmando). */
    fun vencido(actual: FlujoPago, ahoraMs: Long, limiteMs: Long = PROCESANDO_MAX_MS): FlujoPago =
        if (actual is FlujoPago.Procesando && ahoraMs - actual.desdeMs >= limiteMs) {
            FlujoPago.Confirmando(actual.pagoId, actual.historialBase)
        } else actual

    /**
     * Snapshot de `/pagos/{id}`. Con la hoja abierta o tras Completed, se
     * resuelve cuando el servidor ya ha anotado el cobro y su resultado y el
     * pago ha salido de solicitado/pendiente_pago (retenido, fallido, o
     * cancelado porque el cobro caducó).
     */
    fun snapshot(actual: FlujoPago, pago: Pago): FlujoPago {
        val (pagoId, base) = when (actual) {
            is FlujoPago.Presentado -> actual.pagoId to actual.historialBase
            is FlujoPago.Procesando -> actual.pagoId to actual.historialBase
            is FlujoPago.Confirmando -> actual.pagoId to actual.historialBase
            else -> return actual
        }
        if (pago.id != pagoId) return actual
        val esperando = pago.estado == EstadoPago.SOLICITADO || pago.estado == EstadoPago.PENDIENTE_PAGO
        return if (!esperando && pago.historial.size >= base + 2) {
            FlujoPago.Resuelto(pagoId, pago.estado, pago.historial.lastOrNull()?.actor.orEmpty())
        } else actual
    }
}

/** Acción pedida al servidor y aún sin respuesta (para desactivar botones). */
data class AccionEnCurso(val pagoId: String, val accion: AccionPago)

/**
 * Estado de las pantallas de pagos (T-E8). Los errores van como [ErrorPago]:
 * el texto, en los 14 idiomas, lo elige la pantalla.
 */
data class PagosEstado(
    /** false: T-E8 oculta los pagos (interruptor de Remote Config o build sin clave de test). */
    val disponibles: Boolean = false,
    val miUid: String = "",
    val comoCliente: List<Pago> = emptyList(),
    val comoProfesional: List<Pago> = emptyList(),
    val cargandoListas: Boolean = false,
    val errorListas: ErrorPago? = null,
    val chatId: String? = null,
    val pagosChat: List<Pago> = emptyList(),
    val cargandoChat: Boolean = false,
    val errorChat: ErrorPago? = null,
    val detalle: Pago? = null,
    val cargandoDetalle: Boolean = false,
    val errorDetalle: ErrorPago? = null,
    /** null con [cuentaCargada]: el profesional aún no ha empezado el alta en Stripe. */
    val cuentaStripe: CuentaStripe? = null,
    val cuentaCargada: Boolean = false,
    val errorCuenta: ErrorPago? = null,
    /** URL de alta en Stripe para abrir fuera de la app; tras abrirla, [PagosViewModel.enlaceAbierto]. */
    val enlaceCuenta: String? = null,
    val pidiendoEnlace: Boolean = false,
    val accionEnCurso: AccionEnCurso? = null,
    val solicitando: Boolean = false,
    /** Id del pago recién solicitado; tras usarlo, [PagosViewModel.solicitudVista]. */
    val pagoSolicitado: String? = null,
    /** Último error de una acción; tras mostrarlo, [PagosViewModel.errorVisto]. */
    val error: ErrorPago? = null,
    val flujoPago: FlujoPago = FlujoPago.Inactivo,
    /** Tengo algún pago (como cliente o profesional): decide si Ajustes enseña «Pagos» con los pagos apagados. */
    val tengoPagos: Boolean = false
) {
    /** Todos mis pagos, el más reciente primero. */
    val todos: List<Pago> get() = Pago.ordenar(comoCliente + comoProfesional)

    val ocupado: Boolean get() = accionEnCurso != null || FlujosPago.enCurso(flujoPago)

    /** El profesional solo puede pedir pagos con la cuenta de Stripe activa. */
    val puedeSolicitar: Boolean get() = disponibles && cuentaStripe?.activa == true

    fun papel(pago: Pago): PapelPago? = AccionesPago.papel(pago, miUid)

    /**
     * Botones que puedo ver en [pago] según mi papel y su estado (la tabla del
     * servidor). Sin pagos disponibles no se ofrece pagar: no se podría abrir la hoja.
     */
    fun acciones(pago: Pago): Set<AccionPago> =
        AccionesPago.disponibles(pago, miUid).filterTo(LinkedHashSet()) { disponibles || !it.abreHojaDePago }
}

/**
 * Pagos con retención: mis listas, los de un chat, el detalle, mi cuenta de
 * Stripe, las acciones y el pago con PaymentSheet (ver [FlujoPago] y
 * `Util/PasarelaStripe.kt`).
 *
 * Nunca cambia un pago en local: el estado solo cambia cuando llega el
 * snapshot de `/pagos` con lo que ha escrito el servidor.
 */
class PagosViewModel(
    private val repo: RepositorioPagos = RepositorioPagos(),
    /** Reloj y límite inyectables para probar la espera de Procesando. */
    private val reloj: () -> Long = System::currentTimeMillis,
    private val limiteProcesandoMs: Long = FlujosPago.PROCESANDO_MAX_MS
) : ViewModel() {

    var estado by mutableStateOf(PagosEstado())
        private set

    private var jobCliente: Job? = null
    private var jobProfesional: Job? = null
    private var jobEnlace: Job? = null
    private var jobLimite: Job? = null
    private var errorCliente: ErrorPago? = null
    private var errorProfesional: ErrorPago? = null
    private var cargandoCliente = false
    private var cargandoProfesional = false
    private var jobChat: Job? = null
    private var jobDetalle: Job? = null
    private var jobCuenta: Job? = null
    private var jobSeguimiento: Job? = null

    /** Relee el interruptor y la sesión (Remote Config puede haberse activado después). */
    fun refrescarDisponibilidad() {
        estado = estado.copy(disponibles = repo.pagosDisponibles(), miUid = repo.miUid())
    }

    /** Mis pagos como cliente y como profesional, en tiempo real. Vuelve a suscribir la consulta que haya muerto. */
    fun cargar() {
        refrescarDisponibilidad()
        val clienteVivo = jobCliente?.isActive == true
        val profesionalVivo = jobProfesional?.isActive == true
        if (clienteVivo && profesionalVivo) return
        fun publicar(cambio: PagosEstado.() -> PagosEstado) {
            estado = estado.cambio().copy(
                cargandoListas = cargandoCliente || cargandoProfesional,
                errorListas = errorCliente ?: errorProfesional
            )
        }
        if (!clienteVivo) {
            errorCliente = null
            cargandoCliente = true
            jobCliente = viewModelScope.launch {
                repo.misPagosComoCliente().collect { r ->
                    errorCliente = (r as? ResultadoPago.Error)?.error
                    cargandoCliente = false
                    publicar { if (r is ResultadoPago.Ok) copy(comoCliente = r.valor) else this }
                }
                cargandoCliente = false
            }
        }
        if (!profesionalVivo) {
            errorProfesional = null
            cargandoProfesional = true
            jobProfesional = viewModelScope.launch {
                repo.misPagosComoProfesional().collect { r ->
                    errorProfesional = (r as? ResultadoPago.Error)?.error
                    cargandoProfesional = false
                    publicar { if (r is ResultadoPago.Ok) copy(comoProfesional = r.valor) else this }
                }
                cargandoProfesional = false
            }
        }
        publicar { this }
    }

    /** ¿Tengo algún pago? Una lectura por papel, sin listeners (para la fila «Pagos» de Ajustes). */
    fun comprobarTengoPagos() {
        viewModelScope.launch {
            if (repo.tengoPagos()) estado = estado.copy(tengoPagos = true)
        }
    }

    /** Pagos de un chat (soy cliente o profesional en él). */
    fun cargarChat(chatId: String) {
        refrescarDisponibilidad()
        if (estado.chatId == chatId && jobChat?.isActive == true) return
        jobChat?.cancel()
        estado = estado.copy(chatId = chatId, pagosChat = emptyList(), cargandoChat = true, errorChat = null)
        jobChat = viewModelScope.launch {
            repo.pagosDelChat(chatId).collect { r ->
                estado = when (r) {
                    is ResultadoPago.Ok -> estado.copy(pagosChat = r.valor, cargandoChat = false, errorChat = null)
                    is ResultadoPago.Error -> estado.copy(cargandoChat = false, errorChat = r.error)
                }
            }
        }
    }

    fun abrirDetalle(pagoId: String) {
        refrescarDisponibilidad()
        if (estado.detalle?.id == pagoId && jobDetalle?.isActive == true) return
        jobDetalle?.cancel()
        estado = estado.copy(detalle = null, cargandoDetalle = true, errorDetalle = null)
        jobDetalle = viewModelScope.launch {
            repo.pago(pagoId).collect { r ->
                estado = when (r) {
                    is ResultadoPago.Ok -> estado.copy(
                        detalle = r.valor,
                        cargandoDetalle = false,
                        errorDetalle = if (r.valor == null) ErrorPago.PAGO_NO_ENCONTRADO else null
                    )
                    is ResultadoPago.Error -> estado.copy(cargandoDetalle = false, errorDetalle = r.error)
                }
            }
        }
    }

    fun cerrarDetalle() {
        jobDetalle?.cancel()
        jobDetalle = null
        estado = estado.copy(detalle = null, cargandoDetalle = false, errorDetalle = null)
    }

    /** Mi cuenta de Stripe Connect, en tiempo real (la copia el webhook). */
    fun cargarCuenta() {
        refrescarDisponibilidad()
        if (jobCuenta?.isActive == true) return
        jobCuenta = viewModelScope.launch {
            repo.miCuentaStripe().collect { r ->
                estado = when (r) {
                    is ResultadoPago.Ok -> estado.copy(cuentaStripe = r.valor, cuentaCargada = true, errorCuenta = null)
                    is ResultadoPago.Error -> estado.copy(cuentaCargada = true, errorCuenta = r.error)
                }
            }
        }
    }

    // ══ Cuenta de Stripe ══

    fun crearEnlaceCuenta() {
        if (estado.pidiendoEnlace) return
        estado = estado.copy(pidiendoEnlace = true, error = null)
        jobEnlace = viewModelScope.launch {
            estado = when (val r = repo.crearEnlaceCuenta()) {
                is ResultadoPago.Ok -> estado.copy(pidiendoEnlace = false, enlaceCuenta = r.valor)
                is ResultadoPago.Error -> estado.copy(pidiendoEnlace = false, error = r.error)
            }
        }
    }

    fun enlaceAbierto() {
        estado = estado.copy(enlaceCuenta = null)
    }

    /** La pantalla se fue mientras se pedía el enlace: no se abre el navegador más tarde. */
    fun descartarEnlace() {
        jobEnlace?.cancel()
        jobEnlace = null
        estado = estado.copy(pidiendoEnlace = false, enlaceCuenta = null)
    }

    /** Al volver del alta: pregunta a Stripe sin esperar al webhook. */
    fun refrescarCuenta() {
        viewModelScope.launch {
            when (val r = repo.estadoCuenta()) {
                is ResultadoPago.Ok -> estado = estado.copy(
                    cuentaStripe = r.valor.copy(accountId = estado.cuentaStripe?.accountId.orEmpty()),
                    cuentaCargada = true
                )
                // sin_cuenta: aún no ha empezado el alta; es «sin configurar», no un fallo
                is ResultadoPago.Error -> estado = if (r.motivo == "sin_cuenta") estado.copy(cuentaCargada = true)
                else estado.copy(error = r.error)
            }
        }
    }

    // ══ Acciones ══

    /** El profesional pide un pago en el chat. [importeCent]: ver FormatoPago.centimosDesdeTexto. */
    fun solicitar(chatId: String, importeCent: Long, descripcion: String) {
        if (estado.solicitando) return
        estado = estado.copy(solicitando = true, error = null)
        viewModelScope.launch {
            estado = when (val r = repo.solicitar(chatId, importeCent, descripcion)) {
                is ResultadoPago.Ok -> estado.copy(solicitando = false, pagoSolicitado = r.valor)
                is ResultadoPago.Error -> estado.copy(solicitando = false, error = r.error)
            }
        }
    }

    fun solicitudVista() {
        estado = estado.copy(pagoSolicitado = null)
    }

    /**
     * El cliente paga: acepta un pago solicitado, reanuda uno pendiente (tras
     * cerrar la hoja) o reintenta uno fallido. Al
     * llegar los datos, [PagosEstado.flujoPago] pasa a [FlujoPago.Listo] y la
     * pantalla presenta la hoja con `rememberHojaDePago`.
     */
    fun pagar(pago: Pago) {
        val accion = when (pago.estado) {
            EstadoPago.SOLICITADO, EstadoPago.PENDIENTE_PAGO -> AccionPago.ACEPTAR
            EstadoPago.FALLIDO -> AccionPago.REINTENTAR
            else -> return
        }
        if (accion !in estado.acciones(pago) || estado.accionEnCurso != null) return
        val flujo = FlujosPago.iniciar(estado.flujoPago, pago, accion)
        if (flujo !is FlujoPago.Preparando) return
        estado = estado.copy(flujoPago = flujo, error = null)
        seguir(pago.id)
        viewModelScope.launch {
            val r = if (accion == AccionPago.ACEPTAR) repo.aceptar(pago.id) else repo.reintentar(pago.id)
            estado = estado.copy(
                flujoPago = when (r) {
                    is ResultadoPago.Ok -> FlujosPago.datosRecibidos(estado.flujoPago, pago.id, r.valor)
                    is ResultadoPago.Error -> FlujosPago.errorAlPreparar(estado.flujoPago, pago.id, r.error)
                }
            )
        }
    }

    /** La pantalla lo llama justo antes de presentar la hoja, para no presentarla dos veces. */
    fun hojaPresentada() {
        estado = estado.copy(flujoPago = FlujosPago.hojaPresentada(estado.flujoPago))
    }

    /** Resultado de PaymentSheet (callback de `rememberHojaDePago`). */
    fun resultadoHoja(r: ResultadoHojaPago) {
        val flujo = FlujosPago.resultadoHoja(estado.flujoPago, r, reloj())
        estado = estado.copy(flujoPago = flujo)
        if (flujo is FlujoPago.Procesando) {
            // Sin snapshot que lo resuelva en el plazo, deja de bloquear (el estado sigue llegando por los listeners)
            jobLimite?.cancel()
            jobLimite = viewModelScope.launch {
                delay(limiteProcesandoMs)
                estado = estado.copy(flujoPago = FlujosPago.vencido(estado.flujoPago, reloj(), limiteProcesandoMs))
            }
        }
    }

    /** La pantalla ya ha mostrado el final del flujo (o se ha ido): vuelve a Inactivo. */
    fun flujoVisto() {
        jobLimite?.cancel()
        jobLimite = null
        jobSeguimiento?.cancel()
        jobSeguimiento = null
        estado = estado.copy(flujoPago = FlujoPago.Inactivo)
    }

    /** Snapshot del pago en curso, para saber cuándo el webhook ha decidido. */
    private fun seguir(pagoId: String) {
        jobSeguimiento?.cancel()
        jobSeguimiento = viewModelScope.launch {
            repo.pago(pagoId).collect { r ->
                val pago = (r as? ResultadoPago.Ok)?.valor ?: return@collect
                estado = estado.copy(flujoPago = FlujosPago.snapshot(estado.flujoPago, pago))
            }
        }
    }

    fun cancelar(pago: Pago) = ejecutar(pago, AccionPago.CANCELAR) { repo.cancelar(pago.id) }
    fun terminar(pago: Pago) = ejecutar(pago, AccionPago.TERMINAR) { repo.terminar(pago.id) }
    fun confirmar(pago: Pago) = ejecutar(pago, AccionPago.CONFIRMAR) { repo.confirmar(pago.id) }
    fun disputar(pago: Pago, motivo: String) = ejecutar(pago, AccionPago.DISPUTAR) { repo.disputar(pago.id, motivo) }

    /**
     * Pide [accion] al servidor si la tabla me la deja hacer. Si va bien no se
     * toca nada: el estado nuevo llega con el snapshot.
     */
    private fun ejecutar(pago: Pago, accion: AccionPago, llamada: suspend () -> ResultadoPago<EstadoPago>) {
        if (estado.ocupado || accion !in estado.acciones(pago)) return
        estado = estado.copy(accionEnCurso = AccionEnCurso(pago.id, accion), error = null)
        viewModelScope.launch {
            val r = llamada()
            estado = estado.copy(accionEnCurso = null, error = (r as? ResultadoPago.Error)?.error)
        }
    }

    fun errorVisto() {
        estado = estado.copy(error = null)
    }
}
