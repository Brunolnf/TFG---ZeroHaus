package com.example.zerohaus.Modelos

import java.text.Normalizer

/**
 * Catálogo único de especialidades de los profesionales.
 *
 * Los valores canónicos se guardan en español en `/tecnicos/{id}.especialidades`
 * y con ellos se filtra el directorio; en la interfaz se traducen al mostrarlos
 * ([com.example.zerohaus.Util.TextosEnergia.especialidad]).
 *
 * Los perfiles anteriores a la 2.2 se escribían a mano ("aislamiento, placas
 * solares…"): [normalizar] los interpreta con sinónimos para que también
 * aparezcan al filtrar.
 */
object Especialidades {

    const val AISLAMIENTO = "Aislamiento"
    const val VENTANAS = "Ventanas"
    const val CALEFACCION = "Calefacción"
    const val FOTOVOLTAICA = "Fotovoltaica"
    const val AEROTERMIA = "Aerotermia"
    const val AUDITORIAS = "Auditorías"
    const val REHABILITACION = "Rehabilitación"
    const val BIOMASA = "Biomasa"
    const val CERTIFICACION = "Certificación"
    const val CONSULTORIA = "Consultoría"

    /** Orden en el que se muestran. */
    val TODAS = listOf(
        AISLAMIENTO, VENTANAS, CALEFACCION, FOTOVOLTAICA, AEROTERMIA,
        AUDITORIAS, REHABILITACION, BIOMASA, CERTIFICACION, CONSULTORIA
    )

    // Raíz (sin tildes, minúsculas) → especialidad. El orden importa: lo más
    // específico primero ("solar termica" antes que "solar").
    private val SINONIMOS: List<Pair<String, String>> = listOf(
        "aislam" to AISLAMIENTO, "aislant" to AISLAMIENTO, "sate" to AISLAMIENTO,
        "fachada" to AISLAMIENTO, "cubierta" to AISLAMIENTO, "insufla" to AISLAMIENTO,
        "ventan" to VENTANAS, "carpinter" to VENTANAS, "acristal" to VENTANAS, "cerramiento" to VENTANAS,
        "aerotermi" to AEROTERMIA, "bomba de calor" to AEROTERMIA, "bombas de calor" to AEROTERMIA,
        "geotermi" to AEROTERMIA,
        "solar termic" to CALEFACCION, "calefacc" to CALEFACCION, "caldera" to CALEFACCION,
        "climatiz" to CALEFACCION, "agua caliente" to CALEFACCION, "suelo radiante" to CALEFACCION,
        "fotovolt" to FOTOVOLTAICA, "placas solares" to FOTOVOLTAICA, "paneles solares" to FOTOVOLTAICA,
        "autoconsumo" to FOTOVOLTAICA, "solar" to FOTOVOLTAICA,
        "auditor" to AUDITORIAS,
        "rehabilit" to REHABILITACION, "reforma" to REHABILITACION, "obra" to REHABILITACION,
        "biomasa" to BIOMASA, "pellet" to BIOMASA,
        "certific" to CERTIFICACION, "cee" to CERTIFICACION,
        "consultor" to CONSULTORIA, "asesor" to CONSULTORIA,
    )

    private fun sinTildes(s: String) = Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

    /** Especialidad canónica de un texto (canónico o escrito a mano), o null. */
    fun normalizar(texto: String): String? {
        val limpio = sinTildes(texto)
        if (limpio.isEmpty()) return null
        TODAS.firstOrNull { sinTildes(it) == limpio }?.let { return it }
        return SINONIMOS.firstOrNull { (raiz, _) -> limpio.contains(raiz) }?.second
    }

    /** Especialidades canónicas de un perfil, en el orden del catálogo. */
    fun canonicas(lista: List<String>): List<String> {
        val encontradas = lista.mapNotNull { normalizar(it) }.toSet()
        return TODAS.filter { it in encontradas }
    }

    /**
     * Especialidad de los profesionales que hacen una mejora del informe
     * (por el título que guarda AlgoritmoEnergetico), o null si no hace falta
     * un profesional (p. ej. cambiar bombillas).
     */
    fun paraRecomendacion(titulo: String): String? {
        val t = sinTildes(titulo)
        return when {
            "ventana" in t -> VENTANAS
            "aislamiento" in t -> AISLAMIENTO
            "aerotermia" in t -> AEROTERMIA
            "inverter" in t -> AEROTERMIA
            "solar termica" in t -> CALEFACCION
            "fotovoltaic" in t -> FOTOVOLTAICA
            else -> null
        }
    }
}
