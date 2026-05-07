package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Modelos.Vivienda
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

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
            emisiones = resultado.emisiones,
            costeAnual = resultado.costeAnual,
            recomendaciones = resultado.recomendaciones
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
            .orderBy("fechaGeneracion", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.firstOrNull()?.toObject(InformeEnergetico::class.java))
            }
            .addOnFailureListener { callback(null) }
    }

    fun obtenerInformes(callback: (List<InformeEnergetico>) -> Unit) {
        db.collection("informes")
            .whereEqualTo("uid", uid())
            .orderBy("fechaGeneracion", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.mapNotNull { it.toObject(InformeEnergetico::class.java) })
            }
            .addOnFailureListener { callback(emptyList()) }
    }
}