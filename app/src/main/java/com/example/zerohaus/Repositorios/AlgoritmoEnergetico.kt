package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Modelos.Recomendacion
import com.example.zerohaus.Modelos.Vivienda

/**
 * Cálculo de eficiencia energética simplificado basado en los parámetros del CTE
 * (Código Técnico de la Edificación, DB HE - Ahorro de Energía) y en los factores
 * de conversión del IDAE (Instituto para la Diversificación y Ahorro de la Energía).
 *
 * La clasificación energética (A–G) sigue la escala de etiquetado energético de
 * edificios establecida en el RD 390/2021.
 */
object AlgoritmoEnergetico {

    data class ResultadoCalculo(
        val etiqueta: String,
        val estadoEficiencia: String,
        val consumoEstimado: Double,
        val emisiones: Double,
        val costeAnual: Double,
        val recomendaciones: List<Recomendacion>
    )

    fun calcular(vivienda: Vivienda): ResultadoCalculo {
        val baseConsumo = vivienda.superficie * 1.2

        val factorVentanas = when (vivienda.tipoVentanas) {
            "Vidrio simple"          -> 1.4
            "Doble acristalamiento"  -> 1.0
            "Triple"                 -> 0.8
            else                     -> 1.2
        }
        val factorAislamiento = when (vivienda.aislamiento) {
            "Sin aislamiento"        -> 1.5
            "Aislamiento parcial"    -> 1.15
            "Aislamiento completo"   -> 0.8
            else                     -> 1.2
        }
        val factorCalefaccion = when (vivienda.calefaccion) {
            "Caldera de gas"         -> 1.2
            "Eléctrica"              -> 1.4
            "Aerotermia"             -> 0.7
            "Biomasa"                -> 0.9
            else                     -> 1.1
        }
        val factorAcs = when (vivienda.acs) {
            "Gas"                    -> 1.1
            "Eléctrico"              -> 1.3
            "Solar térmica"          -> 0.6
            "Aerotermia"             -> 0.7
            else                     -> 1.0
        }
        val factorAnio = when {
            vivienda.anioConstruccion >= 2020 -> 0.8
            vivienda.anioConstruccion >= 2006 -> 0.95
            vivienda.anioConstruccion >= 1980 -> 1.1
            else                              -> 1.3
        }
        // Orientación sur maximiza captación solar pasiva (CTE DB HE1)
        val factorOrientacion = when (vivienda.orientacion) {
            "Sur"            -> 0.92
            "Este", "Oeste"  -> 1.0
            "Norte"          -> 1.08
            else             -> 1.0
        }

        val consumo = baseConsumo * factorVentanas * factorAislamiento *
                factorCalefaccion * factorAcs * factorAnio * factorOrientacion

        // Factor de emisiones: 0,22 kg CO₂/kWh (mix eléctrico español, IDAE 2023)
        val emisiones = consumo * 0.22
        // Precio medio electricidad España 2024: 0,15 €/kWh
        val coste = consumo * 0.15

        val etiqueta = when {
            consumo < 50  -> "A"
            consumo < 90  -> "B"
            consumo < 140 -> "C"
            consumo < 200 -> "D"
            consumo < 280 -> "E"
            consumo < 380 -> "F"
            else          -> "G"
        }
        val estadoEficiencia = when (etiqueta) {
            "A"  -> "Muy alta eficiencia"
            "B"  -> "Alta eficiencia"
            "C"  -> "Eficiencia buena"
            "D"  -> "Eficiencia media"
            "E"  -> "Eficiencia baja"
            "F"  -> "Eficiencia muy baja"
            else -> "Eficiencia mínima"
        }

        val recs = mutableListOf<Recomendacion>()
        if (vivienda.tipoVentanas == "Vidrio simple")
            recs.add(Recomendacion("Mejorar aislamiento de ventanas a doble acristalamiento", 25))
        if (vivienda.calefaccion == "Caldera de gas" || vivienda.calefaccion == "Eléctrica")
            recs.add(Recomendacion("Instalar aerotermia como sistema de calefacción", 20))
        if (vivienda.aislamiento != "Aislamiento completo")
            recs.add(Recomendacion("Completar el aislamiento térmico de la vivienda", 18))
        if (vivienda.acs != "Solar térmica" && vivienda.acs != "Aerotermia")
            recs.add(Recomendacion("Instalar solar térmica para ACS", 15))
        recs.add(Recomendacion("Sustituir iluminación por LED", 10))

        return ResultadoCalculo(
            etiqueta = etiqueta,
            estadoEficiencia = estadoEficiencia,
            consumoEstimado = Math.round(consumo * 10.0) / 10.0,
            emisiones = Math.round(emisiones * 10.0) / 10.0,
            costeAnual = Math.round(coste * 10.0) / 10.0,
            recomendaciones = recs
        )
    }
}
