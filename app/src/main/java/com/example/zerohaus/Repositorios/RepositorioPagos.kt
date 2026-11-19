package com.example.zerohaus.Repositorios

import com.example.zerohaus.BuildConfig
import com.example.zerohaus.Modelos.AccionPago
import com.example.zerohaus.Modelos.CuentaStripe
import com.example.zerohaus.Modelos.DatosPaymentSheet
import com.example.zerohaus.Modelos.DecisionDisputa
import com.example.zerohaus.Modelos.DisponibilidadPagos
import com.example.zerohaus.Modelos.EnlaceStripe
import com.example.zerohaus.Modelos.ErrorPago
import com.example.zerohaus.Modelos.ErroresPago
import com.example.zerohaus.Modelos.EstadoCuentaStripe
import com.example.zerohaus.Modelos.EstadoPago
import com.example.zerohaus.Modelos.FormatoPago
import com.example.zerohaus.Modelos.Pago
import com.example.zerohaus.Modelos.ResultadoPago
import com.example.zerohaus.Util.Diagnostico
import com.example.zerohaus.Util.PasarelaStripe
import com.example.zerohaus.Util.esFalloDeRed
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.remoteconfig.remoteConfig
import com.example.zerohaus.Modelos.hastaElPrimerError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Pago con retención: lecturas de `/pagos` y `/cuentas_stripe` y llamadas a
 * las Cloud Functions de pago (europe-west1).
 *
 * La app nunca escribe en `/pagos` ni en `/cuentas_stripe` (las reglas lo
 * deniegan) ni calcula importes: todo cambio pasa por una callable y llega de
 * vuelta por los snapshots. Las consultas son las que admiten las reglas:
 * `cliente == yo` o `profesional == yo`, con `chatId` opcional y sin `orderBy`.
 */
class RepositorioPagos {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val functions = FirebaseFunctions.getInstance(REGION)

    companion object {
        private const val REGION = "europe-west1"
        private const val PAGOS = "pagos"
        private const val CUENTAS_STRIPE = "cuentas_stripe"

        /** Parámetro de Remote Config; sin descargar, getBoolean da false (pagos apagados). */
        const val PAGOS_HABILITADOS = "pagos_habilitados"
    }

    fun miUid(): String = auth.currentUser?.uid.orEmpty()

    /**
     * Interruptor de la app: `pagos_habilitados` (Remote Config, que descarga
     * ConfigRemota al arrancar) y una clave publicable de test. Con false, T-E8
     * oculta los pagos. El servidor aplica su propio interruptor igualmente.
     */
    fun pagosDisponibles(): Boolean {
        val habilitados = runCatching { Firebase.remoteConfig.getBoolean(PAGOS_HABILITADOS) }.getOrDefault(false)
        return DisponibilidadPagos.calcular(BuildConfig.STRIPE_PUBLISHABLE_KEY, habilitados)
    }

    // ══ Lecturas en tiempo real ══

    /** Pagos en los que soy el cliente, el más reciente primero. */
    fun misPagosComoCliente(): Flow<ResultadoPago<List<Pago>>> = consulta("cliente", chatId = null)

    /** Pagos en los que soy el profesional, el más reciente primero. */
    fun misPagosComoProfesional(): Flow<ResultadoPago<List<Pago>>> = consulta("profesional", chatId = null)

    /** Pagos que el servidor marca con `requiereAdmin` (solo el administrador puede listarlos). Sin `orderBy`. */
    fun pagosQueRequierenAdmin(): Flow<ResultadoPago<List<Pago>>> = listaDeAdmin(db.collection(PAGOS).whereEqualTo("requiereAdmin", true))

    private fun listaDeAdmin(q: Query): Flow<ResultadoPago<List<Pago>>> {
        if (miUid().isBlank()) return flowOf(ResultadoPago.Error(ErrorPago.SIN_SESION))
        return callbackFlow {
            val reg = q.addSnapshotListener { snap, error ->
                if (error != null || snap == null) {
                    trySend(ResultadoPago.Error(errorDeLectura(error)))
                    close()
                    return@addSnapshotListener
                }
                trySend(ResultadoPago.Ok(Pago.ordenar(snap.documents.mapNotNull { Pago.desdeMapa(it.id, it.data) })))
            }
            awaitClose { reg.remove() }
        }
    }

