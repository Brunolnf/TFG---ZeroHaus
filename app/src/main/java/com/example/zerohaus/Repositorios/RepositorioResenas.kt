package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Resena
import com.example.zerohaus.Util.getOrTimeout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
     * Publica una reseña vinculándola a UN proyecto completado sin valorar (regla:
     * exactamente una valoración por proyecto/solicitud "Completado"). Busca la
     * solicitud completada más antigua con este técnico que aún no tenga reseña, y
     * ata la nueva reseña a su `solicitudId`. Si no queda ninguna sin valorar, rechaza.
     */
    fun publicarResena(resena: Resena, callback: (Result<Unit>) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { callback(Result.failure(Exception("No autenticado"))); return }
        // 1) Reseñas que este usuario ya hizo a este técnico → proyectos ya valorados.
        db.collection("resenas")
            .whereEqualTo("tecnicoId", resena.tecnicoId)
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { resSnap ->
                val totalResenas = resSnap.size()
                val yaValorados = resSnap.documents
                    .mapNotNull { it.getString("solicitudId") }
                    .filter { it.isNotBlank() }
                    .toSet()
                // 2) Solicitudes completadas de este usuario con este técnico.
                db.collection("solicitudes")
                    .whereEqualTo("uidCliente", uid)
                    .get()
                    .addOnSuccessListener { solSnap ->
                        val completadas = solSnap.documents.filter { doc ->
                            doc.getString("tecnicoId") == resena.tecnicoId && doc.getString("estado") == "Completado"
                        }
                        // Red de seguridad por conteo (cubre reseñas antiguas sin solicitudId):
                        // el nº de reseñas nunca puede superar el de proyectos completados.
                        if (totalResenas >= completadas.size) {
                            callback(Result.failure(Exception("Ya has valorado todos tus proyectos completados con este técnico")))
                            return@addOnSuccessListener
                        }
                        // Proyecto completado más antiguo aún sin reseña.
                        val pendiente = completadas
                            .sortedBy { it.getLong("fechaCreacion") ?: 0L }
                            .firstOrNull { it.id !in yaValorados }
                        if (pendiente == null) {
                            callback(Result.failure(Exception("Ya has valorado todos tus proyectos completados con este técnico")))
                            return@addOnSuccessListener
                        }
                        val ref = db.collection("resenas").document()
                        val r = resena.copy(id = ref.id, uid = uid, solicitudId = pendiente.id)
                        ref.set(r)
                            .addOnSuccessListener {
                                recalcularRatingTecnico(resena.tecnicoId)
                                callback(Result.success(Unit))
                            }
                            .addOnFailureListener { e ->
                                callback(Result.failure(Exception(e.message ?: "Error publicando reseña")))
                            }
                    }
                    .addOnFailureListener { e ->
                        callback(Result.failure(Exception(e.message ?: "Error verificando proyectos")))
                    }
            }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error verificando reseñas")))
            }
    }

    /**
     * Borra reseñas duplicadas del USUARIO ACTUAL sobre este técnico (solo las suyas; no
     * puede tocar las de otros). Duplicado = misma solicitud valorada dos veces, o —para
     * reseñas antiguas sin solicitudId— mismo contenido creado con < 60 s de diferencia.
     * Conserva la más antigua. Recalcula el rating si borró algo.
     */
    fun limpiarResenasDuplicadas(tecnicoId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("resenas")
            .whereEqualTo("tecnicoId", tecnicoId)
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { snap ->
                val mias = snap.documents.mapNotNull { it.toObject(Resena::class.java) }.sortedBy { it.fecha }
                val conservadas = mutableListOf<Resena>()
                var borreAlgo = false
                mias.forEach { r ->
                    val esDup = conservadas.any { prev ->
                        (r.solicitudId.isNotBlank() && prev.solicitudId == r.solicitudId) ||
                            (r.solicitudId.isBlank() && prev.solicitudId.isBlank() &&
                                prev.puntuacion == r.puntuacion && prev.comentario == r.comentario &&
                                kotlin.math.abs(prev.fecha - r.fecha) <= 60_000L)
                    }
                    if (esDup) {
                        if (r.id.isNotBlank()) { db.collection("resenas").document(r.id).delete(); borreAlgo = true }
                    } else {
                        conservadas.add(r)
                    }
                }
                if (borreAlgo) recalcularRatingTecnico(tecnicoId)
            }
    }

    private fun recalcularRatingTecnico(tecnicoId: String) {
        db.collection("resenas")
            .whereEqualTo("tecnicoId", tecnicoId)
            .get()
            .addOnSuccessListener { snap ->
                val resenas = snap.documents.mapNotNull { it.toObject(Resena::class.java) }
                val nuevoRating = if (resenas.isEmpty()) 0.0
                    else Math.round(resenas.map { it.puntuacion }.average() * 10.0) / 10.0
                db.collection("tecnicos").document(tecnicoId).update(
                    mapOf("rating" to nuevoRating, "opiniones" to resenas.size)
                )
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

    fun contarResenas(tecnicoId: String, callback: (Int) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { callback(0); return }
        db.collection("resenas")
            .whereEqualTo("tecnicoId", tecnicoId)
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { snap -> callback(snap.size()) }
            .addOnFailureListener { callback(0) }
    }
}
