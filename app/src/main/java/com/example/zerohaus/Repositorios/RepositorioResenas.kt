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
        val ref = db.collection("resenas").document()
        val r = resena.copy(id = ref.id, uid = auth.currentUser?.uid ?: "")
        ref.set(r)
            .addOnSuccessListener {
                actualizarRatingTecnico(resena.tecnicoId)
                callback(Result.success(Unit))
            }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error publicando reseña")))
            }
    }

    private fun actualizarRatingTecnico(tecnicoId: String) {
        db.collection("resenas")
            .whereEqualTo("tecnicoId", tecnicoId)
            .get()
            .addOnSuccessListener { snap ->
                val resenas = snap.documents.mapNotNull { it.toObject(Resena::class.java) }
                if (resenas.isNotEmpty()) {
                    val media = resenas.map { it.puntuacion }.average()
                    val mediaRedondeada = Math.round(media * 10.0) / 10.0
                    db.collection("tecnicos").document(tecnicoId)
                        .update(mapOf("rating" to mediaRedondeada, "opiniones" to resenas.size))
                }
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
