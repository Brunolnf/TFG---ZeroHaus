package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Tecnico
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
                val usuario = Usuario(uid = uid, nombre = nombre, email = email, tipoUsuario = tipo)
                db.collection("usuarios").document(uid).set(usuario)
                    .addOnSuccessListener {
                        if (tipo == "Técnico") {
                            // Crear perfil básico de técnico para que aparezca en búsquedas
                            val tecnico = Tecnico(
                                id = uid,
                                uid = uid,
                                nombre = nombre,
                                emailContacto = email
                            )
                            db.collection("tecnicos").document(uid).set(tecnico)
                                .addOnSuccessListener { callback(Result.success(Unit)) }
                                .addOnFailureListener { callback(Result.success(Unit)) }
                        } else {
                            callback(Result.success(Unit))
                        }
                    }
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
                callback(Result.failure(Exception(traducirError(e.message))))
            }
    }

    private fun traducirError(msg: String?): String {
        val m = msg?.lowercase() ?: return "Error desconocido"
        return when {
            "no user record" in m || "user-not-found" in m ->
                "No existe ninguna cuenta registrada con ese correo."
            "badly formatted" in m || "invalid-email" in m ->
                "El formato del correo electrónico no es válido."
            "network" in m || "connection" in m || "unreachable" in m ->
                "Error de conexión. Comprueba tu internet e inténtalo de nuevo."
            "too many requests" in m || "quota" in m ->
                "Demasiados intentos. Espera unos minutos e inténtalo de nuevo."
            "password" in m || "credential" in m || "wrong-password" in m ->
                "Contraseña incorrecta."
            "email already" in m || "already in use" in m ->
                "Ya existe una cuenta con ese correo."
            else -> "Error al enviar el correo. Inténtalo de nuevo."
        }
    }
}