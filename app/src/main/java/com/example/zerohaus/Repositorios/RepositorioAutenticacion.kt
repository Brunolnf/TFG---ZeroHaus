package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.Usuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class RepositorioAutenticacion {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun login(
        email: String,
        password: String,
        callback: (Result<Unit>) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                // Tras un login exitoso comprobamos el flag de moderación en Firestore.
                // Si el admin lo ha bloqueado/eliminado, cerramos sesión inmediatamente
                // para que la app no quede en estado "logueado".
                val uid = result.user?.uid
                if (uid == null) {
                    callback(Result.success(Unit))
                    return@addOnSuccessListener
                }
                db.collection("usuarios").document(uid).get()
                    .addOnSuccessListener { doc ->
                        val u = doc.toObject(Usuario::class.java)
                        when {
                            // Sin doc → cuenta Auth huérfana (borrada definitivamente por el
                            // admin). No la dejamos entrar para que el borrado sea efectivo
                            // aunque la cáscara de Auth siga viva.
                            !doc.exists() -> {
                                auth.signOut()
                                callback(Result.failure(Exception("Esta cuenta no existe o ha sido eliminada.")))
                            }
                            u?.eliminado == true -> {
                                auth.signOut()
                                callback(Result.failure(Exception("Esta cuenta ha sido eliminada por el administrador.")))
                            }
                            u?.bloqueado == true -> {
                                auth.signOut()
                                callback(Result.failure(Exception("Tu cuenta está bloqueada. Contacta con el administrador.")))
                            }
                            else -> callback(Result.success(Unit))
                        }
                    }
                    .addOnFailureListener {
                        // Si falla la lectura del doc, permitimos el login (no bloqueamos
                        // por errores de red): la moderación no debe romper el flujo normal.
                        callback(Result.success(Unit))
                    }
            }
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
                                .addOnFailureListener { e ->
                                    callback(Result.failure(Exception(e.message ?: "Error creando perfil de técnico")))
                                }
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
        // Sólo se actualizan los campos editables desde la UI. Usar set() completo
        // borraría tokenFCM, fechaRegistro, etc. — por eso .update() con campos concretos.
        val datos = mapOf(
            "nombre" to usuario.nombre,
            "fotoPerfil" to usuario.fotoPerfil
        )
        db.collection("usuarios").document(usuario.uid)
            .set(datos, SetOptions.merge())
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error actualizando")))
            }
    }

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun getUid(): String? = auth.currentUser?.uid

    fun logout() { auth.signOut() }

    /**
     * Envía el email de recuperación. Por seguridad **no revela** si el correo
     * existe o no: ante "user-not-found" devuelve éxito igualmente, así un
     * atacante no puede enumerar cuentas. El email se envía en el idioma
     * configurado en la app (vía Firebase Auth setLanguageCode).
     */
    fun recuperarPassword(email: String, idiomaApp: String, callback: (Result<Unit>) -> Unit) {
        auth.setLanguageCode(codigoIdiomaFirebase(idiomaApp))
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                val m = e.message?.lowercase().orEmpty()
                // "user-not-found" se trata como éxito silencioso (no enumeración)
                if ("no user record" in m || "user-not-found" in m) {
                    callback(Result.success(Unit))
                } else {
                    callback(Result.failure(Exception(traducirError(e.message))))
                }
            }
    }

    private fun codigoIdiomaFirebase(idioma: String): String = when (idioma) {
        "English"   -> "en"
        "Català"    -> "ca"
        "Euskara"   -> "eu"
        "Galego"    -> "gl"
        "Português" -> "pt"
        "Français"  -> "fr"
        "Deutsch"   -> "de"
        "Italiano"  -> "it"
        else        -> "es"
    }

    private fun traducirError(msg: String?): String {
        val m = msg?.lowercase() ?: return "Error desconocido"
        return when {
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