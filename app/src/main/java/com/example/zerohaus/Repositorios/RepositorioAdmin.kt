package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.Usuario
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions

/**
 * Operaciones reservadas al administrador.
 *
 * **Crear usuarios sin desloguear al admin**: Firebase Auth solo permite
 * `createUserWithEmailAndPassword` desde la instancia activa, lo que
 * normalmente loguea automáticamente al nuevo usuario. Para evitarlo
 * inicializamos una FirebaseApp **secundaria** (`"admin_secondary"`)
 * con las mismas credenciales del proyecto, creamos al usuario allí
 * y desechamos la sesión secundaria. La sesión del admin queda intacta.
 *
 * **Bloquear/eliminar**: el cliente Android no puede tocar otras cuentas
 * Auth (eso requiere Admin SDK en backend). Trabajamos con flags en
 * Firestore (`bloqueado`, `eliminado`) que el login comprueba.
 */
class RepositorioAdmin {

    private val db = FirebaseFirestore.getInstance()
    // Cloud Functions desplegadas en europe-west1 (más cerca de España = menor latencia).
    private val functions = FirebaseFunctions.getInstance("europe-west1")

    /** Lista todos los usuarios (incluidos bloqueados y eliminados). */
    fun listarUsuarios(callback: (List<Usuario>) -> Unit) {
        db.collection("usuarios").get()
            .addOnSuccessListener { snap ->
                val lista = snap.documents.mapNotNull { it.toObject(Usuario::class.java) }
                    .sortedBy { it.nombre.lowercase() }
                callback(lista)
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    /**
     * Crea una cuenta Auth + doc Firestore usando una FirebaseApp secundaria
     * para no perder la sesión del administrador.
     */
    fun crearUsuario(
        nombre: String,
        email: String,
        password: String,
        tipo: String,
        callback: (Result<Unit>) -> Unit
    ) {
        val primary = FirebaseApp.getInstance()
        val secondaryName = "admin_secondary"
        val secondary: FirebaseApp = try {
            FirebaseApp.getInstance(secondaryName)
        } catch (_: IllegalStateException) {
            FirebaseApp.initializeApp(primary.applicationContext, primary.options, secondaryName)
        }

        val secAuth = FirebaseAuth.getInstance(secondary)
        secAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid == null) {
                    secAuth.signOut()
                    callback(Result.failure(Exception("No se pudo obtener el UID del nuevo usuario")))
                    return@addOnSuccessListener
                }
                val usuario = Usuario(
                    uid = uid,
                    nombre = nombre,
                    email = email,
                    tipoUsuario = tipo
                )
                db.collection("usuarios").document(uid).set(usuario)
                    .addOnSuccessListener {
                        // Si es técnico, crear también su perfil profesional.
                        if (tipo == "Técnico") {
                            val tec = Tecnico(
                                id = uid,
                                uid = uid,
                                nombre = nombre,
                                emailContacto = email
                            )
                            db.collection("tecnicos").document(uid).set(tec)
                                .addOnSuccessListener { finalizarCreacion(secAuth, callback, null) }
                                .addOnFailureListener { e ->
                                    finalizarCreacion(secAuth, callback, e.message ?: "Error creando técnico")
                                }
                        } else {
                            finalizarCreacion(secAuth, callback, null)
                        }
                    }
                    .addOnFailureListener { e ->
                        finalizarCreacion(secAuth, callback, e.message ?: "Error guardando usuario")
                    }
            }
            .addOnFailureListener { e ->
                secAuth.signOut()
                val msg = when {
                    (e.message ?: "").contains("already in use", ignoreCase = true) ->
                        "Ya existe una cuenta con ese correo."
                    (e.message ?: "").contains("badly formatted", ignoreCase = true) ->
                        "Email mal formado."
                    (e.message ?: "").contains("weak", ignoreCase = true) ->
                        "La contraseña es demasiado débil (mínimo 6 caracteres)."
                    else -> e.message ?: "Error creando el usuario"
                }
                callback(Result.failure(Exception(msg)))
            }
    }

    private fun finalizarCreacion(
        secAuth: FirebaseAuth,
        callback: (Result<Unit>) -> Unit,
        errorMsg: String?
    ) {
        // Cerrar sesión secundaria para que el admin siga siendo el único
        // usuario activo en la app.
        secAuth.signOut()
        if (errorMsg == null) callback(Result.success(Unit))
        else callback(Result.failure(Exception(errorMsg)))
    }