    /**
     * Pagos de un chat, sea yo cliente o profesional: las reglas solo admiten
     * consultas filtradas por mi propia parte, así que son dos y se juntan.
     */
    fun pagosDelChat(chatId: String): Flow<ResultadoPago<List<Pago>>> {
        if (chatId.isBlank()) return flowOf(ResultadoPago.Ok(emptyList()))
        return combine(consulta("cliente", chatId), consulta("profesional", chatId)) { a, b ->
            when {
                a is ResultadoPago.Error -> a
                b is ResultadoPago.Error -> b
                else -> ResultadoPago.Ok(Pago.ordenar((a as ResultadoPago.Ok).valor + (b as ResultadoPago.Ok).valor))
            }
        }.hastaElPrimerError()
    }

    /**
     * Pagos en disputa (solo el administrador puede listar `/pagos` entero). El más reciente primero.
     */
    fun pagosEnDisputa(): Flow<ResultadoPago<List<Pago>>> {
        if (miUid().isBlank()) return flowOf(ResultadoPago.Error(ErrorPago.SIN_SESION))
        val q = db.collection(PAGOS).whereEqualTo("estado", EstadoPago.EN_DISPUTA.id)
        return callbackFlow {
            val reg = q.addSnapshotListener { snap, error ->
                if (error != null || snap == null) {
                    trySend(ResultadoPago.Error(errorDeLectura(error)))
                    close() // el listener ya no emite: que quien lo recoge pueda volver a suscribirse
                    return@addSnapshotListener
                }
                trySend(ResultadoPago.Ok(Pago.ordenar(snap.documents.mapNotNull { Pago.desdeMapa(it.id, it.data) })))
            }
            awaitClose { reg.remove() }
        }
    }

    /** Un pago; Ok(null) si no existe o no se puede mostrar. */
    fun pago(pagoId: String): Flow<ResultadoPago<Pago?>> {
        if (miUid().isBlank()) return flowOf(ResultadoPago.Error(ErrorPago.SIN_SESION))
        if (pagoId.isBlank() || '/' in pagoId) return flowOf(ResultadoPago.Ok(null))
        return callbackFlow {
            val reg = db.collection(PAGOS).document(pagoId).addSnapshotListener { snap, error ->
                if (error != null || snap == null) {
                    trySend(ResultadoPago.Error(errorDeLectura(error)))
                    close() // el listener ya no emite: que quien lo recoge pueda volver a suscribirse
                    return@addSnapshotListener
                }
                trySend(ResultadoPago.Ok(if (snap.exists()) Pago.desdeMapa(snap.id, snap.data) else null))
            }
            awaitClose { reg.remove() }
        }
    }

    /** Mi cuenta de Stripe Connect; Ok(null) si aún no la he dado de alta. Sigue al usuario de la sesión. */
    fun miCuentaStripe(): Flow<ResultadoPago<CuentaStripe?>> = porUsuario { uid ->
        callbackFlow {
            val reg = db.collection(CUENTAS_STRIPE).document(uid).addSnapshotListener { snap, error ->
                if (error != null || snap == null) {
                    trySend(ResultadoPago.Error(errorDeLectura(error)))
                    close()
                    return@addSnapshotListener
                }
                trySend(ResultadoPago.Ok(if (snap.exists()) CuentaStripe.desdeMapa(snap.data) else null))
            }
            awaitClose { reg.remove() }
        }
    }

    private fun consulta(campoParte: String, chatId: String?): Flow<ResultadoPago<List<Pago>>> = porUsuario { uid ->
        var q: Query = db.collection(PAGOS).whereEqualTo(campoParte, uid)
        if (chatId != null) q = q.whereEqualTo("chatId", chatId)
        callbackFlow {
            val reg = q.addSnapshotListener { snap, error ->
                if (error != null || snap == null) {
                    trySend(ResultadoPago.Error(errorDeLectura(error)))
                    close()
                    return@addSnapshotListener
                }
                // Un documento que no se puede leer bien no sale (antes que mostrar 0,00 €)
                trySend(ResultadoPago.Ok(Pago.ordenar(snap.documents.mapNotNull { Pago.desdeMapa(it.id, it.data) })))
            }
            awaitClose { reg.remove() }
        }
    }

