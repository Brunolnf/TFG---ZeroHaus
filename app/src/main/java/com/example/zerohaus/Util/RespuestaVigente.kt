package com.example.zerohaus.Util

/**
 * Una respuesta asíncrona (consejos de IA guardados o recién generados) solo se
 * aplica si el informe y el idioma con los que se lanzó siguen siendo los abiertos;
 * si el usuario cambió de informe o de idioma entretanto, pertenece a otra pantalla.
 */
fun esRespuestaVigente(
    informeLanzado: String,
    idiomaLanzado: String,
    informeActual: String,
    idiomaActual: String
): Boolean = informeLanzado.isNotEmpty() && informeLanzado == informeActual && idiomaLanzado == idiomaActual
