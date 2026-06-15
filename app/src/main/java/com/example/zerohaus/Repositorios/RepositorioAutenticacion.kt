package com.example.zerohaus.Repositorios

import android.os.Handler
import android.os.Looper
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.Usuario
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source

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
                var respondido = false
                val handler = Handler(Looper.getMainLooper())
                // Fail-open: si la lectura de moderación no responde en 6s (red o App
                // Check colgados), dejamos entrar igual para no congelar el login.
                handler.postDelayed({
                    if (!respondido) { respondido = true; callback(Result.success(Unit)) }
                }, 6000L)
                db.collection("usuarios").document(uid).get()
                    .addOnSuccessListener { doc ->
                        if (respondido) return@addOnSuccessListener
                        respondido = true; handler.removeCallbacksAndMessages(null)
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
                        if (respondido) return@addOnFailureListener
                        respondido = true; handler.removeCallbacksAndMessages(null)
                        callback(Result.success(Unit))
                    }
            }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(traducirError(e.message))))
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
        val ref = db.collection("usuarios").document(uid)
        // 1) Caché primero: respuesta instantánea desde disco, sin esperar a la red
        //    ni al token de App Check. Evita el spinner en reaperturas de la app.
        ref.get(Source.CACHE)
            .addOnSuccessListener { doc ->
                val habiaCache = doc.exists()
                if (habiaCache) callback(doc.toObject(Usuario::class.java))
                // 2) Refresca del servidor en segundo plano. Si falla o se cuelga,
                //    no pasa nada: ya hemos entregado los datos de caché.
                ref.get(Source.SERVER)
                    .addOnSuccessListener { fresh ->
                        if (fresh.exists()) callback(fresh.toObject(Usuario::class.java))
                        // Sin doc en caché ni en servidor: avisamos para no dejar
                        // al SesionViewModel esperando un callback que no llega.
                        else if (!habiaCache) callback(null)
                    }
                    .addOnFailureListener {
                        // Sólo informamos del fallo si no teníamos nada en caché;
                        // si ya entregamos datos cacheados, no los pisamos con null.
                        if (!habiaCache) callback(null)
                    }
            }
            .addOnFailureListener {
                // Sin caché (primer arranque): vamos directos al servidor.
                ref.get(Source.SERVER)
                    .addOnSuccessListener { doc -> callback(doc.toObject(Usuario::class.java)) }
                    .addOnFailureListener { callback(null) }
            }
    }

    /**
     * Igual que [obtenerUsuario] pero invoca el callback EXACTAMENTE UNA VEZ.
     *
     * [obtenerUsuario] llama al callback dos veces (caché y luego servidor) para
     * refrescar la UI. Eso es correcto para pantallas de display, pero en acciones
     * de escritura (crear solicitud de presupuesto, publicar reseña…) provoca que
     * la acción se ejecute dos veces y se creen DOCUMENTOS DUPLICADOS. Usa esta
     * variante en cualquier callback que cree o modifique datos.
     */
    fun obtenerUsuarioUnaVez(callback: (Usuario?) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { callback(null); return }
        val ref = db.collection("usuarios").document(uid)
        ref.get(Source.CACHE)
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    callback(doc.toObject(Usuario::class.java))
                } else {
                    ref.get(Source.SERVER)
                        .addOnSuccessListener { fresh -> callback(fresh.toObject(Usuario::class.java)) }
                        .addOnFailureListener { callback(null) }
                }
            }
            .addOnFailureListener {
                ref.get(Source.SERVER)
                    .addOnSuccessListener { doc -> callback(doc.toObject(Usuario::class.java)) }
                    .addOnFailureListener { callback(null) }
            }
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

    fun getUid(): String? = auth.currentUser?.uid

    /**
     * Envía el email de recuperación. Por seguridad **no revela** si el correo
     * existe o no: ante "user-not-found" devuelve éxito igualmente, así un
     * atacante no puede enumerar cuentas. El email se envía en el idioma
     * configurado en la app (vía Firebase Auth setLanguageCode).
     */
    fun recuperarPassword(email: String, idiomaApp: String, callback: (Result<Unit>) -> Unit) {
        auth.setLanguageCode(codigoIdiomaFirebase(idiomaApp))
        // ActionCodeSettings sin handleCodeInApp fuerza el uso del handler estándar
        // de Firebase (firebaseapp.com/__/auth/action), evitando Dynamic Links
        // que fueron discontinuados en 2025 y pueden causar que el link no funcione.
        val settings = ActionCodeSettings.newBuilder()
            .setUrl("https://zerohaus-2a865.firebaseapp.com")
            .setHandleCodeInApp(false)
            .setAndroidPackageName("com.example.zerohaus", false, null)
            .build()
        auth.sendPasswordResetEmail(email, settings)
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
            "no user record" in m || "user-not-found" in m ->
                "No existe ninguna cuenta con ese correo."
            "password" in m || "credential" in m || "wrong-password" in m
                || "malformed" in m || "expired" in m ->
                "Email o contraseña incorrectos."
            "email already" in m || "already in use" in m ->
                "Ya existe una cuenta con ese correo."
            "user-disabled" in m || "disabled" in m ->
                "Esta cuenta ha sido deshabilitada."
            else -> "No se pudo iniciar sesión. Inténtalo de nuevo."
        }
    }
}