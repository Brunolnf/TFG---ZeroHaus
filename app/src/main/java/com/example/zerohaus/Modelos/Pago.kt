package com.example.zerohaus.Modelos

import java.math.BigDecimal
import java.net.URI
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/*
 * Pago con retención (bloque E). Todo lo de aquí es puro (sin Firebase ni
 * Android) para poder probarlo en la JVM.
 *
 * El servidor es la única fuente: calcula importe, comisión y neto, y es el
 * único que cambia el estado (las reglas deniegan escribir en /pagos y en
 * /cuentas_stripe). La app solo muestra lo que lee y llama a las funciones.
 */

/** Estado de `/pagos/{id}`; [id] es el valor que guarda el servidor. */
enum class EstadoPago(val id: String) {
    SOLICITADO("solicitado"),
    PENDIENTE_PAGO("pendiente_pago"),
    RETENIDO("retenido"),
    TERMINADO("terminado"),
    LIBERADO("liberado"),
    CANCELADO("cancelado"),
    EN_DISPUTA("en_disputa"),
    REEMBOLSADO("reembolsado"),
    CONTRACARGO("contracargo"),
    FALLIDO("fallido"),

    /** Un estado que esta versión de la app no conoce: se muestra sin acciones. */
    DESCONOCIDO("");

    /** De aquí ya no sale (salvo un contracargo tras liberar, que llega del banco). */
    val esFinal: Boolean get() = this == LIBERADO || this == CANCELADO || this == REEMBOLSADO || this == CONTRACARGO

    companion object {
        fun desde(id: String?): EstadoPago = entries.firstOrNull { it != DESCONOCIDO && it.id == id } ?: DESCONOCIDO
    }
}

/** Papel del usuario actual en un pago. */
enum class PapelPago { CLIENTE, PROFESIONAL }

/**
 * Lo que un usuario puede pedir al servidor sobre un pago. [evento] es el de la
 * tabla de transiciones del servidor; [funcion], la Cloud Function que lo hace.
 */
enum class AccionPago(val evento: String, val funcion: String) {
    ACEPTAR("aceptar", "pagos_aceptar"),
    REINTENTAR("reintentar", "pagos_reintentar"),
    CANCELAR("cancelar", "pagos_cancelar"),
    TERMINAR("terminar", "pagos_terminar"),
    CONFIRMAR("confirmar", "pagos_confirmar"),
    DISPUTAR("disputar", "pagos_disputar");

    /** Aceptar y reintentar devuelven los datos de PaymentSheet; el resto, el estado nuevo. */
    val abreHojaDePago: Boolean get() = this == ACEPTAR || this == REINTENTAR
}

/**
 * Acciones que se ofrecen según el papel y el estado: la parte de cliente y de
 * profesional de `TRANSICIONES` en `functions/pagos.py` (las de sistema y de
 * administrador no las hace la app), más [REANUDAR]. Si el servidor cambia su
 * tabla, cambia esta; `AccionesPagoTest` las compara.
 */
object AccionesPago {
    // Antes que TABLA: las propiedades se inicializan en orden
    /**
     * No es una transición: en pendiente_pago con el cobro ya creado,
     * `pagos_aceptar` devuelve el mismo cobro sin crear otro. Sirve para volver
     * a abrir la hoja si el cliente la cerró (si no, solo podría cancelar).
     */
    val REANUDAR: Map<Pair<EstadoPago, AccionPago>, Set<PapelPago>> =
        mapOf((EstadoPago.PENDIENTE_PAGO to AccionPago.ACEPTAR) to setOf(PapelPago.CLIENTE))

    private val TABLA: Map<Pair<EstadoPago, AccionPago>, Set<PapelPago>> = mapOf(
        (EstadoPago.SOLICITADO to AccionPago.ACEPTAR) to setOf(PapelPago.CLIENTE),
        (EstadoPago.SOLICITADO to AccionPago.CANCELAR) to setOf(PapelPago.CLIENTE, PapelPago.PROFESIONAL),
        (EstadoPago.PENDIENTE_PAGO to AccionPago.CANCELAR) to setOf(PapelPago.CLIENTE, PapelPago.PROFESIONAL),
        (EstadoPago.FALLIDO to AccionPago.REINTENTAR) to setOf(PapelPago.CLIENTE),
        (EstadoPago.FALLIDO to AccionPago.CANCELAR) to setOf(PapelPago.CLIENTE, PapelPago.PROFESIONAL),
        // Con el dinero retenido ya no se cancela: o se termina o se disputa
        (EstadoPago.RETENIDO to AccionPago.TERMINAR) to setOf(PapelPago.PROFESIONAL),
        (EstadoPago.RETENIDO to AccionPago.DISPUTAR) to setOf(PapelPago.CLIENTE),
        (EstadoPago.TERMINADO to AccionPago.CONFIRMAR) to setOf(PapelPago.CLIENTE),
        (EstadoPago.TERMINADO to AccionPago.DISPUTAR) to setOf(PapelPago.CLIENTE),
    ) + REANUDAR

