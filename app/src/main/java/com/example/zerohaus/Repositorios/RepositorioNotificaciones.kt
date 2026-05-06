package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Notificacion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class RepositorioNotificaciones {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    fun obtenerNotificaciones(callback: (List<Notificacion>) -> Unit) {
        db.collection("notificaciones")
            .whereEqualTo("uid", uid())
            .get()
            .addOnSuccessListener { snap ->
                callback(
                    snap.documents
                        .mapNotNull { it.toObject(Notificacion::class.java) }
                        .sortedByDescending { it.fecha }
                )
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun escucharNotificaciones(callback: (List<Notificacion>) -> Unit): ListenerRegistration {
        return db.collection("notificaciones")
            .whereEqualTo("uid", uid())
            .addSnapshotListener { snap, _ ->
                val lista = snap?.documents
                    ?.mapNotNull { it.toObject(Notificacion::class.java) }
                    ?.sortedByDescending { it.fecha }
                    ?: emptyList()
                callback(lista)
            }
    }

    fun marcarTodasLeidas(callback: (Result<Unit>) -> Unit) {
        db.collection("notificaciones")
            .whereEqualTo("uid", uid())
            .get()
            .addOnSuccessListener { snap ->
                val noLeidas = snap.documents.filter { it.getBoolean("leida") == false }
                if (noLeidas.isEmpty()) { callback(Result.success(Unit)); return@addOnSuccessListener }
                val batch = db.batch()
                noLeidas.forEach { doc -> batch.update(doc.reference, "leida", true) }
                batch.commit()
                    .addOnSuccessListener { callback(Result.success(Unit)) }
                    .addOnFailureListener { e -> callback(Result.failure(Exception(e.message))) }
            }
            .addOnFailureListener { e -> callback(Result.failure(Exception(e.message))) }
    }

    fun marcarLeida(notificacionId: String, callback: (Result<Unit>) -> Unit) {
        db.collection("notificaciones").document(notificacionId)
            .update("leida", true)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e -> callback(Result.failure(Exception(e.message))) }
    }

    fun crearNotificacion(notificacion: Notificacion, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("notificaciones").document()
        val n = notificacion.copy(id = ref.id, uid = uid())
        ref.set(n)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e -> callback(Result.failure(Exception(e.message))) }
    }

    fun crearNotificacionParaUid(uidDestino: String, notificacion: Notificacion) {
        val ref = db.collection("notificaciones").document()
        ref.set(notificacion.copy(id = ref.id, uid = uidDestino))
    }
}
