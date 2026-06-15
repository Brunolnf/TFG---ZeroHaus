
package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.AjustesUsuario
import com.example.zerohaus.Util.getOrTimeout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RepositorioAjustes {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private fun uid() = auth.currentUser?.uid ?: ""

    fun obtenerAjustes(callback: (AjustesUsuario) -> Unit) {
        db.collection("ajustes").document(uid()).getOrTimeout { doc ->
            callback(doc?.toObject(AjustesUsuario::class.java) ?: AjustesUsuario(uid = uid()))
        }
    }

    fun guardarAjustes(ajustes: AjustesUsuario, callback: (Result<Unit>) -> Unit) {
        db.collection("ajustes").document(uid()).set(ajustes.copy(uid = uid()))
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e -> callback(Result.failure(Exception(e.message))) }
    }
}