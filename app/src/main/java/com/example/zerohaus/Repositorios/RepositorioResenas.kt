package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Resena
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
class RepositorioResenas {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun obtenerResenas(tecnicoId: String, callback: (List<Resena>) -> Unit) {
        db.collection("resenas")
            .whereEqualTo("tecnicoId", tecnicoId)
            .get()
            .addOnSuccessListener { snap ->
                callback(
                    snap.documents
                        .mapNotNull { it.toObject(Resena::class.java) }
                        .sortedByDescending { it.fecha }
                )
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

    fun actualizarRatingTecnico(tecnicoId: String) {
        db.collection("resenas")
            .whereEqualTo("tecnicoId", tecnicoId)
            .get()
            .addOnSuccessListener { snap ->
                val resenas = snap.documents.mapNotNull { it.toObject(Resena::class.java) }
                val media = if (resenas.isEmpty()) 0.0
                            else Math.round(resenas.map { it.puntuacion }.average() * 10.0) / 10.0
                db.collection("tecnicos").document(tecnicoId)
                    .update(mapOf("rating" to media, "opiniones" to resenas.size))
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