    /** uid de la sesión; cambia al cerrar sesión o entrar con otra cuenta. */
    private fun uidActual(): Flow<String> = callbackFlow {
        val oyente = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid.orEmpty()) }
        auth.addAuthStateListener(oyente)
        awaitClose { auth.removeAuthStateListener(oyente) }
    }.distinctUntilChanged()

    /**
     * Crea la lectura para el uid de la sesión y, si la sesión cambia, la vuelve
     * a crear con el nuevo (una consulta de otra cuenta daría permiso denegado).
     * Termina tras un error de lectura; sin sesión espera a que haya una.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> porUsuario(crear: (String) -> Flow<ResultadoPago<T>>): Flow<ResultadoPago<T>> =
        uidActual()
            .flatMapLatest { uid -> if (uid.isBlank()) flowOf(ResultadoPago.Error(ErrorPago.SIN_SESION)) else crear(uid) }
            .transformWhile { r ->
                emit(r)
                !(r is ResultadoPago.Error && r.error != ErrorPago.SIN_SESION)
            }

    /** ¿Tengo algún pago como cliente o como profesional? Un `get()` con límite 1 por papel, sin listeners. */
    suspend fun tengoPagos(): Boolean {
        val uid = miUid()
        if (uid.isBlank()) return false
        suspend fun hay(campo: String): Boolean = suspendCancellableCoroutine { cont ->
            db.collection(PAGOS).whereEqualTo(campo, uid).limit(1).get()
                .addOnSuccessListener { cont.resume(!it.isEmpty) }
                .addOnFailureListener { cont.resume(false) }
        }
        return hay("cliente") || hay("profesional")
    }

    private fun errorDeLectura(e: FirebaseFirestoreException?): ErrorPago = when (e?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> ErrorPago.NO_AUTORIZADO
        FirebaseFirestoreException.Code.UNAVAILABLE -> ErrorPago.RED
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> ErrorPago.SIN_SESION
        else -> ErrorPago.DESCONOCIDO
    }

    // ══ Cuenta de Stripe del profesional ══

    /** Enlace de alta (o de completar datos) en Stripe Connect. Solo se devuelve si es https de Stripe. */
    suspend fun crearEnlaceCuenta(): ResultadoPago<String> {
        if (!pagosDisponibles()) return ResultadoPago.Error(ErrorPago.PAGOS_NO_DISPONIBLES)
        return llamar("stripe_crear_enlace_cuenta", emptyMap()) { datos ->
            (datos["url"] as? String)?.takeIf { EnlaceStripe.esValido(it) }
        }
    }

    /**
     * Estado de mi cuenta preguntado a Stripe (al volver del alta, antes de que
     * llegue el webhook). Sin [CuentaStripe.accountId]: ese solo está en el documento.
     */
    suspend fun estadoCuenta(): ResultadoPago<CuentaStripe> =
        llamar("stripe_estado_cuenta", emptyMap()) { datos ->
            val estado = EstadoCuentaStripe.desde(datos["estado"] as? String)
            if (estado == EstadoCuentaStripe.DESCONOCIDO) null
            else CuentaStripe(
                accountId = "",
                estado = estado,
                chargesEnabled = datos["chargesEnabled"] == true,
                payoutsEnabled = datos["payoutsEnabled"] == true,
                detailsSubmitted = datos["detailsSubmitted"] == true
            )
        }

    // ══ Pagos ══

    /**
     * El profesional pide [importeCent] al cliente del chat. El servidor valida
     * el importe (mínimo, máximo) y calcula la comisión. Devuelve el id del pago.
     */
    suspend fun solicitar(chatId: String, importeCent: Long, descripcion: String): ResultadoPago<String> {
        if (!pagosDisponibles()) return ResultadoPago.Error(ErrorPago.PAGOS_NO_DISPONIBLES)
        val cent = FormatoPago.centimosParaEnviar(importeCent) ?: return ResultadoPago.Error(ErrorPago.IMPORTE_NO_VALIDO)
        val texto = descripcion.trim()
        if (texto.isEmpty() || texto.length > FormatoPago.DESCRIPCION_MAX) return ResultadoPago.Error(ErrorPago.DESCRIPCION_NO_VALIDA)
        return llamar("pagos_solicitar", mapOf("chatId" to chatId, "importeCent" to cent, "descripcion" to texto)) { datos ->
            (datos["pagoId"] as? String)?.takeIf { it.isNotBlank() }
        }
    }

    /** El cliente acepta: el servidor crea el cobro y devuelve los datos de PaymentSheet. */
    suspend fun aceptar(pagoId: String): ResultadoPago<DatosPaymentSheet> = hojaDePago(AccionPago.ACEPTAR, pagoId)

    /** Tras un cobro fallido, el cliente vuelve a intentarlo con un cobro nuevo. */
    suspend fun reintentar(pagoId: String): ResultadoPago<DatosPaymentSheet> = hojaDePago(AccionPago.REINTENTAR, pagoId)

    suspend fun cancelar(pagoId: String): ResultadoPago<EstadoPago> = cambioDeEstado(AccionPago.CANCELAR, pagoId)
    suspend fun terminar(pagoId: String): ResultadoPago<EstadoPago> = cambioDeEstado(AccionPago.TERMINAR, pagoId)
    suspend fun confirmar(pagoId: String): ResultadoPago<EstadoPago> = cambioDeEstado(AccionPago.CONFIRMAR, pagoId)

    suspend fun disputar(pagoId: String, motivo: String): ResultadoPago<EstadoPago> {
        val texto = motivo.trim()
        if (texto.isEmpty() || texto.length > FormatoPago.MOTIVO_DISPUTA_MAX) return ResultadoPago.Error(ErrorPago.MOTIVO_NO_VALIDO)
        return cambioDeEstado(AccionPago.DISPUTAR, pagoId, mapOf("motivo" to texto))
    }

    /**
     * El administrador resuelve una disputa: libera el dinero al profesional o
     * lo reembolsa al cliente. No depende del interruptor de la app: el
     * servidor comprueba el rol de administrador.
     */
    suspend fun resolver(pagoId: String, decision: DecisionDisputa): ResultadoPago<EstadoPago> {
        if (pagoId.isBlank()) return ResultadoPago.Error(ErrorPago.PAGO_NO_ENCONTRADO)
        return llamar("pagos_resolver", mapOf("pagoId" to pagoId, "decision" to decision.id)) { datos ->
            EstadoPago.desde(datos["estado"] as? String).takeIf { it != EstadoPago.DESCONOCIDO }
        }
    }

    private suspend fun hojaDePago(accion: AccionPago, pagoId: String): ResultadoPago<DatosPaymentSheet> {
        // Sin clave de test no se podría abrir PaymentSheet: ni se crea el cobro
        if (!pagosDisponibles()) return ResultadoPago.Error(ErrorPago.PAGOS_NO_DISPONIBLES)
        if (pagoId.isBlank()) return ResultadoPago.Error(ErrorPago.PAGO_NO_ENCONTRADO)
        // stripeVersion: la versión de la API del SDK, con la que el servidor crea la ephemeral key
        return llamar(accion.funcion, mapOf("pagoId" to pagoId, "stripeVersion" to PasarelaStripe.versionApi)) {
            DatosPaymentSheet.desdeRespuesta(it)
        }
    }

    /**
     * Cancelar, terminar, confirmar y disputar no dependen del interruptor de la
     * app: sobre un pago ya creado decide el servidor (un cliente con el dinero
     * retenido tiene que poder disputar aunque se apaguen los pagos nuevos).
     */
    private suspend fun cambioDeEstado(
        accion: AccionPago,
        pagoId: String,
        extra: Map<String, Any> = emptyMap()
    ): ResultadoPago<EstadoPago> {
        if (pagoId.isBlank()) return ResultadoPago.Error(ErrorPago.PAGO_NO_ENCONTRADO)
        return llamar(accion.funcion, mapOf("pagoId" to pagoId) + extra) { datos ->
            EstadoPago.desde(datos["estado"] as? String).takeIf { it != EstadoPago.DESCONOCIDO }
        }
    }

    /**
     * Llama a [funcion] y convierte la respuesta con [leer] (null = respuesta no
     * válida). Los fallos se clasifican con [ErroresPago.clasificar]; los que no
     * son de red ni de uso normal se registran en Crashlytics.
     */
    private suspend fun <T> llamar(
        funcion: String,
        datos: Map<String, Any>,
        leer: (Map<*, *>) -> T?
    ): ResultadoPago<T> = suspendCancellableCoroutine { cont ->
        functions.getHttpsCallable(funcion)
            .call(HashMap(datos))
            .addOnSuccessListener { r ->
                val valor = (r.getData() as? Map<*, *>)?.let(leer)
                cont.resume(if (valor != null) ResultadoPago.Ok(valor) else ResultadoPago.Error(ErrorPago.RESPUESTA_NO_VALIDA))
            }
            .addOnFailureListener { e ->
                val fe = e as? FirebaseFunctionsException
                val error = ErroresPago.clasificar(e.esFalloDeRed(), fe?.code?.name, fe?.details)
                if (error == ErrorPago.DESCONOCIDO) Diagnostico.errorDeFuncion(funcion, e)
                cont.resume(ResultadoPago.Error(error, ErroresPago.motivo(fe?.details)))
            }
    }
}
