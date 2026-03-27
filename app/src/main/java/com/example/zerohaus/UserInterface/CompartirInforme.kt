
package com.example.zerohaus.UserInterface

import android.content.Context
import android.content.Intent
import com.example.zerohaus.Modelos.InformeEnergetico
import java.text.SimpleDateFormat
import java.util.*

fun compartirInforme(context: Context, informe: InformeEnergetico) {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val texto = buildString {
        appendLine("=== INFORME ENERGÉTICO ZEROHAUS ==="); appendLine()
        appendLine("Vivienda: ${informe.nombreVivienda}"); appendLine("Fecha: ${sdf.format(Date(informe.fechaGeneracion))}"); appendLine()
        appendLine("CALIFICACIÓN: ${informe.etiqueta} — ${informe.estadoEficiencia}"); appendLine()
        appendLine("INDICADORES:"); appendLine("  Consumo: ${informe.consumoEstimado} kWh/año"); appendLine("  Emisiones: ${informe.emisiones} kg CO₂/año"); appendLine("  Coste: ${informe.costeAnual} €/año"); appendLine()
        if (informe.recomendaciones.isNotEmpty()) { appendLine("RECOMENDACIONES:"); informe.recomendaciones.forEach { r -> appendLine("  • ${r.titulo} (ahorro ${r.ahorroEstimado}%)") }; appendLine() }
        appendLine("Generado con ZeroHaus — Eficiencia energética inteligente")
    }
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Informe energético — ${informe.nombreVivienda}"); putExtra(Intent.EXTRA_TEXT, texto) }, "Compartir informe"))
}
