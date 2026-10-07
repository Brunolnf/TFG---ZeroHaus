package com.example.zerohaus.Util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

/**
 * Fotos listas para subir: reducidas a [comprimir]`.ladoMax` px, giradas según
 * su EXIF (BitmapFactory lo ignora y las fotos en vertical salían tumbadas) y
 * en JPEG. Una foto del móvil pesa 3-20 MB; así queda en unos cientos de KB,
 * se sube antes y no choca con los límites de Storage (15 MB en el chat,
 * 5 MB la foto de perfil).
 */
object Imagenes {

    /**
     * Devuelve el JPEG, o null si [uri] no es una imagen legible. Hacer fuera
     * del hilo principal; puede lanzar OutOfMemoryError con fotos enormes en
     * móviles con poca memoria.
     */
    fun comprimir(context: Context, uri: Uri, ladoMax: Int, calidad: Int = 85): ByteArray? {
        val resolver = context.contentResolver
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null

        // Se decodifica ya reducida (potencia de 2) para no cargar la foto entera
        var muestreo = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (muestreo * 2) >= ladoMax) muestreo *= 2
        // RGB_565 ocupa la mitad que ARGB_8888 y un JPEG no tiene transparencia
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = muestreo
                inPreferredConfig = Bitmap.Config.RGB_565
            })
        } ?: return null

        val grados = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        val escala = minOf(1f, ladoMax.toFloat() / maxOf(bitmap.width, bitmap.height))
        val matriz = Matrix().apply {
            if (escala < 1f) postScale(escala, escala)
            if (grados != 0) postRotate(grados.toFloat())
        }
        val final = if (matriz.isIdentity) bitmap
                    else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)

        val salida = ByteArrayOutputStream()
        final.compress(Bitmap.CompressFormat.JPEG, calidad, salida)
        if (final !== bitmap) final.recycle()
        bitmap.recycle()
        return salida.toByteArray()
    }
}
