package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Resena
import com.example.zerohaus.Util.getOrTimeout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Valoraciones de profesionales: lectura, publicación (una por cliente) y comprobación.
 */
class RepositorioResenas {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun obtenerResenas(tecnicoId: String, callback: (List<Resena>) -> Unit) {
        db.collection("resenas")
            .whereEqualTo("tecnicoId", tecnicoId)
            .getOrTimeout { snap ->
                callback(snap?.documents
                    ?.mapNotNull { it.toObject(Resena::class.java) }
                    ?.sortedByDescending { it.fecha } ?: emptyList())
            }
    }

    /**
     * Publica una reseña. Máximo UNA reseña por profesional gracias al docId
     * determinista "{uid}_{tecnicoId}". Volver a valorar sustituye la anterior.
     */
    fun publicarResena(resena: Resena, callback: (Result<Unit>) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { callback(Result.failure(Exception("No autenticado"))); return }

        val docIdResena = "${uid}_${resena.tecnicoId}"
        db.collection("resenas")
            .whereEqualTo("tecnicoId", resena.tecnicoId)
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { previas ->
                previas.documents
                    .filter { it.id != docIdResena }
                    .forEach { it.reference.delete() }
                db.collection("resenas").document(docIdResena)
                    .set(resena.copy(id = docIdResena, uid = uid))
                    .addOnSuccessListener { callback(Result.success(Unit)) }
                    .addOnFailureListener { e ->
                        callback(Result.failure(Exception(e.message ?: "Error publicando reseña")))
                    }
            }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error verificando reseñas")))
            }
    }
}
