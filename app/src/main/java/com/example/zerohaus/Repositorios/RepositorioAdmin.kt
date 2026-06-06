package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.Usuario
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

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
     * Eliminación completa desde el cliente: borra en cascada todos los datos
     * del usuario en Firestore. La cuenta Auth no se puede borrar desde el
     * cliente (requiere Admin SDK), pero quedará huérfana sin datos.
     */
    fun eliminarUsuario(uid: String, callback: (Result<Unit>) -> Unit) {
        // Colecciones con campo "uid"
        val colSimples = listOf("viviendas", "informes", "certificados", "notificaciones")
        // Colecciones con dos posibles campos
        val colDobles = listOf(
            "proyectos" to listOf("uid", "tecnicoUid"),
            "solicitudes" to listOf("uidCliente", "tecnicoUid"),
            "pagos" to listOf("uidCliente", "tecnicoUid"),
            "resenas" to listOf("uid", "tecnicoId")
        )

        var pendientes = colSimples.size + colDobles.size + 1 // +1 para chats
        var hayError = false

        fun checkDone() {
            pendientes--
            if (pendientes <= 0) {
                // Al final borrar docs por ID: usuarios, tecnicos, ajustes
                val docsBorrar = listOf("usuarios", "tecnicos", "ajustes")
                var pendDocs = docsBorrar.size
                docsBorrar.forEach { col ->
                    db.collection(col).document(uid).delete()
                        .addOnCompleteListener {
                            pendDocs--
                            if (pendDocs <= 0) {
                                if (hayError) callback(Result.failure(Exception("Eliminado con algunos errores")))
                                else callback(Result.success(Unit))
                            }
                        }
                }
            }
        }

        // 1) Colecciones simples
        colSimples.forEach { col ->
            borrarQuery(db.collection(col).whereEqualTo("uid", uid)) { checkDone() }
        }

        // 2) Colecciones dobles
        colDobles.forEach { (col, campos) ->
            var subPend = campos.size
            campos.forEach { campo ->
                borrarQuery(db.collection(col).whereEqualTo(campo, uid)) {
                    subPend--
                    if (subPend <= 0) checkDone()
                }
            }
        }

        // 3) Chats (incluye subcolección mensajes)
        db.collection("chats").whereArrayContains("participantes", uid).get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) { checkDone(); return@addOnSuccessListener }
                var chatsPend = snap.size()
                snap.documents.forEach { chatDoc ->
                    // Primero borrar mensajes
                    chatDoc.reference.collection("mensajes").get()
                        .addOnSuccessListener { msgs ->
                            val batch = db.batch()
                            msgs.documents.forEach { batch.delete(it.reference) }
                            batch.delete(chatDoc.reference)
                            batch.commit().addOnCompleteListener {
                                chatsPend--
                                if (chatsPend <= 0) checkDone()
                            }
                        }
                        .addOnFailureListener {
                            chatDoc.reference.delete()
                            chatsPend--
                            if (chatsPend <= 0) checkDone()
                        }
                }
            }
            .addOnFailureListener { hayError = true; checkDone() }
    }

    private fun borrarQuery(
        query: com.google.firebase.firestore.Query,
        onDone: () -> Unit
    ) {
        query.get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) { onDone(); return@addOnSuccessListener }
                val batch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit().addOnCompleteListener { onDone() }
            }
            .addOnFailureListener { onDone() }
    }

}
