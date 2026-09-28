package com.example.zerohaus.Repositorios

import com.example.zerohaus.Util.esFalloDeRed
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException

/**
 * Consejo individual generado por la IA.
 */
data class ConsejoIA(
    val titulo: String,
    val detalle: String,
    val prioridad: String,   // alta | media | baja
    val coste: String        // bajo | medio | alto
)

data class SugerenciasIA(
    val resumen: String,
    val consejos: List<ConsejoIA>,
    val habitos: List<String>
)

/** Motivo de fallo, para mostrar el mensaje traducido en la UI. */
enum class ErrorIA { LIMITE_DIARIO, SIN_CONEXION, NO_DISPONIBLE }

class ErrorIAException(val tipo: ErrorIA) : Exception(tipo.name)

/**
 * Consejos personalizados del informe generados por Gemini en la Cloud
 * Function `generar_sugerencias_ia` (la app no lleva claves de IA). El
 * servidor cachea el resultado por idioma dentro del propio informe.
 */
class RepositorioIA {

    private val functions = FirebaseFunctions.getInstance("europe-west1")

    fun generarSugerencias(
        informeId: String,
        idioma: String,
        regenerar: Boolean,
        callback: (Result<SugerenciasIA>) -> Unit
    ) {
        functions.getHttpsCallable("generar_sugerencias_ia")
            .call(hashMapOf("informeId" to informeId, "idioma" to idioma, "regenerar" to regenerar))
            .addOnSuccessListener { r ->
                @Suppress("UNCHECKED_CAST")
                val m = r.getData() as? Map<String, Any?>
                if (m == null) {
                    callback(Result.failure(ErrorIAException(ErrorIA.NO_DISPONIBLE)))
                    return@addOnSuccessListener
                }
                callback(Result.success(parsear(m)))
            }
            .addOnFailureListener { e ->
                val tipo = when {
                    e.esFalloDeRed() -> ErrorIA.SIN_CONEXION
                    (e as? FirebaseFunctionsException)?.code ==
                        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> ErrorIA.LIMITE_DIARIO
                    else -> ErrorIA.NO_DISPONIBLE
                }
                callback(Result.failure(ErrorIAException(tipo)))
            }
    }

    companion object {
        /** También sirve para leer la caché `sugerenciasIA` guardada en el informe. */
        @Suppress("UNCHECKED_CAST")
        fun parsear(m: Map<String, Any?>): SugerenciasIA = SugerenciasIA(
            resumen = m["resumen"] as? String ?: "",
            consejos = (m["consejos"] as? List<Map<String, Any?>>).orEmpty().map {
                ConsejoIA(
                    titulo = it["titulo"] as? String ?: "",
                    detalle = it["detalle"] as? String ?: "",
                    prioridad = it["prioridad"] as? String ?: "media",
                    coste = it["coste"] as? String ?: "medio"
                )
            },
            habitos = (m["habitos"] as? List<String>).orEmpty()
        )
    }
}