    /** Actualiza nombre y tipoUsuario. No tocamos email (cambia la cuenta Auth). */
    fun actualizarUsuario(
        uid: String,
        nombre: String,
        tipoUsuario: String,
        callback: (Result<Unit>) -> Unit
    ) {
        val datos = mapOf(
            "nombre" to nombre,
            "tipoUsuario" to tipoUsuario
        )
        db.collection("usuarios").document(uid).set(datos, SetOptions.merge())
            .addOnSuccessListener {
                // Si pasa a técnico y aún no tiene perfil, lo creamos.
                if (tipoUsuario == "Técnico") {
                    db.collection("tecnicos").document(uid).get()
                        .addOnSuccessListener { snap ->
                            if (!snap.exists()) {
                                val tec = Tecnico(id = uid, uid = uid, nombre = nombre)
                                db.collection("tecnicos").document(uid).set(tec)
                                    .addOnCompleteListener { callback(Result.success(Unit)) }
                            } else {
                                // Actualiza el nombre del técnico también para mantener coherencia.
                                db.collection("tecnicos").document(uid)
                                    .set(mapOf("nombre" to nombre), SetOptions.merge())
                                    .addOnCompleteListener { callback(Result.success(Unit)) }
                            }
                        }
                } else {
                    callback(Result.success(Unit))
                }
            }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error actualizando")))
            }
    }

    /** Bloquea o desbloquea. El cambio surte efecto en el siguiente login. */
    fun setBloqueado(uid: String, bloqueado: Boolean, callback: (Result<Unit>) -> Unit) {
        db.collection("usuarios").document(uid)
            .set(mapOf("bloqueado" to bloqueado), SetOptions.merge())
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error cambiando estado")))
            }
    }

    /**
     * "Elimina" el usuario marcándolo como eliminado y borrando datos no críticos.
     * La cuenta Auth no se puede tocar desde el cliente — queda huérfana pero
     * el flag `eliminado=true` impide cualquier login posterior.
     */
    fun eliminarUsuario(uid: String, callback: (Result<Unit>) -> Unit) {
        // Marcamos eliminado=true en lugar de borrar el doc, así el login
        // detecta la cuenta como muerta aunque la cuenta Auth siga existiendo.
        db.collection("usuarios").document(uid)
            .set(mapOf("eliminado" to true, "bloqueado" to true), SetOptions.merge())
            .addOnSuccessListener {
                // Borramos perfil de técnico si existía (para que no aparezca en búsquedas).
                db.collection("tecnicos").document(uid).delete()
                    .addOnCompleteListener { callback(Result.success(Unit)) }
            }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error eliminando")))
            }
    }

    /**
     * Borrado **definitivo total** delegado a la Cloud Function
     * `eliminar_usuario_completo` (region europe-west1).
     *
     * La function (Admin SDK, server-side):
     *   1. Verifica que el token JWT del caller pertenece al admin.
     *   2. Borra en cascada los datos del usuario en todas las colecciones
     *      (viviendas, informes, proyectos, certificados, notificaciones,
     *      reseñas, solicitudes, pagos, chats + mensajes, ajustes).
     *   3. Borra los docs principales (/usuarios, /tecnicos).
     *   4. Borra la cuenta de Firebase Auth de verdad.
     *
     * Devuelve un Result<Map<String,Any>> con el conteo por colección
     * para mostrar feedback útil en la UI.
     */
    fun eliminarDefinitivamente(
        uid: String,
        callback: (Result<Map<String, Any>>) -> Unit
    ) {
        val datos = hashMapOf<String, Any>("uid" to uid)
        functions.getHttpsCallable("eliminar_usuario_completo").call(datos)
            .addOnSuccessListener { result ->
                @Suppress("UNCHECKED_CAST")
                val data = result.getData() as? Map<String, Any> ?: emptyMap()
                callback(Result.success(data))
            }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(traducirErrorFunction(e))))
            }
    }

    private fun traducirErrorFunction(e: Exception): String {
        val msg = e.message?.lowercase().orEmpty()
        return when {
            "unauthenticated" in msg -> "Debes iniciar sesión."
            "permission_denied" in msg || "permission-denied" in msg ->
                "Solo el administrador puede ejecutar esta operación."
            "failed_precondition" in msg || "failed-precondition" in msg ->
                "El administrador no puede eliminarse a sí mismo."
            "invalid_argument" in msg || "invalid-argument" in msg ->
                "Datos inválidos."
            "unavailable" in msg || "deadline" in msg ->
                "Sin conexión con el servidor. Inténtalo de nuevo."
            else -> e.message ?: "Error en el borrado definitivo"
        }
    }

    /** Restaura un usuario eliminado (opcional, útil si se elimina por error). */
    fun restaurarUsuario(uid: String, callback: (Result<Unit>) -> Unit) {
        db.collection("usuarios").document(uid)
            .set(mapOf("eliminado" to false, "bloqueado" to false), SetOptions.merge())
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error restaurando")))
            }
    }
}
