package com.example.zerohaus.Repositorios

/**
 * Precios medios de la energía para hogares en España (€/kWh, IVA incluido)
 * que usa [AlgoritmoEnergetico]. Arrancan con los valores de 2024 y
 * Remote Config los actualiza al abrir la app ([com.example.zerohaus.Util.ConfigRemota]),
 * así se pueden cambiar desde la consola de Firebase sin publicar una versión.
 */
object PreciosEnergia {

    const val ELECTRICIDAD_DEFECTO = 0.17
    const val GAS_DEFECTO = 0.08          // gas natural TUR
    const val BIOMASA_DEFECTO = 0.06      // pellet

    // Fuera de este rango un precio no es real: sería un error al teclearlo en
    // la consola, y un informe con él daría costes absurdos.
    private val RANGO_VALIDO = 0.01..1.5

    @Volatile var electricidad = ELECTRICIDAD_DEFECTO
        private set
    @Volatile var gas = GAS_DEFECTO
        private set
    @Volatile var biomasa = BIOMASA_DEFECTO
        private set

    /** Aplica los precios remotos; los que no sean válidos se ignoran. */
    fun actualizar(electricidad: Double, gas: Double, biomasa: Double) {
        if (electricidad in RANGO_VALIDO) this.electricidad = electricidad
        if (gas in RANGO_VALIDO) this.gas = gas
        if (biomasa in RANGO_VALIDO) this.biomasa = biomasa
    }

    /** Vuelve a los precios por defecto (tests). */
    fun restablecer() {
        electricidad = ELECTRICIDAD_DEFECTO
        gas = GAS_DEFECTO
        biomasa = BIOMASA_DEFECTO
    }

    /** Precio de la luz para una vivienda: el de su factura si lo tiene y es válido. */
    fun electricidadPara(precioFactura: Double): Double =
        if (precioFactura in RANGO_VALIDO) precioFactura else electricidad
}
