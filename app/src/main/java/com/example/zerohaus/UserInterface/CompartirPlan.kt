package com.example.zerohaus.UserInterface

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import androidx.core.content.FileProvider
import com.example.zerohaus.Repositorios.AlgoritmoEnergetico
import com.example.zerohaus.Repositorios.DeduccionIrpf
import com.example.zerohaus.Util.AppCadenas
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.Formato
import com.example.zerohaus.Util.TextosEnergia
import com.example.zerohaus.Util.getCadenas
import java.io.File
import java.io.FileOutputStream
import java.util.Date

/**
 * Comparte el plan de reforma por etapas (estilo pasaporte de renovación):
 * pasos en orden, con inversión, ahorro, amortización y la etiqueta que se
 * alcanza en cada uno. Va en PDF y también en texto, para las apps que
 * muestran el mensaje; si el PDF falla, se comparte solo el texto.
 */
fun compartirPlan(context: Context, nombreVivienda: String, etiquetaInicial: String, etapas: List<AlgoritmoEnergetico.Etapa>) {
    if (etapas.isEmpty()) return
    val c = getCadenas(AppEstado.idioma)
    val pdf = runCatching { generarPdfPlan(context, nombreVivienda, etiquetaInicial, etapas) }.getOrNull()
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_SUBJECT, "${c.planTitulo} — $nombreVivienda")
        putExtra(Intent.EXTRA_TEXT, textoPlan(nombreVivienda, etiquetaInicial, etapas, c))
        if (pdf != null) {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdf))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }
    context.startActivity(Intent.createChooser(intent, c.histCompartirTitulo))
}

/** Línea de un paso: "~5.500 € · Ahorra 306 €/año · se amortiza en 18 años". */
private fun detallePaso(e: AlgoritmoEnergetico.Etapa, c: AppCadenas) =
    "~${Formato.formatMoneda(e.mejora.inversion, 0)} · ${c.planAhorra} ${Formato.formatMonedaAnual(e.ahorroEuros, 0)} · " +
        "${c.planSeAmortiza} ${Formato.numero(e.amortizacionAnios)} ${c.simAnios}"

private fun resumenPlan(final: AlgoritmoEnergetico.Etapa, etiquetaInicial: String, c: AppCadenas) =
    "${c.simInversion}: ${Formato.formatMoneda(final.inversionAcumulada, 0)} · " +
        "${c.simAhorroAnual}: ${Formato.formatMonedaAnual(final.ahorroAcumulado, 0)} · " +
        "${c.histEtiqueta}: $etiquetaInicial → ${final.etiqueta}"

/**
 * Porcentaje de deducción que se alcanza en cada paso (solo en el primero
 * que llega a él), o 0. Todo 0 si la deducción ya no está vigente.
 */
internal fun deduccionPorPaso(
    etapas: List<AlgoritmoEnergetico.Etapa>,
    vigente: Boolean = DeduccionIrpf.vigente()
): List<Int> {
    if (!vigente) return etapas.map { 0 }
    var maximo = 0
    return etapas.map { e ->
        val pct = e.deduccionAcumulada?.porcentaje ?: 0
        if (pct > maximo) pct.also { maximo = it } else 0
    }
}

