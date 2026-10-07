package com.example.zerohaus.Util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.example.zerohaus.Modelos.Planes
import com.google.firebase.auth.FirebaseAuth
import java.security.MessageDigest

/**
 * Envoltorio de Google Play Billing para las 4 suscripciones de ZeroHaus
 * (verificado trimestral/anual, destacado mensual, destacado+anuncios mensual).
 *
 * Flujo:
 *   1. suscribirse() lanza el flujo nativo de Google Play.
 *   2. onPurchasesUpdated entrega el purchaseToken → el ViewModel lo envía
 *      a la Cloud Function `activar_suscripcion` (server-side, idempotente).
 *   3. El servidor confirma (acknowledge) la compra en Google Play; la app
 *      lo repite como respaldo.
 *
 * Cada compra se etiqueta con el hash del uid (obfuscatedAccountId) para que
 * el servidor rechace tokens pertenecientes a otra cuenta de ZeroHaus.
 *
 * Sin Google Play no hay compra posible: no existe ningún modo de prueba.
 */

/** Por qué no se pudo comprar (la UI lo muestra traducido). */
enum class ErrorCompra { NO_DISPONIBLE, NO_INICIADA, NO_COMPLETADA }

/** Suscripción vigente que se sustituye al cambiar de plan. */
data class PlanAnterior(val productoId: String, val purchaseToken: String)

class BillingManager(context: Context) : PurchasesUpdatedListener {

    var onSuscripcionPendiente: ((productoId: String, purchaseToken: String) -> Unit)? = null
    var onErrorCompra: ((ErrorCompra) -> Unit)? = null

    private var productos: Map<String, ProductDetails> = emptyMap()
    private var conectado = false

    // applicationContext: el BillingManager vive en un ViewModel, que dura más
    // que la Activity (guardarla la filtraba en cada giro de pantalla)
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        // Billing 8: reconecta solo si Play Services corta el servicio
        .enableAutoServiceReconnection()
        .build()

    /** Precio localizado de Google Play. Se toma la ÚLTIMA fase de la oferta
     *  (la cuota recurrente): la primera puede ser una prueba gratuita. */
    fun precioDe(productoId: String): String? {
        val detalles = productos[productoId] ?: return null
        return detalles.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.lastOrNull()
            ?.formattedPrice
    }

    /** Precios de todos los planes que Google Play tiene publicados. */
    fun precios(): Map<String, String> =
        productos.keys.mapNotNull { id -> precioDe(id)?.let { id to it } }.toMap()