    /** Acciones de [papel] en [estado], en el orden del enum. Sin papel (no soy parte), ninguna. */
    fun disponibles(papel: PapelPago?, estado: EstadoPago): Set<AccionPago> {
        if (papel == null) return emptySet()
        return AccionPago.entries.filter { papel in TABLA[estado to it].orEmpty() }.toSet()
    }

    /** Mi papel en [pago], o null si no soy parte (o si el pago tuviera la misma cuenta en los dos lados). */
    fun papel(pago: Pago, miUid: String): PapelPago? {
        if (miUid.isBlank()) return null
        val soyCliente = pago.cliente == miUid
        val soyProfesional = pago.profesional == miUid
        return when {
            soyCliente && !soyProfesional -> PapelPago.CLIENTE
            soyProfesional && !soyCliente -> PapelPago.PROFESIONAL
            else -> null
        }
    }

    fun disponibles(pago: Pago, miUid: String): Set<AccionPago> = disponibles(papel(pago, miUid), pago.estado)
}

/** Entrada de `historial`: cada cambio de estado, con quién lo hizo. */
data class EntradaHistorialPago(
    val estado: EstadoPago,
    val evento: String,
    val actor: String,
    val uid: String?,
    val fecha: Long
)

data class DisputaPago(val motivo: String, val fecha: Long)

/** Reintentos de la liberación automática que han fallado; [proximoIntento] en ms, si lo hay. */
data class LiberacionPago(val fallos: Int, val proximoIntento: Long?)

/** Cómo acabó la reclamación del banco. */
enum class ResultadoContracargo(val id: String) {
    GANADO("won"),
    PERDIDO("lost"),
    DESCONOCIDO("");

    companion object {
        fun desde(id: String?): ResultadoContracargo =
            entries.firstOrNull { it != DESCONOCIDO && it.id == id } ?: DESCONOCIDO
    }
}

/** Reclamación (contracargo) abierta por el banco sobre el pago. */
data class ContracargoPago(val resultado: ResultadoContracargo, val fecha: Long)

/**
 * Documento de `/pagos/{pagoId}` tal como lo escribe el servidor. Los importes
 * van en céntimos y la app no hace ninguna cuenta con ellos: solo los muestra
 * con [FormatoPago.importe].
 */
