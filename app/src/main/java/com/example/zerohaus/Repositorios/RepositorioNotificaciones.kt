package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Notificacion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class RepositorioNotificaciones {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    fun obtenerNotificaciones(callback: (List<Notificacion>) -> Unit) {
        db.collection("notificaciones")
            .whereEqualTo("uid", uid())
            .orderBy("fecha", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.mapNotNull { it.toObject(Notificacion::class.java) })
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun marcarTodasLeidas(callback: (Result<Unit>) -> Unit) {
        db.collection("notificaciones")
            .whereEqualTo("uid", uid())
            .whereEqualTo("leida", false)
            .get()
            .addOnSuccessListener { snap ->
                val batch = db.batch()
                snap.documents.forEach { doc ->
                    batch.update(doc.reference, "leida", true)
                }
                batch.commit()
                    .addOnSuccessListener { callback(Result.success(Unit)) }
                    .addOnFailureListener { e ->
                        callback(Result.failure(Exception(e.message)))
                    }
            }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message)))
            }
    }

    fun marcarLeida(notificacionId: String, callback: (Result<Unit>) -> Unit) {
        db.collection("notificaciones").document(notificacionId)
            .update("leida", true)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message)))
            }
    }

    fun crearNotificacion(notificacion: Notificacion, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("notificaciones").document()
        val n = notificacion.copy(id = ref.id, uid = uid())
        ref.set(n)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message)))
            }
    }
}