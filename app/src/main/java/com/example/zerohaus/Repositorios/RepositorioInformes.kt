package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Modelos.Recomendacion
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

        // Algoritmo de cálculo energético simplificado
        val baseConsumo = vivienda.superficie * 1.2

        val factorVentanas = when (vivienda.tipoVentanas) {
            "Vidrio simple" -> 1.4
            "Doble acristalamiento" -> 1.0
            "Triple" -> 0.8
            else -> 1.2
        }
        val factorAislamiento = when (vivienda.aislamiento) {
            "Sin aislamiento" -> 1.5
            "Aislamiento parcial" -> 1.15
            "Aislamiento completo" -> 0.8
            else -> 1.2
        }
        val factorCalefaccion = when (vivienda.calefaccion) {
            "Caldera de gas" -> 1.2
            "Eléctrica" -> 1.4
            "Aerotermia" -> 0.7
            "Biomasa" -> 0.9
            else -> 1.1
        }
        val factorAcs = when (vivienda.acs) {
            "Gas" -> 1.1
            "Eléctrico" -> 1.3
            "Solar térmica" -> 0.6
            "Aerotermia" -> 0.7
            else -> 1.0
        }
        val factorAnio = when {
            vivienda.anioConstruccion >= 2020 -> 0.8
            vivienda.anioConstruccion >= 2006 -> 0.95
            vivienda.anioConstruccion >= 1980 -> 1.1
            else -> 1.3
        }

        val consumo = baseConsumo * factorVentanas * factorAislamiento *
                factorCalefaccion * factorAcs * factorAnio
        val emisiones = consumo * 0.22
        val coste = consumo * 0.15

        val etiqueta = when {
            consumo < 50 -> "A"
            consumo < 90 -> "B"
            consumo < 140 -> "C"
            consumo < 200 -> "D"
            consumo < 280 -> "E"
            consumo < 380 -> "F"
            else -> "G"
        }
        val estadoEficiencia = when (etiqueta) {
            "A" -> "Muy alta eficiencia"
            "B" -> "Alta eficiencia"
            "C" -> "Eficiencia buena"
            "D" -> "Eficiencia media"
            "E" -> "Eficiencia baja"
            "F" -> "Eficiencia muy baja"
            else -> "Eficiencia mínima"
        }

        // Generar recomendaciones según los datos
        val recs = mutableListOf<Recomendacion>()
        if (vivienda.tipoVentanas == "Vidrio simple") {
            recs.add(Recomendacion("Mejorar aislamiento de ventanas a doble acristalamiento", 25))
        }
        if (vivienda.calefaccion == "Caldera de gas" || vivienda.calefaccion == "Eléctrica") {
            recs.add(Recomendacion("Instalar aerotermia como sistema de calefacción", 20))
        }
        if (vivienda.aislamiento != "Aislamiento completo") {
            recs.add(Recomendacion("Completar el aislamiento térmico de la vivienda", 18))
        }
        if (vivienda.acs != "Solar térmica" && vivienda.acs != "Aerotermia") {
            recs.add(Recomendacion("Instalar solar térmica para ACS", 15))
        }
        recs.add(Recomendacion("Sustituir iluminación por LED", 10))

        val informe = InformeEnergetico(
            id = ref.id,
            viviendaId = vivienda.id,
            uid = uid(),
            nombreVivienda = vivienda.nombre,
            etiqueta = etiqueta,
            estadoEficiencia = estadoEficiencia,
            consumoEstimado = Math.round(consumo * 10.0) / 10.0,
            emisiones = Math.round(emisiones * 10.0) / 10.0,
            costeAnual = Math.round(coste * 10.0) / 10.0,
            recomendaciones = recs
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