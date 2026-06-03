package com.example.zerohaus.Util

/**
 * Formato de unidades de energía y moneda según los ajustes del usuario.
 * Las conversiones usan factores estándar (IDAE para energía, tipos de cambio
 * fijos aproximados para moneda — suficiente para una visualización indicativa).
 */
object Formato {

    // 1 kWh = 3.6 MJ = 859.845 kcal
    private const val KWH_A_MJ = 3.6
    private const val KWH_A_KCAL = 859.845

    // Tipos de cambio aproximados respecto al euro (referencia fija TFG)
    private const val EUR_A_USD = 1.08
    private const val EUR_A_GBP = 0.85

    fun formatEnergia(kwh: Double, decimales: Int = 1): String {
        val unidad = AppEstado.unidadEnergia
        val valor = when (unidad) {
            "MJ"   -> kwh * KWH_A_MJ
            "kcal" -> kwh * KWH_A_KCAL
            else   -> kwh
        }
        return "${"%.${decimales}f".format(valor)} $unidad"
    }

    fun formatEnergiaAnual(kwh: Double, decimales: Int = 1): String =
        "${formatEnergia(kwh, decimales)}/año"

    fun formatMoneda(eur: Double, decimales: Int = 2): String {
        val unidad = AppEstado.unidadMoneda
        val (valor, simbolo) = when (unidad) {
            "USD" -> (eur * EUR_A_USD) to "$"
            "GBP" -> (eur * EUR_A_GBP) to "£"
            else  -> eur to "€"
        }
        return "${"%.${decimales}f".format(valor)} $simbolo"
    }

    fun formatMonedaAnual(eur: Double, decimales: Int = 2): String =
        "${formatMoneda(eur, decimales)}/año"

    fun simboloMoneda(): String = when (AppEstado.unidadMoneda) {
        "USD" -> "$"
        "GBP" -> "£"
        else  -> "€"
    }

    /** Diferencia de energía (sin "/año"). */
    fun formatEnergiaDelta(kwh: Double, decimales: Int = 1): String =
        formatEnergia(kwh, decimales)
}
