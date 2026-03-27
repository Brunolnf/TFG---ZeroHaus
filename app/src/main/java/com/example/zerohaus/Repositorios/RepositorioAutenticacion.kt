package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Usuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RepositorioAutenticacion {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun login(
        email: String,
        password: String,
        callback: (Result<Unit>) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error al iniciar sesión")))
            }
    }

    fun registro(
        nombre: String,
        email: String,
        password: String,
        tipo: String,
        callback: (Result<Unit>) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid == null) {
                    callback(Result.failure(Exception("No se pudo obtener el usuario")))
                    return@addOnSuccessListener
                }
                val usuario = Usuario(
                    uid = uid,
                    nombre = nombre,
                    email = email,
                    tipoUsuario = tipo
                )
                db.collection("usuarios").document(uid).set(usuario)
                    .addOnSuccessListener { callback(Result.success(Unit)) }
                    .addOnFailureListener { e ->
                        callback(Result.failure(Exception(e.message ?: "Error guardando usuario")))
                    }
            }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error en registro")))
            }
    }

    fun obtenerUsuario(callback: (Usuario?) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { callback(null); return }
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { doc -> callback(doc.toObject(Usuario::class.java)) }
            .addOnFailureListener { callback(null) }
    }

    fun actualizarUsuario(usuario: Usuario, callback: (Result<Unit>) -> Unit) {
        db.collection("usuarios").document(usuario.uid).set(usuario)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error actualizando")))
            }
    }

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun getUid(): String? = auth.currentUser?.uid

    fun logout() { auth.signOut() }

    fun recuperarPassword(email: String, callback: (Result<Unit>) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error enviando email")))
            }
    }
}