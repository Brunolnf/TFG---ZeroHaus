package com.example.zerohaus.Util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL

/**
 * Descarga fotos de casas y genera archivos de prueba (PDFs simulados)
 * en el almacenamiento del dispositivo para poder adjuntarlos en los chats.
 *
 * Usa MediaStore (compatible Android 10+) para que aparezcan en la
 * galeria y en el explorador de archivos sin permisos extra.
 */
object DescargadorArchivos {

    // Fotos de casas reales vía Unsplash (libres de derechos, tamaño fijo)
    private val fotosUrls = listOf(
        "https://images.unsplash.com/photo-1518780664697-55e3ad937233?w=800&q=80" to "casa_unifamiliar.jpg",
        "https://images.unsplash.com/photo-1564013799919-ab600027ffc6?w=800&q=80" to "casa_moderna.jpg",
        "https://images.unsplash.com/photo-1570129477492-45c003edd2be?w=800&q=80" to "fachada_principal.jpg",
        "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?w=800&q=80" to "chalet_jardin.jpg",
        "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?w=800&q=80" to "salon_interior.jpg",
        "https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?w=800&q=80" to "cocina_reformada.jpg",
        "https://images.unsplash.com/photo-1523217582562-09d0def993a6?w=800&q=80" to "tejado_paneles.jpg",
        "https://images.unsplash.com/photo-1558036117-15d82a90b9b1?w=800&q=80" to "piso_exterior.jpg"
    )

    // Nombres de archivos de prueba (se generan como bitmaps con texto)
    private val archivosNombres = listOf(
        "presupuesto_aerotermia.pdf",
        "informe_energetico_vivienda.pdf",
        "factura_instalacion.pdf",
        "certificado_eficiencia.pdf",
        "plano_instalacion.pdf",
        "contrato_reforma.pdf"
    )

    data class Progreso(val actual: Int, val total: Int, val nombre: String)

    suspend fun descargarTodo(
        context: Context,
        onProgreso: (Progreso) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val total = fotosUrls.size + archivosNombres.size
        var descargadas = 0
        var errores = 0

        // 1. Descargar fotos de casas a la galeria
        fotosUrls.forEachIndexed { i, (url, nombre) ->
            onProgreso(Progreso(i + 1, total, nombre))
            try {
                val bytes = URL(url).readBytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    guardarImagenEnGaleria(context, bitmap, nombre)
                    bitmap.recycle()
                    descargadas++
                } else {
                    errores++
                }
            } catch (e: Exception) {
                errores++
            }
        }

        // 2. Generar archivos de prueba en Downloads
        archivosNombres.forEachIndexed { i, nombre ->
            val idx = fotosUrls.size + i + 1
            onProgreso(Progreso(idx, total, nombre))
            try {
                generarArchivoPrueba(context, nombre)
                descargadas++
            } catch (e: Exception) {
                errores++
            }
        }

        val msg = "$descargadas archivos descargados" +
                if (errores > 0) " ($errores errores)" else ""
        Result.success(msg)
    }

    private fun guardarImagenEnGaleria(context: Context, bitmap: Bitmap, nombre: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, nombre)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ZeroHaus")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
    }

    private fun generarArchivoPrueba(context: Context, nombre: String) {
        // Genera una imagen con aspecto de documento (texto sobre fondo blanco)
        // para que sirva como archivo adjuntable en los chats
        val w = 595; val h = 842 // A4 proporcional
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paintTitulo = Paint().apply {
            color = Color.parseColor("#065F46")
            textSize = 28f; isFakeBoldText = true; isAntiAlias = true
        }
        val paintTexto = Paint().apply {
            color = Color.parseColor("#374151")
            textSize = 16f; isAntiAlias = true
        }
        val paintLinea = Paint().apply {
            color = Color.parseColor("#D1D5DB")
            strokeWidth = 1f
        }
        val paintLogo = Paint().apply {
            color = Color.parseColor("#16A34A")
            textSize = 14f; isAntiAlias = true
        }

        // Cabecera
        canvas.drawText("ZeroHaus", 40f, 50f, paintLogo)
        canvas.drawLine(40f, 65f, w - 40f, 65f, paintLinea)

        // Titulo del documento
        val titulo = nombre.removeSuffix(".pdf").replace("_", " ")
            .replaceFirstChar { it.uppercase() }
        canvas.drawText(titulo, 40f, 110f, paintTitulo)

        // Contenido simulado
        val lineas = listOf(
            "Documento generado para pruebas de ZeroHaus.",
            "",
            "Cliente: Propietario de prueba",
            "Fecha: ${java.text.SimpleDateFormat("dd/MM/yyyy").format(java.util.Date())}",
            "Referencia: ZH-${(1000..9999).random()}",
            "",
            "─────────────────────────────────────────",
            "",
            "Este documento es un archivo de prueba descargado",
            "desde el panel de administracion de ZeroHaus para",
            "verificar el envio de archivos en las conversaciones.",
            "",
            "Puede adjuntarlo en cualquier chat para comprobar",
            "que la subida, visualizacion y descarga de archivos",
            "funciona correctamente.",
            "",
            "─────────────────────────────────────────",
            "",
            "Datos tecnicos de ejemplo:",
            "  - Superficie: 120 m2",
            "  - Consumo estimado: 8.500 kWh/ano",
            "  - Etiqueta energetica: D",
            "  - Emisiones CO2: 1.850 kg/ano",
            "",
            "Presupuesto desglosado:",
            "  - Material: 4.200,00 EUR",
            "  - Mano de obra: 2.800,00 EUR",
            "  - IVA (21%): 1.470,00 EUR",
            "  - TOTAL: 8.470,00 EUR"
        )
        var y = 155f
        lineas.forEach { linea ->
            canvas.drawText(linea, 40f, y, paintTexto)
            y += 22f
        }

        // Pie
        canvas.drawLine(40f, h - 60f, w - 40f, h - 60f, paintLinea)
        canvas.drawText("ZeroHaus - Eficiencia energetica para tu hogar", 40f, h - 35f, paintLogo)

        // Guardar como imagen en Downloads (se puede abrir como "archivo" en el chat)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, nombre.replace(".pdf", ".png"))
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ZeroHaus")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }
}
