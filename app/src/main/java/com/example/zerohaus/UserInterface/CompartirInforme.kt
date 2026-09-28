package com.example.zerohaus.UserInterface

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.Formato
import com.example.zerohaus.Util.TextosEnergia
import com.example.zerohaus.Util.getCadenas
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/** Genera el PDF y lo guarda en [destino] sin abrir ningún intent. */
fun compartirInformeSilencioso(context: Context, informe: InformeEnergetico, destino: File) {
    val pdf = generarPdf(context, informe)
    if (pdf.canonicalPath != destino.canonicalPath) {
        pdf.copyTo(destino, overwrite = true)
    }
}

fun compartirInforme(context: Context, informe: InformeEnergetico) {
    val c = getCadenas(AppEstado.idioma)
    val pdfFile = try { generarPdf(context, informe) } catch (_: Exception) { null }
    if (pdfFile != null) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "${c.infTitulo} — ${informe.nombreVivienda}")
            putExtra(Intent.EXTRA_TEXT, buildTextoInforme(informe))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, c.histCompartirTitulo))
    } else {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${c.infTitulo} — ${informe.nombreVivienda}")
            putExtra(Intent.EXTRA_TEXT, buildTextoInforme(informe))
        }
        context.startActivity(Intent.createChooser(intent, c.histCompartirTitulo))
    }
}

private fun generarPdf(context: Context, informe: InformeEnergetico): File {
    val c = getCadenas(AppEstado.idioma)
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val fechaStr = sdf.format(Date(informe.fechaGeneracion))

    val pageWidth = 595
    val pageHeight = 842

    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
    val canvas = page.canvas
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val verde = Color.parseColor("#16A34A")
    val verdeFondo = Color.parseColor("#DCFCE7")
    val gris = Color.parseColor("#6B7280")
    val grisClaro = Color.parseColor("#F3F4F6")
    val blanco = Color.WHITE
    val negro = Color.BLACK

    var y = 0f

    paint.color = verde
    canvas.drawRect(0f, 0f, pageWidth.toFloat(), 80f, paint)

    paint.color = blanco
    paint.textSize = 22f
    paint.isFakeBoldText = true
    canvas.drawText("ZeroHaus", 24f, 34f, paint)
    paint.textSize = 11f
    paint.isFakeBoldText = false
    canvas.drawText(c.infTitulo.uppercase(), 24f, 54f, paint)

    paint.textAlign = Paint.Align.RIGHT
    paint.textSize = 10f
    canvas.drawText(fechaStr, (pageWidth - 24).toFloat(), 34f, paint)
    canvas.drawText(informe.nombreVivienda, (pageWidth - 24).toFloat(), 54f, paint)
    paint.textAlign = Paint.Align.LEFT
    y = 106f

    val badgeColor = etiquetaColorPdf(informe.etiqueta)
    paint.color = badgeColor
    canvas.drawRoundRect(RectF(24f, y, 74f, y + 50f), 10f, 10f, paint)
    paint.color = blanco
    paint.textSize = 30f
    paint.isFakeBoldText = true
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(informe.etiqueta, 49f, y + 37f, paint)
    paint.textAlign = Paint.Align.LEFT

    paint.color = negro
    paint.textSize = 18f
    paint.isFakeBoldText = true
    canvas.drawText(TextosEnergia.estado(informe.etiqueta, c), 90f, y + 24f, paint)
    paint.textSize = 11f
    paint.isFakeBoldText = false
    paint.color = gris
    canvas.drawText(c.infCalificacion, 90f, y + 40f, paint)
    y += 72f

    paint.color = grisClaro
    canvas.drawRect(24f, y, (pageWidth - 24).toFloat(), y + 1f, paint)
    y += 16f

    val boxW = (pageWidth - 48f - 16f) / 3f
    val stats = listOf(
        Triple(c.histConsumo.uppercase(), Formato.formatEnergiaAnual(informe.consumoEstimado, 0), c.pdfEnergiaFinal),
        Triple(c.histEmisiones.uppercase(), Formato.formatEmisionesAnual(informe.emisiones), c.pdfCo2Equiv),
        Triple(c.histCoste.uppercase(), Formato.formatMonedaAnual(informe.costeAnual, 0), c.pdfEstimacionAnual)
    )
    stats.forEachIndexed { i, (titulo, valor, desc) ->
        val bx = 24f + i * (boxW + 8f)
        paint.color = grisClaro
        canvas.drawRoundRect(RectF(bx, y, bx + boxW, y + 74f), 8f, 8f, paint)
        paint.color = gris
        paint.textSize = 9f
        paint.isFakeBoldText = true
        canvas.drawText(titulo, bx + 10f, y + 18f, paint)
        paint.color = negro
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText(valor, bx + 10f, y + 40f, paint)
        paint.color = gris
        paint.textSize = 8f
        paint.isFakeBoldText = false
        canvas.drawText(desc, bx + 10f, y + 58f, paint)
    }
    y += 92f

    if (informe.recomendaciones.isNotEmpty()) {
        paint.color = negro
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText(c.infRecomendaciones, 24f, y, paint)
        y += 14f
        paint.color = gris
        paint.textSize = 9f
        paint.isFakeBoldText = false
        canvas.drawText(c.pdfMejoras, 24f, y, paint)
        y += 20f

        informe.recomendaciones.forEach { rec ->
            if (y > pageHeight - 80f) return@forEach
            paint.color = verdeFondo
            canvas.drawRoundRect(RectF(24f, y, (pageWidth - 24).toFloat(), y + 46f), 6f, 6f, paint)
            paint.color = verde
            canvas.drawCircle(52f, y + 23f, 18f, paint)
            paint.color = blanco
            paint.textSize = 10f
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${rec.ahorroEstimado}%", 52f, y + 27f, paint)
            paint.textAlign = Paint.Align.LEFT
            paint.color = negro
            paint.textSize = 11f
            paint.isFakeBoldText = true
            canvas.drawText(TextosEnergia.recomendacion(rec.titulo, c), 80f, y + 20f, paint)
            paint.color = gris
            paint.textSize = 9f
            paint.isFakeBoldText = false
            canvas.drawText(if (rec.ahorroEuros > 0) "${c.infAhorroEstimado}: ${Formato.formatMonedaAnual(rec.ahorroEuros, 0)} (${rec.ahorroEstimado}%)" else "${c.infAhorroEstimado}: ${rec.ahorroEstimado}%", 80f, y + 36f, paint)
            y += 56f
        }
    }

    paint.color = gris
    paint.textSize = 8f
    paint.isFakeBoldText = false
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(
        c.pdfGenerado,
        pageWidth / 2f, (pageHeight - 22).toFloat(), paint
    )
    canvas.drawText(
        "© ${Calendar.getInstance().get(Calendar.YEAR)} ZeroHaus. ${c.pdfAviso}",
        pageWidth / 2f, (pageHeight - 10).toFloat(), paint
    )

    doc.finishPage(page)

    val dir = File(context.cacheDir, "informes")
    dir.mkdirs()
    val nombreSeguro = informe.nombreVivienda
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "vivienda" }
        .take(40)
    val fileName = "informe_${nombreSeguro}_${informe.fechaGeneracion}.pdf"
    val file = File(dir, fileName)
    FileOutputStream(file).use { doc.writeTo(it) }
    doc.close()
    return file
}

