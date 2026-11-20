package com.example.zerohaus.Util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.example.zerohaus.BuildConfig
import com.example.zerohaus.Modelos.ClaveStripe
import com.example.zerohaus.Modelos.DatosPaymentSheet
import com.example.zerohaus.Modelos.ResultadoHojaPago
import com.stripe.android.PaymentConfiguration
import com.stripe.android.Stripe
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult

/**
 * Integración con PaymentSheet de Stripe (solo modo test).
 *
 * No hace falta tocar MainActivity ni ZeroHausApp: [PaymentConfiguration.init]
 * se llama justo antes de presentar la hoja (guarda la clave y es idempotente),
 * y la hoja la crea [rememberHojaDePago] en el composable de la pantalla, que
 * vive dentro de MainActivity (una ComponentActivity, como exige Stripe).
 */
object PasarelaStripe {

    const val NOMBRE_COMERCIO = "ZeroHaus"

    /** Versión de la API de Stripe de este SDK; el servidor crea la ephemeral key con ella. */
    val versionApi: String get() = Stripe.API_VERSION

    /** false si la build no lleva una clave pk_test_ válida (entonces no se presenta nada). */
    fun inicializar(context: Context): Boolean {
        val clave = BuildConfig.STRIPE_PUBLISHABLE_KEY
        if (!ClaveStripe.esValida(clave)) return false
        PaymentConfiguration.init(context.applicationContext, clave)
        return true
    }

    fun configuracion(datos: DatosPaymentSheet): PaymentSheet.Configuration =
        PaymentSheet.Configuration.Builder(NOMBRE_COMERCIO)
            .customer(PaymentSheet.CustomerConfiguration(datos.customerId, datos.ephemeralKeySecret))
            // Sin métodos que se confirman días después (SEPA…): el pago tiene que
            // quedar retenido o fallido en el momento
            .allowsDelayedPaymentMethods(false)
            .build()

    fun resultado(r: PaymentSheetResult): ResultadoHojaPago = when (r) {
        is PaymentSheetResult.Completed -> ResultadoHojaPago.Completado
        is PaymentSheetResult.Canceled -> ResultadoHojaPago.Cancelado
        is PaymentSheetResult.Failed -> ResultadoHojaPago.Fallido(r.error.message)
    }
}

/**
 * PaymentSheet lista para la pantalla de pago (T-E8). Devuelve la función que
 * la presenta. Hay que llamarla siempre, fuera de cualquier `if` (Stripe
 * registra un ActivityResultLauncher, como rememberLauncherForActivityResult),
 * en el composable de la pantalla que muestra el pago:
 *
 * ```
 * val presentar = rememberHojaDePago(onResultado = vm::resultadoHoja)
 * val flujo = vm.estado.flujoPago
 * LaunchedEffect(flujo) {
 *     if (flujo is FlujoPago.Listo) {
 *         vm.hojaPresentada()        // antes de presentar: así no se abre dos veces
 *         presentar(flujo.datos)
 *     }
 * }
 * ```
 *
 * Tras [ResultadoHojaPago.Completado] el pago NO está pagado: lo dice el webhook
 * y la pantalla lo ve en el snapshot de `/pagos` (FlujoPago.Procesando → Resuelto).
 */
@Composable
fun rememberHojaDePago(onResultado: (ResultadoHojaPago) -> Unit): (DatosPaymentSheet) -> Unit {
    val context = LocalContext.current.applicationContext
    val alResultado by rememberUpdatedState(onResultado)
    // El Builder se recuerda; build() es composable y registra el launcher una vez
    val hoja = remember { PaymentSheet.Builder { r -> alResultado(PasarelaStripe.resultado(r)) } }.build()
    return remember(hoja, context) {
        { datos ->
            if (PasarelaStripe.inicializar(context)) {
                hoja.presentWithPaymentIntent(datos.paymentIntentClientSecret, PasarelaStripe.configuracion(datos))
            } else {
                alResultado(ResultadoHojaPago.Fallido("Sin clave publicable de test"))
            }
        }
    }
}
