package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Modelos.Vivienda
import com.example.zerohaus.Util.getOrTimeout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Informes energéticos: los genera con el algoritmo, los guarda en
 * `/informes` y lee el historial y los consejos de IA ya generados.
 */
class RepositorioInformes {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    /**
     * Genera un informe energético basado en los datos de la vivienda.
     * Usa un algoritmo simplificado de cálculo energético.
     */
    fun generarInforme(vivienda: Vivienda, callback: (Result<InformeEnergetico>) -> Unit) {
        val ref = db.collection("informes").document()
        val resultado = AlgoritmoEnergetico.calcular(vivienda)

        val informe = InformeEnergetico(
            id = ref.id,
            viviendaId = vivienda.id,
            uid = uid(),
            nombreVivienda = vivienda.nombre,
            etiqueta = resultado.etiqueta,
            estadoEficiencia = resultado.estadoEficiencia,
            consumoEstimado = resultado.consumoEstimado,
            consumoPorM2 = resultado.consumoPorM2,
            energiaPrimariaM2 = resultado.energiaPrimariaM2,
            emisiones = resultado.emisiones,
            costeAnual = resultado.costeAnual,
            recomendaciones = resultado.recomendaciones,
            precioLuz = resultado.precioLuz,
            consumoLuzEstimado = resultado.consumoLuzEstimado,
            consumoLuzFactura = vivienda.consumoLuzFacturaKwh
        )

        ref.set(informe)
            .addOnSuccessListener { callback(Result.success(informe)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error generando informe")))
            }
    }

    fun obtenerUltimoInforme(callback: (InformeEnergetico?) -> Unit) {
        db.collection("informes")
            .whereEqualTo("uid", uid())
            .getOrTimeout { snap ->
                val ultimo = snap?.documents
                    ?.mapNotNull { it.toObject(InformeEnergetico::class.java) }
                    ?.maxByOrNull { it.fechaGeneracion }
                callback(ultimo)
            }
    }

    fun obtenerInformes(
        onSuccess: (List<InformeEnergetico>) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ) {
        db.collection("informes")
            .whereEqualTo("uid", uid())
            .getOrTimeout { snap ->
                onSuccess(snap?.documents
                    ?.mapNotNull { it.toObject(InformeEnergetico::class.java) }
                    ?.sortedByDescending { it.fechaGeneracion } ?: emptyList())
            }
    }

    // Sobrecarga de compatibilidad para llamadas que no necesitan el error
    fun obtenerInformes(callback: (List<InformeEnergetico>) -> Unit) =
        obtenerInformes(onSuccess = callback, onError = null)

    /** Consejos de IA ya guardados en el informe, solo si están en [idioma]. */
    fun obtenerSugerenciasGuardadas(informeId: String, idioma: String, callback: (SugerenciasIA?) -> Unit) {
        db.collection("informes").document(informeId).getOrTimeout { snap ->
            @Suppress("UNCHECKED_CAST")
            val m = snap?.get("sugerenciasIA") as? Map<String, Any?>
            callback(m?.takeIf { it["idioma"] == idioma }?.let { RepositorioIA.parsear(it) })
        }
    }

    fun eliminarInforme(informeId: String, callback: (Boolean) -> Unit) {
        db.collection("informes").document(informeId)
            .delete()
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }
}
