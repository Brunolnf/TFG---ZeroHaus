package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.SolicitudPresupuesto
import com.example.zerohaus.Modelos.Tecnico
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class RepositorioTecnicos {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    fun obtenerTecnicos(callback: (List<Tecnico>) -> Unit) {
        db.collection("tecnicos")
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.mapNotNull { it.toObject(Tecnico::class.java) })
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun obtenerTecnico(id: String, callback: (Tecnico?) -> Unit) {
        db.collection("tecnicos").document(id).get()
            .addOnSuccessListener { doc -> callback(doc.toObject(Tecnico::class.java)) }
            .addOnFailureListener { callback(null) }
    }

    fun obtenerRanking(callback: (List<Tecnico>) -> Unit) {
        db.collection("tecnicos")
            .orderBy("rating", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.mapNotNull { it.toObject(Tecnico::class.java) })
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun solicitarPresupuesto(
        solicitud: SolicitudPresupuesto,
        callback: (Result<Unit>) -> Unit
    ) {
        val ref = db.collection("solicitudes").document()
        val s = solicitud.copy(id = ref.id, uidCliente = uid())
        ref.set(s)
            .addOnSuccessListener {
                // Crear notificación para el técnico
                val notifRef = db.collection("notificaciones").document()
                val notif = hashMapOf(
                    "id" to notifRef.id,
                    "uid" to solicitud.tecnicoId,
                    "titulo" to "Nueva solicitud de presupuesto",
                    "detalle" to "Has recibido una solicitud de ${solicitud.nombreCliente}",
                    "fecha" to System.currentTimeMillis(),
                    "leida" to false,
                    "tipo" to "presupuesto"
                )
                notifRef.set(notif)
                callback(Result.success(Unit))
            }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error enviando solicitud")))
            }
    }

    /**
     * Registrar un usuario como técnico en la colección de técnicos.
     * Se llama cuando un técnico completa su perfil.
     */
    fun registrarTecnico(tecnico: Tecnico, callback: (Result<Unit>) -> Unit) {
        val ref = if (tecnico.id.isNotEmpty()) {
            db.collection("tecnicos").document(tecnico.id)
        } else {
            db.collection("tecnicos").document()
        }
        val t = tecnico.copy(id = ref.id, uid = uid())
        ref.set(t)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error registrando técnico")))
            }
    }
    fun obtenerMisSolicitudes(callback: (List<SolicitudPresupuesto>) -> Unit) {
        db.collection("solicitudes")
            .whereEqualTo("uidCliente", uid())
            .orderBy("fechaCreacion", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.mapNotNull { it.toObject(SolicitudPresupuesto::class.java) })
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun responderPresupuesto(solicitudId: String, precio: Double, respuesta: String, callback: (Result<Unit>) -> Unit) {
        db.collection("solicitudes").document(solicitudId)
            .update(mapOf("estado" to "Presupuestado", "precioPresupuesto" to precio, "respuestaTecnico" to respuesta, "fechaRespuesta" to System.currentTimeMillis()))
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error respondiendo"))) }
    }

    fun aceptarPresupuesto(solicitudId: String, callback: (Result<Unit>) -> Unit) {
        db.collection("solicitudes").document(solicitudId)
            .update("estado", "Aceptado")
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error aceptando"))) }
    }

    fun rechazarPresupuesto(solicitudId: String, callback: (Result<Unit>) -> Unit) {
        db.collection("solicitudes").document(solicitudId)
            .update("estado", "Rechazado")
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error rechazando"))) }
    }
}