    fun conectar(onListo: () -> Unit = {}) {
        if (conectado) { onListo(); return }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    conectado = true
                    // Las compras pendientes las procesa quien llama, en onListo
                    // (antes se procesaban dos veces cada vez)
                    consultarProductos(onListo)
                } else {
                    Log.w(TAG, "Billing no disponible: ${result.debugMessage}")
                    onListo()
                }
            }

            override fun onBillingServiceDisconnected() {
                conectado = false
            }
        })
    }

    private fun consultarProductos(onListo: () -> Unit) {
        val ids = listOf(
            Planes.VERIFICADO_TRIMESTRAL,
            Planes.VERIFICADO_ANUAL,
            Planes.DESTACADO_MENSUAL,
            Planes.DESTACADO_ANUNCIOS_MENSUAL
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(ids.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            })
            .build()
        // Billing 8: el callback devuelve QueryProductDetailsResult (productos
        // encontrados + los que Play no reconoce), ya no una lista directa.
        client.queryProductDetailsAsync(params) { result, detalles ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productos = detalles.productDetailsList.associateBy { it.productId }
            }
            onListo()
        }
    }

    fun procesarPendientes() {
        if (!conectado) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(params) { result, compras ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            compras.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { entregar(it) }
        }
    }

    /**
     * Restaura las suscripciones activas del usuario (reinstalación, cambio de
     * dispositivo). Reenvía cada compra al servidor y reporta cuántas encontró.
     *   n  > 0 → compras restauradas (se reactivan vía activar_suscripcion)
     *   n == 0 → no hay ninguna suscripción activa
     *   n <  0 → Google Play no está disponible
     */
    fun restaurarCompras(onResultado: (Int) -> Unit) {
        if (!conectado) { onResultado(-1); return }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(params) { result, compras ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onResultado(-1); return@queryPurchasesAsync
            }
            val activas = compras.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            activas.forEach { entregar(it) }
            onResultado(activas.size)
        }
    }

    /**
     * Lanza la compra de [productoId]. Con [anterior] (el plan vigente) es un
     * CAMBIO de plan: Google Play sustituye la suscripción y descuenta el tiempo
     * no usado. Sin esto, comprar otro plan creaba una segunda suscripción en
     * paralelo y se cobraban las dos.
     */
    fun suscribirse(activity: Activity, productoId: String, anterior: PlanAnterior? = null) {
        val detalles = productos[productoId]
        val offerToken = detalles?.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (!conectado || detalles == null || offerToken == null) {
            onErrorCompra?.invoke(ErrorCompra.NO_DISPONIBLE)
            return
        }
        val producto = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(detalles)
            .setOfferToken(offerToken)
        if (anterior != null) {
            producto.setSubscriptionProductReplacementParams(
                SubscriptionProductReplacementParams.newBuilder()
                    .setOldProductId(anterior.productoId)
                    .setReplacementMode(SubscriptionProductReplacementParams.ReplacementMode.WITH_TIME_PRORATION)
                    .build()
            )
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(producto.build()))
            .apply {
                cuentaOfuscada()?.let { setObfuscatedAccountId(it) }
                if (anterior != null) {
                    setSubscriptionUpdateParams(
                        BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                            .setOldPurchaseToken(anterior.purchaseToken)
                            .build()
                    )
                }
            }
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "launchBillingFlow: ${result.responseCode} ${result.debugMessage}")
            onErrorCompra?.invoke(ErrorCompra.NO_INICIADA)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, compras: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                compras.orEmpty()
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach { entregar(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> {
                Log.w(TAG, "onPurchasesUpdated: ${result.responseCode} ${result.debugMessage}")
                onErrorCompra?.invoke(ErrorCompra.NO_COMPLETADA)
            }
        }
    }

    private fun entregar(compra: Purchase) {
        val productoId = compra.products.firstOrNull() ?: return
        // Misma cuenta de Google con varios usuarios de ZeroHaus: no reenviar
        // la suscripción de otro (el servidor la rechazaría igualmente).
        val cuentaCompra = compra.accountIdentifiers?.obfuscatedAccountId
        if (cuentaCompra != null && cuentaCompra != cuentaOfuscada()) return
        onSuscripcionPendiente?.invoke(productoId, compra.purchaseToken)
    }

    fun acknowledge(purchaseToken: String) {
        if (!conectado) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "No se pudo acknowledge la suscripción: ${result.debugMessage}")
            }
        }
    }

    fun liberar() {
        onSuscripcionPendiente = null
        onErrorCompra = null
        if (client.isReady) client.endConnection()
        conectado = false
    }

    companion object {
        private const val TAG = "BillingManager"

        /**
         * SHA-256 (hex, 64 caracteres) del uid de Firebase. Google Play pide un
         * identificador opaco, sin datos personales; functions/main.py
         * (`_cuenta_ofuscada`) calcula el mismo valor para validarlo.
         */
        fun cuentaOfuscada(): String? {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
            return MessageDigest.getInstance("SHA-256")
                .digest(uid.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        /**
         * URL de la ficha de suscripciones de Google Play, donde el usuario puede
         * cambiar de plan o CANCELAR. Google Play exige ofrecer este acceso a las
         * apps con suscripciones. Si se indica [productoId] abre esa suscripción
         * concreta; si no, la lista de suscripciones de la cuenta.
         */
        fun urlGestionSuscripcion(paquete: String, productoId: String? = null): String {
            val base = "https://play.google.com/store/account/subscriptions"
            return if (productoId.isNullOrBlank()) base
            else "$base?sku=$productoId&package=$paquete"
        }
    }
}
