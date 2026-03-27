package com.example.zerohaus.Repositorios


import com.example.zerohaus.Modelos.Vivienda
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RepositorioViviendas {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    fun guardarVivienda(vivienda: Vivienda, callback: (Result<String>) -> Unit) {
        val ref = if (vivienda.id.isNotEmpty()) {
            db.collection("viviendas").document(vivienda.id)
        } else {
            db.collection("viviendas").document()
        }
        val v = vivienda.copy(id = ref.id, uid = uid())
        ref.set(v)
            .addOnSuccessListener { callback(Result.success(ref.id)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error guardando vivienda")))
            }
    }

    fun obtenerViviendas(callback: (List<Vivienda>) -> Unit) {
        db.collection("viviendas")
            .whereEqualTo("uid", uid())
            .get()
            .addOnSuccessListener { snap ->
                val lista = snap.documents.mapNotNull { it.toObject(Vivienda::class.java) }
                callback(lista)
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun obtenerVivienda(id: String, callback: (Vivienda?) -> Unit) {
        db.collection("viviendas").document(id).get()
            .addOnSuccessListener { doc -> callback(doc.toObject(Vivienda::class.java)) }
            .addOnFailureListener { callback(null) }
    }

    fun eliminarVivienda(id: String, callback: (Result<Unit>) -> Unit) {
        db.collection("viviendas").document(id).delete()
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error eliminando")))
            }
    }
}