data class Pago(
    val id: String,
    val cliente: String,
    val profesional: String,
    val chatId: String,
    val descripcion: String,
    val importeCent: Long,
    val comisionCent: Long,
    val netoCent: Long,
    val moneda: String,
    val comisionPct: Double,
    val liberacionDias: Int,
    val estado: EstadoPago,
    val historial: List<EntradaHistorialPago> = emptyList(),
    val creado: Long = 0L,
    val actualizado: Long = 0L,
    val stripePaymentIntentId: String? = null,
    val stripeTransferId: String? = null,
    val stripeRefundId: String? = null,
    val terminadoEn: Long? = null,
    val liberarEn: Long? = null,
    val disputa: DisputaPago? = null,
    /** El servidor pide que un administrador mire este pago (liberación fallida, contracargo…). */
    val requiereAdmin: Boolean = false,
    val liberacion: LiberacionPago? = null,
    val contracargo: ContracargoPago? = null
) {
    companion object {
        /**
         * Pago a partir de los datos de un documento (`DocumentSnapshot.getData()`),
         * con [id] el del documento. Devuelve null si falta algo sin lo que no se
         * puede mostrar bien (las partes, el chat, el estado o algún importe):
         * mejor no enseñarlo que enseñar «0,00 €».
         */
        fun desdeMapa(id: String, datos: Map<String, Any?>?): Pago? {
            if (datos == null || id.isBlank()) return null
            val cliente = datos.texto("cliente") ?: return null
            val profesional = datos.texto("profesional") ?: return null
            val chatId = datos.texto("chatId") ?: return null
            val estado = (datos["estado"] as? String) ?: return null
            val importe = entero(datos["importeCent"])?.takeIf { it >= 0 } ?: return null
            val comision = entero(datos["comisionCent"])?.takeIf { it >= 0 } ?: return null
            val neto = entero(datos["netoCent"])?.takeIf { it >= 0 } ?: return null
            return Pago(
                id = id,
                cliente = cliente,
                profesional = profesional,
                chatId = chatId,
                descripcion = (datos["descripcion"] as? String).orEmpty(),
                importeCent = importe,
                comisionCent = comision,
                netoCent = neto,
                moneda = (datos["moneda"] as? String)?.takeIf { it.isNotBlank() } ?: FormatoPago.MONEDA,
                comisionPct = (datos["comisionPct"] as? Number)?.toDouble() ?: 0.0,
                liberacionDias = entero(datos["liberacionDias"])?.toInt() ?: 0,
                estado = EstadoPago.desde(estado),
                historial = (datos["historial"] as? List<*>).orEmpty().mapNotNull { e ->
                    val m = e as? Map<*, *> ?: return@mapNotNull null
                    EntradaHistorialPago(
                        estado = EstadoPago.desde(m["estado"] as? String),
                        evento = (m["evento"] as? String).orEmpty(),
                        actor = (m["actor"] as? String).orEmpty(),
                        uid = m["uid"] as? String,
                        fecha = fecha(m["fecha"]) ?: 0L
                    )
                },
                creado = fecha(datos["creado"]) ?: 0L,
                actualizado = fecha(datos["actualizado"]) ?: 0L,
                stripePaymentIntentId = datos.texto("stripePaymentIntentId"),
                stripeTransferId = datos.texto("stripeTransferId"),
                stripeRefundId = datos.texto("stripeRefundId"),
                terminadoEn = fecha(datos["terminadoEn"]),
                liberarEn = fecha(datos["liberarEn"]),
                disputa = (datos["disputa"] as? Map<*, *>)?.let { d ->
                    DisputaPago(motivo = (d["motivo"] as? String).orEmpty(), fecha = fecha(d["fecha"]) ?: 0L)
                },
                requiereAdmin = datos["requiereAdmin"] == true,
                liberacion = (datos["liberacion"] as? Map<*, *>)?.let { l ->
                    LiberacionPago(fallos = entero(l["fallos"])?.toInt() ?: 0, proximoIntento = fecha(l["proximoIntento"]))
                },
                contracargo = (datos["contracargo"] as? Map<*, *>)?.let { c ->
                    ContracargoPago(ResultadoContracargo.desde(c["resultado"] as? String), fecha(c["fecha"]) ?: 0L)
                }
            )
        }

        /** Más reciente primero (las reglas no admiten `orderBy`: se ordena aquí). */
        fun ordenar(pagos: Collection<Pago>): List<Pago> =
            pagos.distinctBy { it.id }.sortedWith(compareByDescending<Pago> { it.creado }.thenBy { it.id })
    }
}

/** Estado de la cuenta de Stripe Connect del profesional (`/cuentas_stripe/{uid}`). */
enum class EstadoCuentaStripe(val id: String) {
    PENDIENTE("pendiente"),
    ACTIVA("activa"),
    RESTRINGIDA("restringida"),
    DESCONOCIDO("");

    companion object {
        fun desde(id: String?): EstadoCuentaStripe =
            entries.firstOrNull { it != DESCONOCIDO && it.id == id } ?: DESCONOCIDO
    }
}

/**
 * Cuenta de Stripe del profesional. [accountId] está vacío cuando el dato viene
 * de `stripe_estado_cuenta` y no del documento.
 */
data class CuentaStripe(
    val accountId: String,
    val estado: EstadoCuentaStripe,
    val chargesEnabled: Boolean,
    val payoutsEnabled: Boolean,
    val detailsSubmitted: Boolean
) {
    /** Puede recibir pagos. Lo decide el servidor (estado «activa»); la app solo lo refleja. */
    val activa: Boolean get() = estado == EstadoCuentaStripe.ACTIVA

    companion object {
        fun desdeMapa(datos: Map<String, Any?>?): CuentaStripe? {
            if (datos == null) return null
            return CuentaStripe(
                accountId = (datos["accountId"] as? String).orEmpty(),
                estado = EstadoCuentaStripe.desde(datos["estado"] as? String),
                chargesEnabled = datos["chargesEnabled"] == true,
                payoutsEnabled = datos["payoutsEnabled"] == true,
                detailsSubmitted = datos["detailsSubmitted"] == true
            )
        }
    }
}

