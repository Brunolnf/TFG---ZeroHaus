package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Suscripcion
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Util.Diagnostico
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException

/**
 * Capa de monetización: suscripciones de profesionales.
 *
 * Este repositorio NUNCA escribe suscripciones ni planActivo directamente —
 * las rules lo prohíben. Todo pasa por la Cloud Function `activar_suscripcion`.
 */
class RepositorioMonetizacion {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val functions = FirebaseFunctions.getInstance(REGION)

    private fun uid() = auth.currentUser?.uid ?: ""

    companion object {
        private const val REGION = "europe-west1"
    }

    /** Perfil profesional propio en tiempo real (plan activo al instante). */
    fun escucharMiPerfilProfesional(callback: (Tecnico?) -> Unit): ListenerRegistration {
        return db.collection("tecnicos").document(uid())
            .addSnapshotListener { snap, _ ->
                val t = snap?.toObject(Tecnico::class.java)
                callback(if (t != null && t.id.isBlank()) t.copy(id = snap.id) else t)
            }
    }

    /** Historial de suscripciones del usuario actual. */
    fun escucharMisSuscripciones(callback: (List<Suscripcion>) -> Unit): ListenerRegistration {
        return db.collection("suscripciones")
            .whereEqualTo("uid", uid())
            .addSnapshotListener { snap, _ ->
                callback(snap?.documents
                    ?.mapNotNull { it.toObject(Suscripcion::class.java) }
                    ?.sortedByDescending { it.fechaInicio } ?: emptyList())
            }
    }

    /**
     * Activa una suscripción contra el servidor. Idempotente.
     */
    fun activarSuscripcion(
        planId: String,
        purchaseToken: String,
        callback: (Result<Unit>) -> Unit
    ) {
        val datos = hashMapOf<String, Any>(
            "planId" to planId,
            "purchaseToken" to purchaseToken
        )

        functions.getHttpsCallable("activar_suscripcion")
            .call(datos)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                Diagnostico.errorDeFuncion("activar_suscripcion", e)
                val msg = if (e is FirebaseFunctionsException) e.message
                else "No se pudo activar la suscripción. Inténtalo de nuevo."
                callback(Result.failure(Exception(msg ?: "Error activando la suscripción")))
            }
    }
}
