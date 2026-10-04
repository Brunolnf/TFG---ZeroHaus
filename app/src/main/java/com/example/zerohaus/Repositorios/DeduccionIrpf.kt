package com.example.zerohaus.Repositorios

import java.time.LocalDate

/**
 * Deducciones del IRPF por obras de mejora de la eficiencia energética en la
 * vivienda habitual o alquilada (DA 50ª de la Ley del IRPF, prorrogadas por el
 * RDL 16/2025 para obras hechas hasta el 31/12/2026):
 *  - 20 % si la demanda de calefacción y refrigeración baja al menos un 7 %
 *    (base máxima 5.000 €, así que como mucho 1.000 €).
 *  - 40 % si el consumo de energía primaria no renovable baja al menos un
 *    30 % o la vivienda mejora hasta la letra A o B (base máxima 7.500 €:
 *    como mucho 3.000 €).
 * La del 60 % es para obras del edificio entero (comunidad de propietarios),
 * que la app no calcula. Para aplicarlas hace falta un certificado energético
 * oficial antes y después de la obra: esto es solo una estimación.
 */
object DeduccionIrpf {

    enum class Motivo { DEMANDA, ENERGIA_PRIMARIA, ETIQUETA }

    data class Resultado(
        val porcentaje: Int,        // 20 o 40
        val baseMaxima: Double,     // € de inversión que cuentan como mucho
        val importe: Double,        // € que se deducirían
        val motivo: Motivo,
        val reduccionPct: Int       // % de reducción de demanda o de energía primaria
    )

    private const val BASE_20 = 5_000.0
    private const val BASE_40 = 7_500.0
    private const val REDUCCION_DEMANDA_20 = 0.07
    private const val REDUCCION_PRIMARIA_40 = 0.30

    /** Último día de obras con deducción. Remote Config lo cambia si se vuelve a prorrogar. */
    @Volatile var fechaLimite: LocalDate = LocalDate.of(2026, 12, 31)
        private set

    fun actualizarFechaLimite(texto: String) {
        runCatching { LocalDate.parse(texto.trim()) }.onSuccess { fechaLimite = it }
    }

    fun vigente(hoy: LocalDate = LocalDate.now()): Boolean = !hoy.isAfter(fechaLimite)

    /** La mejor deducción a la que darían derecho unas obras, o null si no llegan a ninguna. */
    fun calcular(
        antes: AlgoritmoEnergetico.Balance,
        despues: AlgoritmoEnergetico.Balance,
        inversion: Double
    ): Resultado? {
        if (inversion <= 0) return null
        val reduccionPrimaria = reduccion(antes.energiaPrimariaM2, despues.energiaPrimariaM2)
        val reduccionDemanda = reduccion(antes.demandaClimatizacion, despues.demandaClimatizacion)
        val etiquetaAntes = AlgoritmoEnergetico.etiquetaPara(antes.energiaPrimariaM2)
        val etiquetaDespues = AlgoritmoEnergetico.etiquetaPara(despues.energiaPrimariaM2)
        // Tiene que MEJORAR hasta A o B: si ya era B y sigue en B, no cuenta
        val mejoraHastaAoB = etiquetaDespues in setOf("A", "B") && etiquetaDespues < etiquetaAntes
        return when {
            reduccionPrimaria >= REDUCCION_PRIMARIA_40 ->
                resultado(40, BASE_40, inversion, Motivo.ENERGIA_PRIMARIA, reduccionPrimaria)
            mejoraHastaAoB ->
                resultado(40, BASE_40, inversion, Motivo.ETIQUETA, reduccionPrimaria)
            reduccionDemanda >= REDUCCION_DEMANDA_20 ->
                resultado(20, BASE_20, inversion, Motivo.DEMANDA, reduccionDemanda)
            else -> null
        }
    }

    private fun reduccion(antes: Double, despues: Double) = if (antes > 0) 1.0 - despues / antes else 0.0

    private fun resultado(porcentaje: Int, base: Double, inversion: Double, motivo: Motivo, reduccion: Double) =
        Resultado(
            porcentaje = porcentaje,
            baseMaxima = base,
            importe = Math.round(porcentaje / 100.0 * minOf(inversion, base)).toDouble(),
            motivo = motivo,
            reduccionPct = (reduccion * 100).toInt()
        )
}
