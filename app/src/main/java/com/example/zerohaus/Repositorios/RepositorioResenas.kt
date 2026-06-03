package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Resena
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
class RepositorioResenas {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun obtenerResenas(tecnicoId: String, callback: (List<Resena>) -> Unit) {
        db.collection("resenas")
            .whereEqualTo("tecnicoId", tecnicoId)
            .orderBy("fecha", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.mapNotNull { it.toObject(Resena::class.java) })
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun publicarResena(resena: Resena, callback: (Result<Unit>) -> Unit) {
        // El rating del técnico lo recalcula la Cloud Function `on_resena_changed`
        // de forma transaccional cuando se crea/edita/borra una reseña. No lo
        // hacemos desde el cliente para evitar race conditions (la query inmediata
        // tras crear el doc puede no incluirlo por consistencia eventual).
        val ref = db.collection("resenas").document()
        val r = resena.copy(id = ref.id, uid = auth.currentUser?.uid ?: "")
        ref.set(r)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error publicando reseña")))
            }
    }

    fun yaValorado(tecnicoId: String, callback: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { callback(false); return }
        db.collection("resenas")
            .whereEqualTo("tecnicoId", tecnicoId)
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { snap -> callback(!snap.isEmpty) }
            .addOnFailureListener { callback(false) }
    }
}
