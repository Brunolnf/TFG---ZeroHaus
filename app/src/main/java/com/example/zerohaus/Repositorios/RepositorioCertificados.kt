package com.example.zerohaus.Repositorios

import android.net.Uri
import android.util.Log
import com.example.zerohaus.Modelos.Certificado
import com.example.zerohaus.Util.getOrTimeout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage

class RepositorioCertificados {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    fun subirCertificado(
        nombre: String,
        tipo: String,
        archivos: List<Uri>,
        callback: (Result<Unit>) -> Unit
    ) {
        val uid = uid()
        if (uid.isBlank()) {
            callback(Result.failure(Exception("No hay sesión activa")))
            return
        }
        db.collection("tecnicos")
            .whereEqualTo("uid", uid)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val tecnicoNombre = snap.documents.firstOrNull()?.getString("nombre") ?: ""
                subirArchivosYGuardar(uid, nombre, tipo, archivos, tecnicoNombre, callback)
            }
            .addOnFailureListener {
                subirArchivosYGuardar(uid, nombre, tipo, archivos, "", callback)
            }
    }

    private fun subirArchivosYGuardar(
        uid: String,
        nombre: String,
        tipo: String,
        archivos: List<Uri>,
        tecnicoNombre: String,
        callback: (Result<Unit>) -> Unit
    ) {
        val urls = mutableListOf<String>()

        fun subirSiguiente(index: Int) {
            if (index >= archivos.size) {
                val ref = db.collection("certificados").document()
                val cert = Certificado(
                    id = ref.id,
                    uid = uid,
                    nombre = nombre,
                    tipo = tipo,
                    urlArchivo = urls.firstOrNull() ?: "",
                    urlsArchivos = urls.toList(),
                    tecnicoNombre = tecnicoNombre
                )
                ref.set(cert)
                    .addOnSuccessListener {
                        Log.d("RepoCerts", "Certificado guardado: ${ref.id}")
                        callback(Result.success(Unit))
                    }
                    .addOnFailureListener { e ->
                        Log.e("RepoCerts", "Error guardando certificado: ${e.message}")
                        callback(Result.failure(Exception(e.message)))
                    }
                return
            }
            val fileName = "${uid}_${System.currentTimeMillis()}_$index"
            val ref = storage.reference.child("certificados/$uid/$fileName")
            ref.putFile(archivos[index])
                .addOnSuccessListener {
                    ref.downloadUrl
                        .addOnSuccessListener { url ->
                            urls.add(url.toString())
                            subirSiguiente(index + 1)
                        }
                        .addOnFailureListener { e ->
                            Log.e("RepoCerts", "Error obteniendo URL: ${e.message}")
                            callback(Result.failure(Exception(e.message)))
                        }
                }
                .addOnFailureListener { e ->
                    Log.e("RepoCerts", "Error subiendo archivo: ${e.message}")
                    callback(Result.failure(Exception(e.message ?: "Error subiendo archivo")))
                }
        }

        subirSiguiente(0)
    }

    fun obtenerCertificados(callback: (List<Certificado>) -> Unit) {
        db.collection("certificados")
            .whereEqualTo("uid", uid())
            .getOrTimeout { snap ->
                callback(snap?.documents?.mapNotNull { it.toObject(Certificado::class.java) }
                    ?.sortedByDescending { it.fechaSubida } ?: emptyList())
            }
    }

    // ══════════════════════════════════════════════════════════
    //  Funciones de ADMINISTRACIÓN
    // ══════════════════════════════════════════════════════════

    fun tieneCertificadosVerificados(tecnicoUid: String, callback: (Boolean) -> Unit) {
        db.collection("certificados")
            .whereEqualTo("uid", tecnicoUid)
            .getOrTimeout { snap ->
                val tiene = snap?.documents
                    ?.mapNotNull { it.toObject(Certificado::class.java) }
                    ?.any { it.verificado } ?: false
                callback(tiene)
            }
    }

    fun obtenerTodosCertificados(callback: (List<Certificado>) -> Unit) {
        db.collection("certificados")
            .getOrTimeout { snap ->
                callback(snap?.documents
                    ?.mapNotNull { it.toObject(Certificado::class.java) }
                    ?.sortedByDescending { it.fechaSubida } ?: emptyList())
            }
    }

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
