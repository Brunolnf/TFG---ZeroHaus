package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.SolicitudPresupuesto
import com.example.zerohaus.Modelos.Tecnico
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RepositorioTecnicos {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    fun obtenerTecnicos(callback: (List<Tecnico>) -> Unit) {
        db.collection("tecnicos")
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.mapNotNull { doc ->
                    doc.toObject(Tecnico::class.java)?.let { t ->
                        if (t.id.isBlank()) t.copy(id = doc.id) else t
                    }
                })
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun obtenerTecnico(id: String, callback: (Tecnico?) -> Unit) {
        db.collection("tecnicos").document(id).get()
            .addOnSuccessListener { doc ->
                val t = doc.toObject(Tecnico::class.java)
                callback(if (t != null && t.id.isBlank()) t.copy(id = doc.id) else t)
            }
            .addOnFailureListener { callback(null) }
    }

    fun obtenerRanking(callback: (List<Tecnico>) -> Unit) {
        db.collection("tecnicos")
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.mapNotNull { doc ->
                    doc.toObject(Tecnico::class.java)?.let { t ->
                        if (t.id.isBlank()) t.copy(id = doc.id) else t
                    }
                })
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun solicitarPresupuesto(solicitud: SolicitudPresupuesto, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("solicitudes").document()
        val s = solicitud.copy(id = ref.id, uidCliente = uid())
        ref.set(s)
            .addOnSuccessListener {
                // Notificación de confirmación para el cliente
                crearNotif(
                    uid = uid(),
                    titulo = "Solicitud enviada a ${solicitud.tecnicoNombre}",
                    detalle = "Tu solicitud de presupuesto ha sido enviada. Recibirás una notificación cuando el técnico responda.",
                    tipo = "presupuesto"
                )
                // Notificación para el técnico (si tiene uid propio)
                if (solicitud.tecnicoId.isNotEmpty()) {
                    crearNotif(
                        uid = solicitud.tecnicoId,
                        titulo = "Nueva solicitud de presupuesto",
                        detalle = "${solicitud.nombreCliente} te ha enviado una solicitud de presupuesto.",
                        tipo = "presupuesto"
                    )
                }
                callback(Result.success(Unit))
            }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error enviando solicitud")))
            }
    }

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

    fun obtenerSolicitudesRecibidas(callback: (List<SolicitudPresupuesto>) -> Unit) {
        db.collection("tecnicos")
            .whereEqualTo("uid", uid())
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val tecnicoId = snap.documents.firstOrNull()?.id
                if (tecnicoId == null) { callback(emptyList()); return@addOnSuccessListener }
                db.collection("solicitudes")
                    .whereEqualTo("tecnicoId", tecnicoId)
                    .get()
                    .addOnSuccessListener { s ->
                        callback(
                            s.documents.mapNotNull { it.toObject(SolicitudPresupuesto::class.java) }
                                .sortedByDescending { it.fechaCreacion }
                        )
                    }
                    .addOnFailureListener { callback(emptyList()) }
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun obtenerMisSolicitudes(callback: (List<SolicitudPresupuesto>) -> Unit) {
        // Sin orderBy para evitar requerir índice compuesto en Firestore
        db.collection("solicitudes")
            .whereEqualTo("uidCliente", uid())
            .get()
            .addOnSuccessListener { snap ->
                callback(
                    snap.documents.mapNotNull { it.toObject(SolicitudPresupuesto::class.java) }
                        .sortedByDescending { it.fechaCreacion }
                )
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun responderPresupuesto(
        solicitudId: String, precio: Double, respuesta: String,
        callback: (Result<Unit>) -> Unit
    ) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            val uidCliente = doc.getString("uidCliente") ?: ""
            val tecnicoNombre = doc.getString("tecnicoNombre") ?: "Técnico"
            ref.update(
                mapOf(
                    "estado" to "Presupuestado",
                    "precioPresupuesto" to precio,
                    "respuestaTecnico" to respuesta,
                    "fechaRespuesta" to System.currentTimeMillis()
                )
            ).addOnSuccessListener {
                if (uidCliente.isNotEmpty()) {
                    crearNotif(
                        uid = uidCliente,
                        titulo = "Presupuesto recibido de $tecnicoNombre",
                        detalle = "$tecnicoNombre ha respondido con un presupuesto de ${precio.toInt()} €. Ábrelo en Presupuestos para aceptarlo.",
                        tipo = "presupuesto"
                    )
                }
                callback(Result.success(Unit))
            }.addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error respondiendo")))
            }
        }.addOnFailureListener { e ->
            callback(Result.failure(Exception(e.message ?: "Error obteniendo solicitud")))
        }
    }

    fun aceptarPresupuesto(solicitudId: String, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            val tecnicoNombre = doc.getString("tecnicoNombre") ?: "Técnico"
            val precio = doc.getDouble("precioPresupuesto") ?: 0.0
            ref.update("estado", "Aceptado")
                .addOnSuccessListener {
                    crearNotif(
                        uid = uid(),
                        titulo = "Presupuesto aceptado ✓",
                        detalle = "Has aceptado el presupuesto de $tecnicoNombre (${precio.toInt()} €). El técnico se pondrá en contacto pronto.",
                        tipo = "presupuesto"
                    )
                    callback(Result.success(Unit))
                }
                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error aceptando"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    fun rechazarPresupuesto(solicitudId: String, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            val tecnicoNombre = doc.getString("tecnicoNombre") ?: "Técnico"
            ref.update("estado", "Rechazado")
                .addOnSuccessListener {
                    crearNotif(
                        uid = uid(),
                        titulo = "Presupuesto rechazado",
                        detalle = "Has rechazado el presupuesto de $tecnicoNombre.",
                        tipo = "presupuesto"
                    )
                    callback(Result.success(Unit))
                }
                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error rechazando"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    fun completarSolicitud(solicitudId: String, callback: (Result<Unit>) -> Unit) {
        val ref = db.collection("solicitudes").document(solicitudId)
        ref.get().addOnSuccessListener { doc ->
            val tecnicoNombre = doc.getString("tecnicoNombre") ?: "Técnico"
            val tecnicoId = doc.getString("tecnicoId") ?: ""
            val nombreCliente = doc.getString("nombreCliente") ?: "Cliente"
            ref.update("estado", "Completado")
                .addOnSuccessListener {
                    crearNotif(
                        uid = uid(),
                        titulo = "Reforma completada ✓",
                        detalle = "Has marcado la reforma con $tecnicoNombre como completada. Ya puedes valorarlo.",
                        tipo = "reforma"
                    )
                    if (tecnicoId.isNotEmpty()) {
                        crearNotif(
                            uid = tecnicoId,
                            titulo = "Reforma completada",
                            detalle = "$nombreCliente ha marcado la reforma como completada.",
                            tipo = "reforma"
                        )
                    }
                    callback(Result.success(Unit))
                }
                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error completando"))) }
        }.addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    fun puedeValorar(tecnicoId: String, callback: (Boolean) -> Unit) {
        db.collection("solicitudes")
            .whereEqualTo("uidCliente", uid())
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.any { doc ->
                    doc.getString("tecnicoId") == tecnicoId && doc.getString("estado") == "Completado"
                })
            }
            .addOnFailureListener { callback(false) }
    }

    private fun crearNotif(uid: String, titulo: String, detalle: String, tipo: String) {
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
