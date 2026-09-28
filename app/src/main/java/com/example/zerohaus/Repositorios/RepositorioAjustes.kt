
package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.AjustesUsuario
import com.example.zerohaus.Util.getOrTimeout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Lectura y guardado de los ajustes del usuario (`/ajustes/{uid}`).
 */
class RepositorioAjustes {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private fun uid() = auth.currentUser?.uid ?: ""

    fun obtenerAjustes(callback: (AjustesUsuario) -> Unit) {
        db.collection("ajustes").document(uid()).getOrTimeout { doc ->
            callback(doc?.toObject(AjustesUsuario::class.java) ?: AjustesUsuario(uid = uid()))
        }
    }

    // merge: el mismo documento guarda el token de notificaciones (tokenFCM),
    // que no forma parte de AjustesUsuario. Un set() sin merge lo borraba y
    // dejaban de llegar los push hasta volver a abrir la app.
    fun guardarAjustes(ajustes: AjustesUsuario, callback: (Result<Unit>) -> Unit) {
        db.collection("ajustes").document(uid()).set(ajustes.copy(uid = uid()), SetOptions.merge())
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e -> callback(Result.failure(Exception(e.message))) }
    }
}