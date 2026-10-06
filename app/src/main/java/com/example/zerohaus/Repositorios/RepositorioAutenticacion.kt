package com.example.zerohaus.Repositorios

import android.os.Handler
import android.os.Looper
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.Usuario
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.codigoIdioma
import com.example.zerohaus.Util.Diagnostico
import com.example.zerohaus.Util.esFalloDeRed
import com.example.zerohaus.Util.getCadenas
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions

/**
 * Autenticación y cuenta: login (con comprobación de bloqueo), registro,
 * recuperación y cambio de contraseña, perfil del usuario y borrado de la
 * propia cuenta (Cloud Function `eliminar_mi_cuenta`).
 */
class RepositorioAutenticacion {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance("europe-west1")

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
                                callback(Result.failure(Exception(textos().authErrCuentaNoExiste)))
                            }
                            u?.eliminado == true -> {
                                auth.signOut()
                                callback(Result.failure(Exception(textos().authErrCuentaEliminada)))
                            }
                            u?.bloqueado == true -> {
                                auth.signOut()
                                callback(Result.failure(Exception(textos().authErrCuentaBloqueada)))
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
                callback(Result.failure(Exception(mensajeDeError(e))))
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
                    callback(Result.failure(Exception(textos().authErrGenerico)))
                    return@addOnSuccessListener
                }
                // El email se verifica después con un código de 6 dígitos
                // (pantalla VerificarEmail + Cloud Functions de verificación).
                val usuario = Usuario(uid = uid, nombre = nombre, email = email, tipoUsuario = tipo)
                db.collection("usuarios").document(uid).set(usuario)
                    .addOnSuccessListener {
                        if (tipo == "Técnico" || tipo == "Empresa") {
                            // Crear perfil profesional para que aparezca en búsquedas.
                            // Si falla, la cuenta ya es válida: el panel del
                            // profesional lo crea al entrar.
                            val tecnico = Tecnico(
                                id = uid,
                                uid = uid,
                                nombre = nombre,
                                emailContacto = email,
                                tipoProfesional = if (tipo == "Empresa") Tecnico.TIPO_EMPRESA
                                                  else Tecnico.TIPO_TECNICO
                            )
                            db.collection("tecnicos").document(uid).set(tecnico)
                                .addOnCompleteListener { callback(Result.success(Unit)) }
                        } else {
                            callback(Result.success(Unit))
                        }
                    }
                    .addOnFailureListener { e ->
                        // Sin /usuarios la cuenta no podría entrar nunca (el login la
                        // trata como borrada) ni volver a registrarse con ese email:
                        // se deshace para poder reintentar.
                        result.user?.delete()
                        callback(Result.failure(Exception(
                            if (e.esFalloDeRed()) textos().errorRed else textos().authErrGenerico)))
                    }
            }
            .addOnFailureListener { e ->
                callback(Result.failure(Exception(mensajeDeError(e))))
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
     * de escritura (publicar una reseña, crear un chat…) provoca que
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
        // Solo se actualizan los campos editables desde la UI. Usar set() completo
        // borraria fechaRegistro y los campos congelados por las rules
        // (bloqueado/eliminado/tipoUsuario) - por eso .set(merge) con campos concretos.
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

    fun getEmail(): String? = auth.currentUser?.email

    /**
     * Cambia la contraseña del usuario actual. Firebase exige un login reciente
     * para operaciones sensibles, así que primero re-autenticamos con la
     * contraseña actual (que además la valida) y solo entonces actualizamos.
     */
    fun cambiarPassword(
        passwordActual: String,
        passwordNueva: String,
        callback: (Result<Unit>) -> Unit
    ) {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email.isNullOrBlank()) {
            callback(Result.failure(Exception(textos().authErrSinSesion)))
            return
        }
        val credencial = EmailAuthProvider.getCredential(email, passwordActual)
        user.reauthenticate(credencial)
            .addOnSuccessListener {
                user.updatePassword(passwordNueva)
                    .addOnSuccessListener { callback(Result.success(Unit)) }
                    .addOnFailureListener { e ->
                        callback(Result.failure(Exception(mensajeDeError(e))))
                    }
            }
            .addOnFailureListener { e ->
                // Aquí lo único que se comprueba es la contraseña actual
                val msg = if (e is FirebaseAuthInvalidCredentialsException) textos().authErrPasswordActual
                          else mensajeDeError(e)
                callback(Result.failure(Exception(msg)))
            }
    }

    /**
     * Borrado self-service de la cuenta: delega en la Cloud Function
     * `eliminar_mi_cuenta`, que borra en cascada todos los datos del usuario y
     * su cuenta de Auth server-side (RGPD / requisito de Google Play). Al
     * terminar cerramos la sesión local.
     */
    fun eliminarMiCuenta(callback: (Result<Unit>) -> Unit) {
        if (auth.currentUser == null) {
            callback(Result.failure(Exception(textos().authErrSinSesion)))
            return
        }
        functions.getHttpsCallable("eliminar_mi_cuenta")
            .call()
            .addOnSuccessListener {
                auth.signOut()
                callback(Result.success(Unit))
            }
            .addOnFailureListener { e ->
                Diagnostico.errorDeFuncion("eliminar_mi_cuenta", e)
                callback(Result.failure(Exception(
                    if (e.esFalloDeRed()) textos().errorRed else textos().ajustesEliminarError)))
            }
    }

    /**
     * Envía el email de recuperación. Por seguridad **no revela** si el correo
     * existe o no: ante "user-not-found" devuelve éxito igualmente, así un
     * atacante no puede enumerar cuentas. El email se envía en el idioma
     * configurado en la app (vía Firebase Auth setLanguageCode).
     */
    fun recuperarPassword(email: String, idiomaApp: String, callback: (Result<Unit>) -> Unit) {
        auth.setLanguageCode(codigoIdioma(idiomaApp))
        // Sin ActionCodeSettings: el enlace usa la página estándar de Firebase
        // (firebaseapp.com/__/auth/action). Antes se le pasaba el paquete
        // "com.example.zerohaus", que no es el de la app (es.zerohaus.app).
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { e ->
                // Cuenta inexistente = éxito silencioso (no revela qué correos existen)
                if (e is FirebaseAuthInvalidUserException && e.errorCode == "ERROR_USER_NOT_FOUND") {
                    callback(Result.success(Unit))
                } else {
                    callback(Result.failure(Exception(mensajeDeError(e))))
                }
            }
    }

    private fun textos() = getCadenas(AppEstado.idioma)

    /**
     * Mensaje para el usuario, en el idioma de la app, de un error de Firebase
     * Auth. Se decide por el tipo de excepción y su código (antes se buscaban
     * palabras en el mensaje en inglés, que no siempre coincidían, y en el
     * registro llegaba sin traducir).
     */
    private fun mensajeDeError(e: Exception): String {
        val c = textos()
        return when (e) {
            is FirebaseNetworkException -> c.errorRed
            is FirebaseTooManyRequestsException -> c.authErrDemasiados
            is FirebaseAuthWeakPasswordException -> c.authErrPasswordDebil
            is FirebaseAuthUserCollisionException -> c.authErrEmailEnUso
            is FirebaseAuthInvalidUserException ->
                if (e.errorCode == "ERROR_USER_DISABLED") c.authErrDeshabilitada
                else c.authErrCredenciales   // no revela si el correo existe
            is FirebaseAuthInvalidCredentialsException ->
                if (e.errorCode == "ERROR_INVALID_EMAIL") c.authErrEmailFormato
                else c.authErrCredenciales
            else -> c.authErrGenerico
        }
    }
}