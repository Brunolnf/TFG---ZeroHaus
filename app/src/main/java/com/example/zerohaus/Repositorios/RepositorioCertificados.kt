package com.example.zerohaus.Repositorios

import android.net.Uri
import com.example.zerohaus.Modelos.Certificado
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class RepositorioCertificados {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun uid() = auth.currentUser?.uid ?: ""

    /**
     * Sube un archivo a Firebase Storage y guarda los metadatos en Firestore
     */
    fun subirCertificado(
        nombre: String,
        tipo: String,
        archivoUri: Uri,
        callback: (Result<Unit>) -> Unit
    ) {
        val uid = uid()
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
                        urlArchivo = url.toString()
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

    fun obtenerCertificados(callback: (List<Certificado>) -> Unit) {
        db.collection("certificados")
            .whereEqualTo("uid", uid())
            .get()
            .addOnSuccessListener { snap ->
                callback(snap.documents.mapNotNull { it.toObject(Certificado::class.java) })
            }
            .addOnFailureListener { callback(emptyList()) }
    }
}