private fun textoPlan(
    nombreVivienda: String,
    etiquetaInicial: String,
    etapas: List<AlgoritmoEnergetico.Etapa>,
    c: AppCadenas
): String = buildString {
    appendLine("=== ${c.planTitulo.uppercase()} · ZEROHAUS ==="); appendLine()
    appendLine("${c.infVivienda}: $nombreVivienda")
    appendLine(c.planSub); appendLine()
    val deducciones = deduccionPorPaso(etapas)
    etapas.forEachIndexed { i, e ->
        appendLine("${i + 1}. ${TextosEnergia.recomendacion(e.mejora.titulo, c)} (${c.histEtiqueta}: ${e.etiqueta})")
        appendLine("   ${detallePaso(e, c)}")
        if (deducciones[i] > 0) appendLine("   ${c.planDeduccion} ${deducciones[i]} %")
    }
    appendLine()
    val final = etapas.last()
    appendLine("${c.planTotal.uppercase()}:")
    appendLine("  ${resumenPlan(final, etiquetaInicial, c)}")
    final.deduccionAcumulada?.takeIf { DeduccionIrpf.vigente() }?.let { d ->
        appendLine("  ${c.irpfTitulo}: ${d.porcentaje} % · ${Formato.formatMoneda(d.importe, 0)}")
    }
    appendLine()
    appendLine(c.simAviso)
    appendLine(c.pdfGenerado)
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
    // Ancho del texto de cada paso: del círculo con el número a la etiqueta
    val anchoPaso = ancho - 142f

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
    canvas.drawText(Formato.fechas("dd/MM/yyyy").format(Date()), (ancho - 24).toFloat(), 34f, paint)
    canvas.drawText(recortar(nombreVivienda, paint, ancho / 2f), (ancho - 24).toFloat(), 54f, paint)
    paint.textAlign = Paint.Align.LEFT

    // Subtítulo: en algunos idiomas no cabe en una línea
    paint.color = gris
    paint.textSize = 10f
    var y = 98f + parrafo(c.planSub, paint, ancho - 48).dibujarEn(canvas, 24f, 98f) + 20f

    // Pasos
    val deducciones = deduccionPorPaso(etapas)
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
        canvas.drawText(recortar(TextosEnergia.recomendacion(e.mejora.titulo, c), paint, anchoPaso), 70f, y + 20f, paint)
        paint.isFakeBoldText = false
        paint.color = gris
        paint.textSize = 9f
        canvas.drawText(recortar(detallePaso(e, c), paint, anchoPaso), 70f, y + 34f, paint)
        if (deducciones[i] > 0) {
            paint.color = verde
            canvas.drawText(recortar("${c.planDeduccion} ${deducciones[i]} %", paint, anchoPaso), 70f, y + 47f, paint)
        }

        // Etiqueta tras el paso
        paint.color = etiquetaColorPdf(e.etiqueta)
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
    val anchoResumen = ancho - 72f
    y += 6f
    paint.color = verdeFondo
    canvas.drawRoundRect(RectF(24f, y, (ancho - 24).toFloat(), y + 58f), 8f, 8f, paint)
    paint.color = Color.BLACK
    paint.textSize = 11f
    paint.isFakeBoldText = true
    canvas.drawText(recortar(c.planTotal, paint, anchoResumen), 36f, y + 20f, paint)
    paint.isFakeBoldText = false
    paint.textSize = 10f
    canvas.drawText(recortar(resumenPlan(final, etiquetaInicial, c), paint, anchoResumen), 36f, y + 38f, paint)
    final.deduccionAcumulada?.takeIf { DeduccionIrpf.vigente() }?.let { d ->
        paint.color = verde
        canvas.drawText(
            recortar("${c.irpfTitulo}: ${d.porcentaje} % · ${Formato.formatMoneda(d.importe, 0)}", paint, anchoResumen),
            36f, y + 52f, paint
        )
    }

    // Pie: el aviso, entero aunque ocupe dos líneas, encima de la firma
    paint.color = gris
    paint.textSize = 8f
    val aviso = parrafo(c.simAviso, paint, ancho - 48, alineacion = Layout.Alignment.ALIGN_CENTER)
    aviso.dibujarEn(canvas, 24f, alto - 22f - aviso.height)
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(c.pdfGenerado, ancho / 2f, (alto - 10).toFloat(), paint)

    doc.finishPage(pagina)
    val dir = File(context.cacheDir, "informes").apply { mkdirs() }
    val seguro = nombreVivienda.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "vivienda" }.take(40)
    val archivo = File(dir, "plan_${seguro}.pdf")
    FileOutputStream(archivo).use { doc.writeTo(it) }
    doc.close()
    return archivo
}