private fun etiquetaColorPdf(etiqueta: String): Int = when (etiqueta) {
    "A" -> Color.parseColor("#15803D")
    "B" -> Color.parseColor("#16A34A")
    "C" -> Color.parseColor("#84CC16")
    "D" -> Color.parseColor("#EAB308")
    "E" -> Color.parseColor("#F97316")
    "F" -> Color.parseColor("#EF4444")
    "G" -> Color.parseColor("#991B1B")
    else -> Color.parseColor("#6B7280")
}

private fun buildTextoInforme(informe: InformeEnergetico): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val c = getCadenas(AppEstado.idioma)
    return buildString {
        appendLine("=== ${c.infTitulo.uppercase()} · ZEROHAUS ==="); appendLine()
        appendLine("${c.infVivienda}: ${informe.nombreVivienda}")
        appendLine("${c.pdfFecha}: ${sdf.format(Date(informe.fechaGeneracion))}"); appendLine()
        appendLine("${c.infCalificacion.uppercase()}: ${informe.etiqueta} — ${TextosEnergia.estado(informe.etiqueta, c)}"); appendLine()
        appendLine("${c.infIndicadores.uppercase()}:")
        appendLine("  ${c.histConsumo}: ${Formato.formatEnergiaAnual(informe.consumoEstimado)}")
        appendLine("  ${c.histEmisiones}: ${Formato.formatEmisionesAnual(informe.emisiones)}")
        appendLine("  ${c.histCoste}: ${Formato.formatMonedaAnual(informe.costeAnual)}"); appendLine()
        if (informe.recomendaciones.isNotEmpty()) {
            appendLine("${c.infRecomendaciones.uppercase()}:")
            informe.recomendaciones.forEach { r ->
                val titulo = TextosEnergia.recomendacion(r.titulo, c)
                val ahorro = if (r.ahorroEuros > 0) "${Formato.formatMonedaAnual(r.ahorroEuros, 0)}, ${r.ahorroEstimado}%" else "${r.ahorroEstimado}%"
                appendLine("  • $titulo (${c.infAhorroEstimado}: $ahorro)")
            }
            appendLine()
        }
        appendLine(c.pdfGenerado)
    }
}

