package com.example.zerohaus.UserInterface

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.zerohaus.Modelos.InformeEnergetico
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

fun compartirInforme(context: Context, informe: InformeEnergetico) {
    val pdfFile = try { generarPdf(context, informe) } catch (_: Exception) { null }
    if (pdfFile != null) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "Informe energético — ${informe.nombreVivienda}")
            putExtra(Intent.EXTRA_TEXT, buildTextoInforme(informe))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir informe"))
    } else {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Informe energético — ${informe.nombreVivienda}")
            putExtra(Intent.EXTRA_TEXT, buildTextoInforme(informe))
        }
        context.startActivity(Intent.createChooser(intent, "Compartir informe"))
    }
}

fun compartirPorEmail(context: Context, informe: InformeEnergetico) {
    val pdfFile = try { generarPdf(context, informe) } catch (_: Exception) { null }
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_SUBJECT, "Informe energético — ${informe.nombreVivienda}")
        putExtra(Intent.EXTRA_TEXT, buildTextoInforme(informe))
        if (pdfFile != null) {
            type = "application/pdf"
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }
    context.startActivity(Intent.createChooser(intent, "Enviar por email"))
}

fun compartirPorWhatsApp(context: Context, informe: InformeEnergetico) {
    val texto = buildTextoWhatsApp(informe)
    val pdfFile = try { generarPdf(context, informe) } catch (_: Exception) { null }

    if (pdfFile != null) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, texto)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { context.startActivity(intent); return } catch (_: Exception) {}
    }

    val intentTexto = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        setPackage("com.whatsapp")
        putExtra(Intent.EXTRA_TEXT, texto)
    }
    try {
        context.startActivity(intentTexto)
    } catch (_: Exception) {
        compartirInforme(context, informe)
    }
}

private fun generarPdf(context: Context, informe: InformeEnergetico): File {
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

    // ─── CABECERA VERDE ───
    paint.color = verde
    canvas.drawRect(0f, 0f, pageWidth.toFloat(), 80f, paint)

    paint.color = blanco
    paint.textSize = 22f
    paint.isFakeBoldText = true
    canvas.drawText("ZeroHaus", 24f, 34f, paint)
    paint.textSize = 11f
    paint.isFakeBoldText = false
    canvas.drawText("INFORME ENERGÉTICO", 24f, 54f, paint)

    paint.textAlign = Paint.Align.RIGHT
    paint.textSize = 10f
    canvas.drawText(fechaStr, (pageWidth - 24).toFloat(), 34f, paint)
    canvas.drawText(informe.nombreVivienda, (pageWidth - 24).toFloat(), 54f, paint)
    paint.textAlign = Paint.Align.LEFT
    y = 106f

    // ─── ETIQUETA ENERGÉTICA ───
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
    canvas.drawText(informe.estadoEficiencia, 90f, y + 24f, paint)
    paint.textSize = 11f
    paint.isFakeBoldText = false
    paint.color = gris
    canvas.drawText("Calificación energética", 90f, y + 40f, paint)
    y += 72f

    // ─── DIVISOR ───
    paint.color = grisClaro
    canvas.drawRect(24f, y, (pageWidth - 24).toFloat(), y + 1f, paint)
    y += 16f

    // ─── 3 CAJAS DE ESTADÍSTICAS ───
    val boxW = (pageWidth - 48f - 16f) / 3f
    val stats = listOf(
        Triple("CONSUMO", "%.0f kWh/año".format(informe.consumoEstimado), "Energía primaria"),
        Triple("EMISIONES", "%.1f kg CO₂/año".format(informe.emisiones), "CO₂ equivalente"),
        Triple("COSTE", "%.0f €/año".format(informe.costeAnual), "Estimación anual")
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

    // ─── RECOMENDACIONES ───
    if (informe.recomendaciones.isNotEmpty()) {
        paint.color = negro
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("Recomendaciones", 24f, y, paint)
        y += 14f
        paint.color = gris
        paint.textSize = 9f
        paint.isFakeBoldText = false
        canvas.drawText("Mejoras sugeridas para aumentar la eficiencia energética de tu vivienda", 24f, y, paint)
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
            canvas.drawText(rec.titulo, 80f, y + 20f, paint)
            paint.color = gris
            paint.textSize = 9f
            paint.isFakeBoldText = false
            canvas.drawText("Ahorro estimado: ${rec.ahorroEstimado}%", 80f, y + 36f, paint)
            y += 56f
        }
    }

    // ─── PIE DE PÁGINA ───
    paint.color = gris
    paint.textSize = 8f
    paint.isFakeBoldText = false
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(
        "Generado con ZeroHaus — Eficiencia energética inteligente",
        pageWidth / 2f, (pageHeight - 22).toFloat(), paint
    )
    canvas.drawText(
        "© 2026 ZeroHaus. Este informe es orientativo y no constituye asesoramiento técnico oficial.",
        pageWidth / 2f, (pageHeight - 10).toFloat(), paint
    )

    doc.finishPage(page)

    val dir = File(context.cacheDir, "informes")
    dir.mkdirs()
    val fileName = "informe_${informe.nombreVivienda.replace(" ", "_")}_${informe.fechaGeneracion}.pdf"
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
    return buildString {
        appendLine("=== INFORME ENERGÉTICO ZEROHAUS ==="); appendLine()
        appendLine("Vivienda: ${informe.nombreVivienda}")
        appendLine("Fecha: ${sdf.format(Date(informe.fechaGeneracion))}"); appendLine()
        appendLine("CALIFICACIÓN: ${informe.etiqueta} — ${informe.estadoEficiencia}"); appendLine()
        appendLine("INDICADORES:")
        appendLine("  Consumo: ${informe.consumoEstimado} kWh/año")
        appendLine("  Emisiones: ${informe.emisiones} kg CO₂/año")
        appendLine("  Coste: ${informe.costeAnual} €/año"); appendLine()
        if (informe.recomendaciones.isNotEmpty()) {
            appendLine("RECOMENDACIONES:")
            informe.recomendaciones.forEach { r -> appendLine("  • ${r.titulo} (ahorro ${r.ahorroEstimado}%)") }
            appendLine()
        }
        appendLine("Generado con ZeroHaus — Eficiencia energética inteligente")
    }
}

private fun buildTextoWhatsApp(informe: InformeEnergetico): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return buildString {
        appendLine("*Informe Energético ZeroHaus*")
        appendLine("🏠 ${informe.nombreVivienda}")
        appendLine("📅 ${sdf.format(Date(informe.fechaGeneracion))}")
        appendLine()
        appendLine("*Calificación: ${informe.etiqueta}* — ${informe.estadoEficiencia}")
        appendLine()
        appendLine("⚡ Consumo: ${informe.consumoEstimado} kWh/año")
        appendLine("🌿 Emisiones: ${informe.emisiones} kg CO₂/año")
        appendLine("💰 Coste: ${informe.costeAnual} €/año")
        if (informe.recomendaciones.isNotEmpty()) {
            appendLine()
            appendLine("*Mejoras recomendadas:*")
            informe.recomendaciones.take(3).forEach { r ->
                appendLine("• ${r.titulo} (ahorro ${r.ahorroEstimado}%)")
            }
        }
        appendLine()
        appendLine("_Generado con ZeroHaus_")
    }
}
