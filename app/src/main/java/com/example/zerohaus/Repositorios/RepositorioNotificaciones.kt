package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Notificacion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class RepositorioNotificaciones {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    fun escucharNotificaciones(callback: (List<Notificacion>) -> Unit): ListenerRegistration {
        return db.collection("notificaciones")
            .whereEqualTo("uid", uid())
            .addSnapshotListener { snap, _ ->
                val todas = snap?.documents
                    ?.mapNotNull { it.toObject(Notificacion::class.java) }
                    ?: emptyList()

                // Limpieza de duplicados: dos notificaciones con el MISMO titulo+detalle+tipo
                // creadas con < VENTANA_DEDUP_MS de diferencia son la misma duplicada (Cloud
                // Function disparada dos veces, push repetido…). Conservamos la más antigua y
                // BORRAMOS las copias de Firestore. Tras el borrado el listener vuelve a saltar
                // ya sin duplicados (converge, no hace bucle).
                val conservadas = mutableListOf<Notificacion>()
                todas.sortedBy { it.fecha }.forEach { n ->
                    val esDup = conservadas.any { prev ->
                        prev.titulo == n.titulo && prev.detalle == n.detalle && prev.tipo == n.tipo &&
                            kotlin.math.abs(prev.fecha - n.fecha) <= VENTANA_DEDUP_MS
                    }
                    if (esDup) {
                        if (n.id.isNotBlank()) db.collection("notificaciones").document(n.id).delete()
                    } else {
                        conservadas.add(n)
                    }
                }
                callback(conservadas.sortedByDescending { it.fecha })
            }
    }

    /** Borra una notificación por id (uso: limpieza de duplicados). */
    fun eliminarNotificacion(notificacionId: String) {
        if (notificacionId.isBlank()) return
        db.collection("notificaciones").document(notificacionId).delete()
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

    companion object {
        /** Ventana para considerar dos notificaciones idénticas como la misma duplicada. */
        private const val VENTANA_DEDUP_MS = 30_000L

        /**
         * Crea una notificación con campos básicos para [uid]. Helper compartido
         * para que repositorios distintos no dupliquen el mismo `hashMapOf`.
         */
        fun crearRapida(uid: String, titulo: String, detalle: String, tipo: String) {
            val db = FirebaseFirestore.getInstance()
            val ref = db.collection("notificaciones").document()
            ref.set(
                hashMapOf(
                    "id" to ref.id, "uid" to uid,
                    "titulo" to titulo, "detalle" to detalle,
                    "fecha" to System.currentTimeMillis(),
                    "leida" to false, "tipo" to tipo
                )
            )
        }
    }
}
