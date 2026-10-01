package com.example.zerohaus.Repositorios

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.zerohaus.Util.Diagnostico
import com.example.zerohaus.Util.esFalloDeRed
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.ByteArrayOutputStream

/** Lo que se lee de una factura de la luz, ya validado por el servidor. */
data class DatosFactura(
    val consumoKwh: Double,        // del periodo facturado
    val dias: Int,
    val importeTotal: Double,      // €, con impuestos
    val potenciaKw: Double,        // 0 si no se pudo leer
    val consumoAnualKwh: Double,   // el periodo llevado a un año
    val precioMedio: Double        // €/kWh = importe / consumo
)

/** Motivo de fallo, para mostrar el mensaje traducido en la UI. */
enum class ErrorFactura { NO_LEGIBLE, DEMASIADO_GRANDE, LIMITE_DIARIO, SIN_CONEXION, NO_DISPONIBLE }

class ErrorFacturaException(val tipo: ErrorFactura) : Exception(tipo.name)

/**
 * Lectura de la factura de la luz con IA: la Cloud Function `leer_factura`
 * pasa el archivo a Gemini y devuelve solo consumo, días, importe y potencia.
 * El archivo viaja en la llamada y el servidor no lo guarda.
 */
class RepositorioFactura {

    private val functions = FirebaseFunctions.getInstance("europe-west1")

    fun leer(mime: String, datos: ByteArray, callback: (Result<DatosFactura>) -> Unit) {
        functions.getHttpsCallable("leer_factura")
            .call(hashMapOf("mime" to mime, "datos" to Base64.encodeToString(datos, Base64.NO_WRAP)))
            .addOnSuccessListener { r ->
                val m = r.getData() as? Map<*, *>
                fun num(k: String) = (m?.get(k) as? Number)?.toDouble() ?: 0.0
                if (m == null || num("consumoAnualKwh") <= 0.0) {
                    callback(Result.failure(ErrorFacturaException(ErrorFactura.NO_LEGIBLE)))
                    return@addOnSuccessListener
                }
                callback(Result.success(DatosFactura(
                    consumoKwh = num("consumoKwh"),
                    dias = num("dias").toInt(),
                    importeTotal = num("importeTotal"),
                    potenciaKw = num("potenciaKw"),
                    consumoAnualKwh = num("consumoAnualKwh"),
                    precioMedio = num("precioMedio")
                )))
            }
            .addOnFailureListener { e ->
                val fe = e as? FirebaseFunctionsException
                val motivo = (fe?.details as? Map<*, *>)?.get("motivo")
                val tipo = when {
                    e.esFalloDeRed() -> ErrorFactura.SIN_CONEXION
                    fe?.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> ErrorFactura.LIMITE_DIARIO
                    motivo == "no_legible" || motivo == "formato" -> ErrorFactura.NO_LEGIBLE
                    motivo == "tamano" -> ErrorFactura.DEMASIADO_GRANDE
                    else -> ErrorFactura.NO_DISPONIBLE
                }
                // Una factura ilegible o el límite diario son uso normal; lo demás, un fallo
                if (tipo == ErrorFactura.NO_DISPONIBLE) Diagnostico.errorDeFuncion("leer_factura", e)
                callback(Result.failure(ErrorFacturaException(tipo)))
            }
    }

    companion object {
        const val MAX_BYTES = 7 * 1024 * 1024   // lo mismo que acepta el servidor
        private const val LADO_MAX = 2048        // px: suficiente para leer la letra pequeña

        /**
         * Prepara el archivo elegido para enviarlo: los PDF tal cual y las
         * fotos reducidas a JPEG (una foto del móvil pesa 3-10 MB; así queda en
         * unos cientos de KB y se lee igual). Hacer fuera del hilo principal.
         * Devuelve (mime, bytes) o lanza [ErrorFacturaException].
         */
        fun prepararArchivo(context: Context, uri: Uri): Pair<String, ByteArray> {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri).orEmpty()
            if (mime == "application/pdf") {
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw ErrorFacturaException(ErrorFactura.NO_LEGIBLE)
                if (bytes.size > MAX_BYTES) throw ErrorFacturaException(ErrorFactura.DEMASIADO_GRANDE)
                return mime to bytes
            }
            // Imagen (o tipo desconocido, como la foto recién hecha): se decodifica
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
            if (limites.outWidth <= 0 || limites.outHeight <= 0) throw ErrorFacturaException(ErrorFactura.NO_LEGIBLE)
            var muestreo = 1
            while (maxOf(limites.outWidth, limites.outHeight) / (muestreo * 2) >= LADO_MAX) muestreo *= 2
            // RGB_565 ocupa la mitad que ARGB_8888 y para leer texto sobra: en el
            // peor caso (lado de casi 4096 px) la foto queda en unos 25 MB
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = muestreo
                    inPreferredConfig = Bitmap.Config.RGB_565
                })
            } ?: throw ErrorFacturaException(ErrorFactura.NO_LEGIBLE)
            val escala = LADO_MAX.toFloat() / maxOf(bitmap.width, bitmap.height)
            val final = if (escala < 1f)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * escala).toInt(), (bitmap.height * escala).toInt(), true)
            else bitmap
            val salida = ByteArrayOutputStream()
            final.compress(Bitmap.CompressFormat.JPEG, 85, salida)
            if (final !== bitmap) final.recycle()
            bitmap.recycle()
            return "image/jpeg" to salida.toByteArray()
        }
    }
}
