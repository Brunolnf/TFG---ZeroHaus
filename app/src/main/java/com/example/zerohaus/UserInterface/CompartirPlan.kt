package com.example.zerohaus.UserInterface

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.zerohaus.Repositorios.AlgoritmoEnergetico
import com.example.zerohaus.Repositorios.DeduccionIrpf
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.Formato
import com.example.zerohaus.Util.TextosEnergia
import com.example.zerohaus.Util.getCadenas
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Comparte en PDF el plan de reforma por etapas (estilo pasaporte de
 * renovación): pasos en orden, con inversión, ahorro, amortización y la
 * etiqueta que se alcanza en cada uno.
 */
fun compartirPlan(context: Context, nombreVivienda: String, etiquetaInicial: String, etapas: List<AlgoritmoEnergetico.Etapa>) {
    val c = getCadenas(AppEstado.idioma)
    val pdf = runCatching { generarPdfPlan(context, nombreVivienda, etiquetaInicial, etapas) }.getOrNull() ?: return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdf)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_SUBJECT, "${c.planTitulo} — $nombreVivienda")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, c.histCompartirTitulo))
}

private fun generarPdfPlan(
    context: Context,
    nombreVivienda: String,
    etiquetaInicial: String,
    etapas: List<AlgoritmoEnergetico.Etapa>
): File {
    val c = getCadenas(AppEstado.idioma)
    val ancho = 595
    val alto = 842
    val doc = PdfDocument()
    val pagina = doc.startPage(PdfDocument.PageInfo.Builder(ancho, alto, 1).create())
    val canvas = pagina.canvas
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val verde = Color.parseColor("#16A34A")
    val verdeFondo = Color.parseColor("#DCFCE7")
    val gris = Color.parseColor("#6B7280")
    val grisClaro = Color.parseColor("#F3F4F6")

    // Cabecera
    paint.color = verde
    canvas.drawRect(0f, 0f, ancho.toFloat(), 80f, paint)
    paint.color = Color.WHITE
    paint.textSize = 22f
    paint.isFakeBoldText = true
    canvas.drawText("ZeroHaus", 24f, 34f, paint)
    paint.textSize = 11f
    paint.isFakeBoldText = false
    canvas.drawText(c.planTitulo.uppercase(), 24f, 54f, paint)
    paint.textAlign = Paint.Align.RIGHT
    paint.textSize = 10f
    canvas.drawText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()), (ancho - 24).toFloat(), 34f, paint)
    canvas.drawText(nombreVivienda.take(45), (ancho - 24).toFloat(), 54f, paint)
    paint.textAlign = Paint.Align.LEFT

    var y = 108f
    paint.color = gris
    paint.textSize = 10f
    canvas.drawText(c.planSub.take(110), 24f, y, paint)
    y += 22f

    // Pasos
    val deduccionVigente = DeduccionIrpf.vigente()
    var ultimoPctMostrado = 0
    etapas.forEachIndexed { i, e ->
        if (y > alto - 140f) return@forEachIndexed
        paint.color = grisClaro
        canvas.drawRoundRect(RectF(24f, y, (ancho - 24).toFloat(), y + 54f), 8f, 8f, paint)
        paint.color = verde
        canvas.drawCircle(46f, y + 27f, 13f, paint)
        paint.color = Color.WHITE
        paint.textSize = 12f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${i + 1}", 46f, y + 31f, paint)
        paint.textAlign = Paint.Align.LEFT

        paint.color = Color.BLACK
        paint.textSize = 11f
        canvas.drawText(TextosEnergia.recomendacion(e.mejora.titulo, c).take(60), 70f, y + 20f, paint)
        paint.isFakeBoldText = false
        paint.color = gris
        paint.textSize = 9f
        canvas.drawText(
            "~${Formato.formatMoneda(e.mejora.inversion, 0)} · ${c.planAhorra} ${Formato.formatMonedaAnual(e.ahorroEuros, 0)} · " +
                "${c.planSeAmortiza} ${Formato.numero(e.amortizacionAnios)} ${c.simAnios}",
            70f, y + 34f, paint
        )
        val pct = e.deduccionAcumulada?.porcentaje ?: 0
        if (deduccionVigente && pct > ultimoPctMostrado) {
            paint.color = verde
            canvas.drawText("${c.planDeduccion} $pct %", 70f, y + 47f, paint)
            ultimoPctMostrado = pct
        }

        // Etiqueta tras el paso
        paint.color = etiquetaColorPlan(e.etiqueta)
        canvas.drawRoundRect(RectF((ancho - 64).toFloat(), y + 12f, (ancho - 34).toFloat(), y + 42f), 6f, 6f, paint)
        paint.color = Color.WHITE
        paint.textSize = 14f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(e.etiqueta, (ancho - 49).toFloat(), y + 32f, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.isFakeBoldText = false
        y += 62f
    }

    // Resumen
    val final = etapas.last()
    y += 6f
    paint.color = verdeFondo
    canvas.drawRoundRect(RectF(24f, y, (ancho - 24).toFloat(), y + 58f), 8f, 8f, paint)
    paint.color = Color.BLACK
    paint.textSize = 11f
    paint.isFakeBoldText = true
    canvas.drawText(c.planTotal, 36f, y + 20f, paint)
    paint.isFakeBoldText = false
    paint.textSize = 10f
    canvas.drawText(
        "${c.simInversion}: ${Formato.formatMoneda(final.inversionAcumulada, 0)} · " +
            "${c.simAhorroAnual}: ${Formato.formatMonedaAnual(final.ahorroAcumulado, 0)} · " +
            "${c.histEtiqueta}: $etiquetaInicial → ${final.etiqueta}",
        36f, y + 38f, paint
    )
    final.deduccionAcumulada?.takeIf { deduccionVigente }?.let { d ->
        paint.color = verde
        canvas.drawText("${c.irpfTitulo}: ${d.porcentaje} % · ${Formato.formatMoneda(d.importe, 0)}", 36f, y + 52f, paint)
    }

    // Pie
    paint.color = gris
    paint.textSize = 8f
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(c.simAviso.take(140), ancho / 2f, (alto - 22).toFloat(), paint)
    canvas.drawText(c.pdfGenerado, ancho / 2f, (alto - 10).toFloat(), paint)

    doc.finishPage(pagina)
    val dir = File(context.cacheDir, "informes").apply { mkdirs() }
    val seguro = nombreVivienda.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "vivienda" }.take(40)
    val archivo = File(dir, "plan_${seguro}.pdf")
    FileOutputStream(archivo).use { doc.writeTo(it) }
    doc.close()
    return archivo
}

private fun etiquetaColorPlan(etiqueta: String): Int = when (etiqueta) {
    "A" -> Color.parseColor("#15803D")
    "B" -> Color.parseColor("#22C55E")
    "C" -> Color.parseColor("#84CC16")
    "D" -> Color.parseColor("#EAB308")
    "E" -> Color.parseColor("#F97316")
    "F" -> Color.parseColor("#EF4444")
    else -> Color.parseColor("#991B1B")
}
