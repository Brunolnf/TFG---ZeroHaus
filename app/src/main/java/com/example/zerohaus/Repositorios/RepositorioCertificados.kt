package com.example.zerohaus.Repositorios

import android.net.Uri
import com.example.zerohaus.Modelos.Certificado
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage

class RepositorioCertificados {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    /**
     * Sube un archivo a Firebase Storage y guarda los metadatos en Firestore.
     * Incluye el nombre del técnico para que el admin pueda identificarlo sin join.
     */
    fun subirCertificado(
        nombre: String,
        tipo: String,
        archivoUri: Uri,
        callback: (Result<Unit>) -> Unit
    ) {
        val uid = uid()
        // Primero obtenemos el nombre del técnico del doc /tecnicos/{uid}
        db.collection("tecnicos").document(uid).get()
            .addOnSuccessListener { tecDoc ->
                val tecnicoNombre = tecDoc.getString("nombre") ?: ""
                val fileName = "${uid}_${System.currentTimeMillis()}"
                val storageRef = storage.reference.child("certificados/$uid/$fileName")

                storageRef.putFile(archivoUri)
                    .addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { url ->
                            val ref = db.collection("certificados").document()
                            val cert = Certificado(
                                id = ref.id,
                                uid = uid,
                                nombre = nombre,
                                tipo = tipo,
                                urlArchivo = url.toString(),
                                tecnicoNombre = tecnicoNombre
                            )
                            ref.set(cert)
                                .addOnSuccessListener { callback(Result.success(Unit)) }
                                .addOnFailureListener { e ->
                                    callback(Result.failure(Exception(e.message)))
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        callback(Result.failure(Exception(e.message ?: "Error subiendo archivo")))
                    }
            }
            .addOnFailureListener {
                // Si no encuentra el doc de técnico, sube igualmente sin nombre
                val fileName = "${uid}_${System.currentTimeMillis()}"
                val storageRef = storage.reference.child("certificados/$uid/$fileName")
                storageRef.putFile(archivoUri)
                    .addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { url ->
                            val ref = db.collection("certificados").document()
                            val cert = Certificado(
                                id = ref.id, uid = uid,
                                nombre = nombre, tipo = tipo,
                                urlArchivo = url.toString()
                            )
                            ref.set(cert)
                                .addOnSuccessListener { callback(Result.success(Unit)) }
                                .addOnFailureListener { e -> callback(Result.failure(Exception(e.message))) }
                        }
                    }
                    .addOnFailureListener { e ->
                        callback(Result.failure(Exception(e.message ?: "Error subiendo archivo")))
                    }
            }
    }

    /** Certificados del técnico autenticado actualmente. */
    fun obtenerCertificados(callback: (List<Certificado>) -> Unit) {
        db.collection("certificados")
            .whereEqualTo("uid", uid())
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.mapNotNull { it.toObject(Certificado::class.java) }
                    .sortedByDescending { it.fechaSubida })
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    // ══════════════════════════════════════════════════════════
    //  Funciones de ADMINISTRACIÓN
    // ══════════════════════════════════════════════════════════

    /** Comprueba si un técnico (por su uid) tiene al menos un certificado verificado. */
    fun tieneCertificadosVerificados(tecnicoUid: String, callback: (Boolean) -> Unit) {
        db.collection("certificados")
            .whereEqualTo("uid", tecnicoUid)
            .whereEqualTo("verificado", true)
            .limit(1)
            .get()
            .addOnSuccessListener { snap -> callback(!snap.isEmpty) }
            .addOnFailureListener { callback(false) }
    }

    /** Todos los certificados pendientes de revisión (ni verificados ni rechazados). */
    fun obtenerPendientesVerificacion(callback: (List<Certificado>) -> Unit) {
        db.collection("certificados")
            .whereEqualTo("verificado", false)
            .whereEqualTo("rechazado", false)
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.mapNotNull { it.toObject(Certificado::class.java) }
                    .sortedBy { it.fechaSubida })
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    /** Aprueba un certificado. */
    fun aprobarCertificado(certId: String, callback: (Result<Unit>) -> Unit) {
        db.collection("certificados").document(certId)
            .set(
                mapOf(
                    "verificado" to true,
                    "rechazado" to false,
                    "motivoRechazo" to "",
                    "fechaVerificacion" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }

    /** Rechaza un certificado con un motivo opcional. */
    fun rechazarCertificado(certId: String, motivo: String, callback: (Result<Unit>) -> Unit) {
        db.collection("certificados").document(certId)
            .set(
                mapOf(
                    "verificado" to false,
                    "rechazado" to true,
                    "motivoRechazo" to motivo,
                    "fechaVerificacion" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e -> callback(Result.failure(Exception(e.message ?: "Error"))) }
    }
}