/**
 * Lo que devuelven `pagos_aceptar` y `pagos_reintentar` para abrir PaymentSheet.
 * Son secretos de un solo pago: [toString] no los muestra para que no acaben
 * en un log ni en Crashlytics.
 */
class DatosPaymentSheet(
    val paymentIntentClientSecret: String,
    val customerId: String,
    val ephemeralKeySecret: String
) {
    override fun toString() = "DatosPaymentSheet(customerId=$customerId, secretos=ocultos)"

    override fun equals(other: Any?) = other is DatosPaymentSheet &&
        other.paymentIntentClientSecret == paymentIntentClientSecret &&
        other.customerId == customerId && other.ephemeralKeySecret == ephemeralKeySecret

    override fun hashCode() = paymentIntentClientSecret.hashCode()

    companion object {
        /** null si falta algún campo: con un dato vacío PaymentSheet no abre. */
        fun desdeRespuesta(datos: Map<*, *>?): DatosPaymentSheet? {
            val secreto = (datos?.get("paymentIntentClientSecret") as? String)?.takeIf { it.isNotBlank() } ?: return null
            val cliente = (datos["customerId"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            val clave = (datos["ephemeralKeySecret"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            return DatosPaymentSheet(secreto, cliente, clave)
        }
    }
}

/** Cómo se cerró PaymentSheet. Tras [Completado] el pago NO está pagado hasta que el webhook lo diga. */
sealed interface ResultadoHojaPago {
    data object Completado : ResultadoHojaPago
    data object Cancelado : ResultadoHojaPago
    /** [mensaje] es el de Stripe, para el registro; a la persona se le muestra uno propio. */
    data class Fallido(val mensaje: String?) : ResultadoHojaPago
}

/**
 * Errores de pago que la app distingue. Llegan en `details.motivo` de la
 * `FirebaseFunctionsException`; ver [ErroresPago.clasificar].
 */
enum class ErrorPago {
    /** Interruptor apagado en el servidor (o Stripe sin clave de test), o en la app falta la clave o `pagos_habilitados`. */
    PAGOS_NO_DISPONIBLES,
    IMPORTE_BAJO_MINIMO,
    IMPORTE_SOBRE_MAXIMO,
    IMPORTE_NO_VALIDO,
    DESCRIPCION_NO_VALIDA,
    MOTIVO_NO_VALIDO,
    /** El pago ya no está en el estado en que se pidió la acción (otro dispositivo, el webhook…). */
    TRANSICION_NO_PERMITIDA,
    /** No soy la parte que puede hacerlo (o no soy el profesional del chat). */
    NO_AUTORIZADO,
    /** Hay un bloqueo entre las dos partes. */
    BLOQUEO,
    CHAT_NO_VALIDO,
    /** El cobro (PaymentIntent) ya no sirve; hay que volver a abrir el pago. */
    COBRO_NO_VALIDO,
    /** Ya pasó el plazo para abrir una disputa. */
    PLAZO_VENCIDO,
    /** Una de las partes está borrando su cuenta. */
    CUENTA_EN_BORRADO,
    /** El reembolso falló en Stripe (se reintenta). Lo ve el administrador. */
    REEMBOLSO_FALLIDO,
    /** Stripe revirtió la transferencia al profesional. Lo ve el administrador. */
    TRANSFERENCIA_REVERTIDA,
    /** Un administrador no puede ser parte de un pago. */
    ADMIN_ES_PARTE,
    /** Otra operación sobre el mismo pago no ha terminado: inténtalo en un momento. */
    OPERACION_EN_CURSO,
    /** Stripe no responde: inténtalo más tarde. */
    STRIPE_NO_RESPONDE,
    EMAIL_SIN_VERIFICAR,
    /** El profesional no tiene la cuenta de Stripe activa. */
    CUENTA_STRIPE_NO_ACTIVA,
    PAGO_NO_ENCONTRADO,
    /** Cupo de solicitudes agotado. */
    LIMITE,
    SIN_SESION,
    RED,
    /** El servidor respondió sin los campos esperados. */
    RESPUESTA_NO_VALIDA,
    DESCONOCIDO
}

/** Resultado tipado de una llamada de pago. [motivo] es el `details.motivo` crudo, para el registro. */
sealed interface ResultadoPago<out T> {
    data class Ok<out T>(val valor: T) : ResultadoPago<T>
    data class Error(val error: ErrorPago, val motivo: String? = null) : ResultadoPago<Nothing>
}

object ErroresPago {

    /**
     * `details.motivo` → [ErrorPago]: los códigos de `ErrorPago` de
     * `functions/pagos.py`, los de las callables (`stripe`, `email_sin_verificar`)
     * y los del contrato (`importe_maximo`). Los que no están (`version_no_valida`, `id_no_valido`…) son fallos que no debería provocar
     * la app: salen como DESCONOCIDO y se registran en Crashlytics.
     */
    val MOTIVOS: Map<String, ErrorPago> = mapOf(
        "pagos_deshabilitados" to ErrorPago.PAGOS_NO_DISPONIBLES,
        "stripe_no_configurado" to ErrorPago.PAGOS_NO_DISPONIBLES,
        "importe_bajo_minimo" to ErrorPago.IMPORTE_BAJO_MINIMO,
        "importe_minimo" to ErrorPago.IMPORTE_BAJO_MINIMO,
        "importe_sobre_maximo" to ErrorPago.IMPORTE_SOBRE_MAXIMO,
        "importe_maximo" to ErrorPago.IMPORTE_SOBRE_MAXIMO,
        "importe_no_entero" to ErrorPago.IMPORTE_NO_VALIDO,
        "descripcion_no_valida" to ErrorPago.DESCRIPCION_NO_VALIDA,
        "motivo_no_valido" to ErrorPago.MOTIVO_NO_VALIDO,
        "transicion_no_permitida" to ErrorPago.TRANSICION_NO_PERMITIDA,
        "actor_no_permitido" to ErrorPago.NO_AUTORIZADO,
        "uid_no_autorizado" to ErrorPago.NO_AUTORIZADO,
        "partes_no_validas" to ErrorPago.NO_AUTORIZADO,
        "no_es_profesional" to ErrorPago.NO_AUTORIZADO,
        "bloqueo" to ErrorPago.BLOQUEO,
        "chat_no_valido" to ErrorPago.CHAT_NO_VALIDO,
        "cobro_no_valido" to ErrorPago.COBRO_NO_VALIDO,
        "plazo_vencido" to ErrorPago.PLAZO_VENCIDO,
        "cuenta_en_borrado" to ErrorPago.CUENTA_EN_BORRADO,
        "reembolso_fallido" to ErrorPago.REEMBOLSO_FALLIDO,
        "transferencia_revertida" to ErrorPago.TRANSFERENCIA_REVERTIDA,
        "admin_es_parte" to ErrorPago.ADMIN_ES_PARTE,
        "cuenta_no_activa" to ErrorPago.CUENTA_STRIPE_NO_ACTIVA,
        "sin_cuenta" to ErrorPago.CUENTA_STRIPE_NO_ACTIVA,
        "pago_no_encontrado" to ErrorPago.PAGO_NO_ENCONTRADO,
        "operacion_en_curso" to ErrorPago.OPERACION_EN_CURSO,
        "cobro_en_curso" to ErrorPago.OPERACION_EN_CURSO,
        "limite_solicitudes" to ErrorPago.LIMITE,
        "stripe" to ErrorPago.STRIPE_NO_RESPONDE,
        "email_sin_verificar" to ErrorPago.EMAIL_SIN_VERIFICAR,
    )

    /**
     * Clasifica un fallo de una callable de pago. La red tiene prioridad; luego
     * `details.motivo`; sin motivo conocido, el código de la excepción.
     * Es la parte pura: FirebaseFunctionsException no se puede construir en un
     * test JVM, así que el repositorio le pasa `code.name` y `details`.
     */
    fun clasificar(esFalloDeRed: Boolean, codigo: String?, detalles: Any?): ErrorPago {
        if (esFalloDeRed) return ErrorPago.RED
        MOTIVOS[motivo(detalles)]?.let { return it }
        return when (codigo) {
            "UNAUTHENTICATED" -> ErrorPago.SIN_SESION
            "PERMISSION_DENIED" -> ErrorPago.NO_AUTORIZADO
            "RESOURCE_EXHAUSTED" -> ErrorPago.LIMITE
            "UNAVAILABLE" -> ErrorPago.RED
            else -> ErrorPago.DESCONOCIDO
        }
    }

    /** `details.motivo` si es un texto. */
    fun motivo(detalles: Any?): String? = (detalles as? Map<*, *>)?.get("motivo") as? String
}

/** Clave publicable de Stripe: solo de modo test. Mismo patrón que `app/build.gradle.kts`. */
object ClaveStripe {
    private val PATRON = Regex("pk_test_[A-Za-z0-9]{10,247}")

    fun esValida(clave: String?): Boolean = clave != null && PATRON.matches(clave)
}

/** Los pagos se ofrecen solo con `pagos_habilitados` en Remote Config y una clave de test válida. */
object DisponibilidadPagos {
    fun calcular(clavePublicable: String?, pagosHabilitados: Boolean): Boolean =
        pagosHabilitados && ClaveStripe.esValida(clavePublicable)
}

/**
 * Formato de los importes del servidor. Siempre en es-ES, sin pasar por
 * coma flotante: los céntimos se convierten con BigDecimal.
 */
object FormatoPago {
    const val MONEDA = "eur"
    const val DESCRIPCION_MAX = 500
    const val MOTIVO_DISPUTA_MAX = 500
    private val LOCALE: Locale = Locale.forLanguageTag("es-ES")
    // Espacio sin salto entre la cifra y el símbolo, para que «€» no quede solo en otra línea
    private const val NBSP = ' '
    private val TEXTO_IMPORTE = Regex("[0-9]{1,7}([.,][0-9]{1,2})?")

    /** 1234 → «12,34 €»; 123456 → «1.234,56 €». Otra moneda: su código en mayúsculas. */
    fun importe(cent: Long, moneda: String = MONEDA, locale: Locale = LOCALE): String {
        val cifra = DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(locale))
            .format(BigDecimal.valueOf(cent, 2))
        val simbolo = if (moneda.equals(MONEDA, ignoreCase = true)) "€" else moneda.uppercase(Locale.ROOT)
        return "$cifra$NBSP$simbolo"
    }

    /** 10.0 → «10 %»; 1.13 → «1,13 %» (la comisión que guardó el servidor). */
    fun porcentaje(pct: Double): String =
        DecimalFormat("0.##", DecimalFormatSymbols.getInstance(LOCALE)).format(BigDecimal.valueOf(pct)) + "$NBSP%"

    /**
     * Lo que escribe el profesional al pedir un pago («12», «12,5», «12,34» o
     * «12.34») en céntimos para `pagos_solicitar`; null si no es un importe.
     * No valida mínimo ni máximo: eso lo decide el servidor.
     */
    fun centimosDesdeTexto(texto: String): Long? {
        val t = normalizarDigitos(texto.trim())
        if (!TEXTO_IMPORTE.matches(t)) return null
        return BigDecimal(t.replace(',', '.')).movePointRight(2).longValueExact()
    }

    /**
     * Cualquier dígito Unicode (árabe-índico, devanagari…) pasa a 0-9, y el
     * separador decimal árabe (٫) a punto: el teclado del idioma de la app lo
     * puede escribir así.
     */
    private fun normalizarDigitos(t: String): String = buildString(t.length) {
        for (ch in t) {
            when {
                ch == '\u066B' -> append('.')
                Character.isDigit(ch) -> append(('0' + Character.digit(ch, 10)))
                else -> append(ch)
            }
        }
    }

    /**
     * Céntimos tal como se mandan a `pagos_solicitar`: como Int. El SDK de
     * Functions envía un Long como objeto `Int64Value` (no como número) y el
     * servidor en Python lo rechazaría por no ser un entero. null si no es
     * positivo o no cabe en un Int (muy por encima de cualquier máximo).
     */
    fun centimosParaEnviar(cent: Long): Int? = cent.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
}

/** URL de alta en Stripe Connect: solo `https` de Stripe (se abre fuera de la app). */
object EnlaceStripe {
    fun esValido(url: String?): Boolean {
        val uri = runCatching { URI(url ?: return false) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return uri.scheme == "https" && (host == "stripe.com" || host.endsWith(".stripe.com")) && uri.userInfo == null
    }
}

private fun Map<String, Any?>.texto(campo: String): String? = (this[campo] as? String)?.takeIf { it.isNotBlank() }

/** Entero de Firestore (Long); un Double solo si no tiene decimales. */
private fun entero(v: Any?): Long? = when (v) {
    is Long -> v
    is Int -> v.toLong()
    is Double -> if (v.isFinite() && v % 1.0 == 0.0) v.toLong() else null
    else -> null
}

/** Fecha en ms: el servidor guarda enteros; por si acaso, también un Timestamp de Firestore. */
private fun fecha(v: Any?): Long? = when (v) {
    is com.google.firebase.Timestamp -> v.toDate().time
    is java.util.Date -> v.time
    else -> entero(v)
}
