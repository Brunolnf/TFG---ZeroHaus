package com.example.zerohaus.Repositorios

import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.Usuario
import com.example.zerohaus.Util.getOrTimeout
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

    /**
     * Borra docs de `/tecnicos` huérfanos cuyo nombre coincide con un patrón
     * genérico ("tecnico", "técnico", "tecnico1", "técnico 2", vacío…). Son
     * datos seed o pruebas viejas que aparecen en el buscador del cliente pero
     * NO tienen entrada en `/usuarios` — por eso la limpieza basada en
     * cuentas Auth no los ve. Aquí los borramos en cascada (reseñas, chats,
     * solicitudes, proyectos, certificados, notificaciones).
     *
     * Verificamos que realmente sean huérfanos (sin `/usuarios/{uid}` ni
     * `/usuarios/{id}`): un técnico legítimo con nombre "Tecnico" o
     * "Tecnico1" (típico en demos) no se debe borrar — antes el filtro
     * solo miraba el nombre y arrasaba con certificados aprobados incluidos.
     */
    fun limpiarTecnicosHuerfanosGenericos(callback: (Int) -> Unit) {
        // Patrón: literal "tecnico"/"técnico" con sufijo numérico opcional
        // (con o sin espacio). Cubre "Tecnico", "tecnico1", "TECNICO 2"…
        val regex = Regex("^t[eé]cnico\\s*\\d*$", RegexOption.IGNORE_CASE)

        db.collection("tecnicos").get()
            .addOnSuccessListener { tecSnap ->
                val candidatos = tecSnap.documents.filter { doc ->
                    val nombre = doc.getString("nombre").orEmpty().trim()
                    nombre.isEmpty() || regex.matches(nombre)
                }
                if (candidatos.isEmpty()) { callback(0); return@addOnSuccessListener }

                // Cruzamos con /usuarios para no tocar técnicos con cuenta Auth.
                db.collection("usuarios").get()
                    .addOnSuccessListener { uSnap ->
                        val uidsConCuenta = uSnap.documents.map { it.id }.toSet()
                        val huerfanos = candidatos.filter { doc ->
                            val uid = doc.getString("uid").orEmpty()
                            val id = doc.id
                            uid !in uidsConCuenta && id !in uidsConCuenta
                        }
                        if (huerfanos.isEmpty()) { callback(0); return@addOnSuccessListener }

                        var pendientes = huerfanos.size
                        var borrados = 0
                        huerfanos.forEach { doc ->
                            val tecId = doc.id
                            val tecUid = doc.getString("uid").orEmpty()
                            borrarTecnicoCompleto(tecId, tecUid) { ok ->
                                if (ok) borrados++
                                pendientes--
                                if (pendientes <= 0) callback(borrados)
                            }
                        }
                    }
                    .addOnFailureListener { callback(0) }
            }
            .addOnFailureListener { callback(0) }
    }

    /**
     * Borra los usuarios "genéricos" sin nombre. Son cuentas restos de
     * pruebas: la app nunca crea cuentas con nombre vacío, así que se
     * pueden eliminar sin riesgo. Se llama en cascada `eliminarUsuario`,
     * así que se borran también sus datos asociados.
     *
     * ANTES también borraba por patrón `^tecnico\d*$` (Tecnico, Tecnico1…),
     * pero un usuario LEGÍTIMO con ese nombre (típico mientras pruebas la
     * app) acababa eliminado junto con sus certificados verificados —
     * exactamente el caso que rompió el mapa de técnicos. Restringimos a
     * nombre vacío para no destruir datos reales con un nombre informal.
     */
    fun limpiarUsuariosGenericos(adminUid: String?, callback: (Int) -> Unit) {
        db.collection("usuarios").get()
            .addOnSuccessListener { snap ->
                val genericos = snap.documents.filter { doc ->
                    val nombre = doc.getString("nombre").orEmpty().trim()
                    val esAdmin = adminUid != null && doc.id == adminUid
                    nombre.isEmpty() && !esAdmin
                }
                if (genericos.isEmpty()) { callback(0); return@addOnSuccessListener }

                var pendientes = genericos.size
                var borrados = 0
                genericos.forEach { doc ->
                    eliminarUsuario(doc.id) { result ->
                        if (result.isSuccess) borrados++
                        pendientes--
                        if (pendientes <= 0) callback(borrados)
                    }
                }
            }
            .addOnFailureListener { callback(0) }
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

    /**
     * Borra todos los técnicos "fake" (sin cuenta Auth real). Un técnico se considera
     * real si existe `/usuarios/{tec.uid}` con `tipoUsuario == "Técnico"`. El resto
     * son seed data o restos de pruebas: se eliminan junto con sus datos asociados
     * (reseñas, chats, solicitudes, proyectos, certificados, notificaciones).
     *
     * El callback recibe el número de técnicos borrados (o -1 si hubo error global).
     */
    fun limpiarTecnicosFake(callback: (Result<Int>) -> Unit) {
        db.collection("tecnicos").get()
            .addOnSuccessListener { tecSnap ->
                if (tecSnap.isEmpty) { callback(Result.success(0)); return@addOnSuccessListener }
                val tecnicos = tecSnap.documents
                // Leemos TODOS los /usuarios (no `whereEqualTo("tipoUsuario", "Técnico")`)
                // y filtramos en cliente: el campo puede estar guardado como "Tecnico"
                // sin tilde, "TECNICO", etc. La query exacta dejaba esos fuera y los
                // técnicos reales acababan clasificados como fakes.
                db.collection("usuarios").get()
                    .addOnSuccessListener { uSnap ->
                        val uidsReales = uSnap.documents
                            .filter { doc ->
                                val tipo = doc.getString("tipoUsuario").orEmpty()
                                tipo.equals("Técnico", ignoreCase = true) ||
                                    tipo.equals("Tecnico", ignoreCase = true)
                            }
                            .map { it.id }
                            .toSet()
                        // Un técnico es real si su uid (o el id del doc) coincide con un
                        // /usuarios/{uid} de tipo "Técnico". Si no, es fake.
                        val fakes = tecnicos.filter { doc ->
                            val uid = doc.getString("uid").orEmpty()
                            val id = doc.id
                            uid !in uidsReales && id !in uidsReales
                        }
                        if (fakes.isEmpty()) { callback(Result.success(0)); return@addOnSuccessListener }

                        var pendientes = fakes.size
                        var borrados = 0
                        fakes.forEach { doc ->
                            val tecId = doc.id
                            val tecUid = doc.getString("uid").orEmpty()
                            borrarTecnicoCompleto(tecId, tecUid) { ok ->
                                if (ok) borrados++
                                pendientes--
                                if (pendientes <= 0) callback(Result.success(borrados))
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        callback(Result.failure(Exception(e.message ?: "Error leyendo usuarios")))
                    }
            }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(e.message ?: "Error leyendo técnicos")))
            }
    }

    /**
     * Cascada de borrado para un técnico "fake": reseñas, certificados, notificaciones,
     * solicitudes, proyectos, chats (con mensajes) y el doc /tecnicos/{tecId}.
     * No toca /usuarios (por definición no existe para fakes) ni Auth (no aplica).
     */
    private fun borrarTecnicoCompleto(
        tecId: String,
        tecUid: String,
        onDone: (Boolean) -> Unit
    ) {
        // Identificadores por los que las colecciones referencian al técnico. Probamos
        // ambos porque los datos antiguos pueden usar el docId o el uid indistintamente.
        val ids = listOfNotNull(tecId.takeIf { it.isNotBlank() }, tecUid.takeIf { it.isNotBlank() })
            .distinct()
        if (ids.isEmpty()) { onDone(false); return }

        val queries = mutableListOf<com.google.firebase.firestore.Query>()
        ids.forEach { id ->
            queries += db.collection("resenas").whereEqualTo("tecnicoId", id)
            queries += db.collection("solicitudes").whereEqualTo("tecnicoId", id)
            queries += db.collection("solicitudes").whereEqualTo("tecnicoUid", id)
            queries += db.collection("proyectos").whereEqualTo("tecnicoId", id)
            queries += db.collection("proyectos").whereEqualTo("tecnicoUid", id)
            queries += db.collection("pagos").whereEqualTo("tecnicoUid", id)
            queries += db.collection("certificados").whereEqualTo("uid", id)
            queries += db.collection("notificaciones").whereEqualTo("uid", id)
        }

        var pendientes = queries.size + ids.size + 1 // +ids chats, +1 doc tecnico

        fun checkFin() {
            pendientes--
            if (pendientes <= 0) onDone(true)
        }

        queries.forEach { q -> borrarQuery(q) { checkFin() } }

        // Chats: borra el chat y su subcolección de mensajes.
        ids.forEach { id ->
            db.collection("chats").whereArrayContains("participantes", id).get()
                .addOnSuccessListener { snap ->
                    if (snap.isEmpty) { checkFin(); return@addOnSuccessListener }
                    var chatsPend = snap.size()
                    snap.documents.forEach { chatDoc ->
                        chatDoc.reference.collection("mensajes").get()
                            .addOnSuccessListener { msgs ->
                                val batch = db.batch()
                                msgs.documents.forEach { batch.delete(it.reference) }
                                batch.delete(chatDoc.reference)
                                batch.commit().addOnCompleteListener {
                                    chatsPend--
                                    if (chatsPend <= 0) checkFin()
                                }
                            }
                            .addOnFailureListener {
                                chatDoc.reference.delete()
                                chatsPend--
                                if (chatsPend <= 0) checkFin()
                            }
                    }
                }
                .addOnFailureListener { checkFin() }
        }

        // Doc del técnico (siempre por docId; el uid puede coincidir o no).
        db.collection("tecnicos").document(tecId).delete()
            .addOnCompleteListener { checkFin() }
    }

}
