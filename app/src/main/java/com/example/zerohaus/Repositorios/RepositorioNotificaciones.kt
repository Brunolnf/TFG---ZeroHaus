package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Notificacion
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/**
 * Historial de notificaciones dentro de la app (`/notificaciones`): escucha
 * en tiempo real, marca como leídas y elimina duplicados.
 */
class RepositorioNotificaciones {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    /**
     * Las [LIMITE] más recientes (índice uid + fecha). Antes se escuchaban
     * todas, y cada mensaje de chat crea una: con el uso la lista crecía sin fin
     * y se descargaba entera en cada cambio.
     */
    fun escucharNotificaciones(callback: (List<Notificacion>) -> Unit): ListenerRegistration {
        return db.collection("notificaciones")
            .whereEqualTo("uid", uid())
            .orderBy("fecha", Query.Direction.DESCENDING)
            .limit(LIMITE)
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

    fun marcarTodasLeidas(callback: (Result<Unit>) -> Unit) {
        db.collection("notificaciones")
            .whereEqualTo("uid", uid())
            .whereEqualTo("leida", false)
            .get()
            .addOnSuccessListener { snap ->
                val noLeidas = snap.documents
                if (noLeidas.isEmpty()) { callback(Result.success(Unit)); return@addOnSuccessListener }
                // Un lote admite 500 escrituras: con más no leídas fallaba entero
                val lotes = noLeidas.chunked(LOTE).map { trozo ->
                    db.batch().apply { trozo.forEach { update(it.reference, "leida", true) } }.commit()
                }
                Tasks.whenAll(lotes)
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

    companion object {
        /** Ventana para considerar dos notificaciones idénticas como la misma duplicada. */
        private const val VENTANA_DEDUP_MS = 30_000L
        /** Notificaciones que se muestran (las más recientes). */
        private const val LIMITE = 50L
        private const val LOTE = 450
    }
}
