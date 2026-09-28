package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.Usuario
import com.example.zerohaus.Util.getOrTimeout
import com.example.zerohaus.Util.Diagnostico
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
 * **Bloquear**: flag `bloqueado` en Firestore que el login comprueba.
 *
 * **Eliminar**: el cliente no puede borrar la cuenta Auth de otro usuario
 * (requiere Admin SDK), así que se delega en la Cloud Function
 * `eliminar_usuario_completo`, que además exige el custom claim `admin`.
 */
class RepositorioAdmin {

    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance("europe-west1")

    /** Lista todos los usuarios (incluidos bloqueados y eliminados). */
    fun listarUsuarios(callback: (List<Usuario>) -> Unit) {
        db.collection("usuarios").getOrTimeout { snap ->
            val lista = snap?.documents?.mapNotNull { it.toObject(Usuario::class.java) }
                ?.sortedBy { it.nombre.lowercase() } ?: emptyList()
            callback(lista)
        }
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
                        // Si es profesional (técnico o empresa), crear también su perfil.
                        if (tipo == "Técnico" || tipo == "Empresa") {
                            val tec = Tecnico(
                                id = uid,
                                uid = uid,
                                nombre = nombre,
                                emailContacto = email,
                                tipoProfesional = if (tipo == "Empresa") Tecnico.TIPO_EMPRESA
                                                  else Tecnico.TIPO_TECNICO
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
                // Si pasa a profesional y aún no tiene perfil, lo creamos.
                if (tipoUsuario == "Técnico" || tipoUsuario == "Empresa") {
                    db.collection("tecnicos").document(uid).get()
                        .addOnSuccessListener { snap ->
                            if (!snap.exists()) {
                                val tec = Tecnico(
                                    id = uid, uid = uid, nombre = nombre,
                                    tipoProfesional = if (tipoUsuario == "Empresa") Tecnico.TIPO_EMPRESA
                                                      else Tecnico.TIPO_TECNICO
                                )
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
     * Borrado definitivo de un usuario con la Cloud Function
     * `eliminar_usuario_completo` (Admin SDK): cuenta de Auth, todos sus datos
     * de Firestore y sus archivos de Storage. Desde el cliente no se puede
     * hacer completo (las reglas no dejan borrar suscripciones, estadísticas,
     * códigos de verificación…, ni se puede borrar la cuenta de Auth de otro).
     */
    /**
     * Marca como verificado el email de un usuario (Cloud Function
     * `marcar_email_verificado`, solo admin). Para cuentas que no pueden recibir
     * el código, como la de prueba de los revisores de Google Play.
     */
    fun marcarEmailVerificado(uid: String, callback: (Result<Unit>) -> Unit) {
        functions.getHttpsCallable("marcar_email_verificado")
            .call(hashMapOf("uid" to uid))
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                Diagnostico.errorDeFuncion("marcar_email_verificado", e)
                callback(Result.failure(Exception(e.message ?: "No se pudo verificar el email")))
            }
    }

    fun eliminarUsuario(uid: String, callback: (Result<Unit>) -> Unit) {
        functions.getHttpsCallable("eliminar_usuario_completo")
            .call(hashMapOf("uid" to uid))
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                Diagnostico.errorDeFuncion("eliminar_usuario_completo", e)
                callback(Result.failure(Exception(e.message ?: "No se pudo eliminar el usuario")))
            }
    }

    /**
     * Recupera técnicos cuyos perfiles `/tecnicos` fueron borrados pero cuya cuenta
     * `/usuarios` sigue intacta. Por cada `/usuarios/{uid}` con tipoUsuario
     * "Técnico" / "Tecnico" (sin tilde) / variantes de caja, recrea un doc mínimo
     * en `/tecnicos/{uid}` si no existe. Idempotente: no toca los que ya están.
     *
     * Se llama automáticamente al abrir el panel del admin para reparar cualquier
     * borrado accidental sin pedirle al usuario que haga nada.
     */
    fun restaurarTecnicosDesdeUsuarios(callback: (Int) -> Unit = {}) {
        db.collection("usuarios").get()
            .addOnSuccessListener { uSnap ->
                val tecnicosUsuarios = uSnap.documents.filter { doc ->
                    val tipo = doc.getString("tipoUsuario").orEmpty()
                    tipo.equals("Técnico", ignoreCase = true) ||
                        tipo.equals("Tecnico", ignoreCase = true)
                }
                if (tecnicosUsuarios.isEmpty()) { callback(0); return@addOnSuccessListener }

                var pendientes = tecnicosUsuarios.size
                var restaurados = 0
                tecnicosUsuarios.forEach { uDoc ->
                    val uid = uDoc.id
                    val nombre = uDoc.getString("nombre").orEmpty()
                    val email = uDoc.getString("email").orEmpty()
                    db.collection("tecnicos").document(uid).get()
                        .addOnSuccessListener { tDoc ->
                            if (tDoc.exists()) {
                                pendientes--
                                if (pendientes <= 0) callback(restaurados)
                                return@addOnSuccessListener
                            }
                            val tec = Tecnico(
                                id = uid,
                                uid = uid,
                                nombre = nombre,
                                emailContacto = email
                            )
                            db.collection("tecnicos").document(uid).set(tec)
                                .addOnCompleteListener {
                                    if (it.isSuccessful) restaurados++
                                    pendientes--
                                    if (pendientes <= 0) callback(restaurados)
                                }
                        }
                        .addOnFailureListener {
                            pendientes--
                            if (pendientes <= 0) callback(restaurados)
                        }
                }
            }
            .addOnFailureListener { callback(0) }
    }

}
