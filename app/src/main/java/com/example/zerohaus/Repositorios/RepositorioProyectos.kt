package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Proyecto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RepositorioProyectos {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    fun obtenerProyectos(callback: (List<Proyecto>) -> Unit) {
        db.collection("proyectos")
            .whereEqualTo("uid", uid())
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.mapNotNull { it.toObject(Proyecto::class.java) })
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun crearProyecto(proyecto: Proyecto, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("proyectos").document()
        val p = proyecto.copy(id = ref.id, uid = uid())
        ref.set(p)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error creando proyecto")))
            }
    }

    fun actualizarTarea(
        proyectoId: String,
        tareaIndex: Int,
        completada: Boolean,
        callback: (Result<Unit>) -> Unit
    ) {
        val ref = db.collection("proyectos").document(proyectoId)
        ref.get().addOnSuccessListener { doc ->
            val proyecto = doc.toObject(Proyecto::class.java) ?: return@addOnSuccessListener
            val tareasActualizadas = proyecto.tareas.toMutableList()
            if (tareaIndex in tareasActualizadas.indices) {
                tareasActualizadas[tareaIndex] = tareasActualizadas[tareaIndex].copy(completada = completada)
            }
            // Recalcular progreso
            val completadas = tareasActualizadas.count { it.completada }
            val total = tareasActualizadas.size
            val progreso = if (total > 0) (completadas * 100) / total else 0
            val estado = when {
                progreso == 100 -> "Finalizado"
                progreso > 0 -> "En curso"
                else -> "Pendiente"
            }
            ref.update(
                mapOf(
                    "tareas" to tareasActualizadas.map { mapOf("nombre" to it.nombre, "completada" to it.completada) },
                    "progreso" to progreso,
                    "estado" to estado
                )
            )
                .addOnSuccessListener { callback(Result.success(Unit)) }
                .addOnFailureListener { e ->
                    callback(Result.failure(Exception(e.message ?: "Error actualizando tarea")))
                }
        }
    }
}