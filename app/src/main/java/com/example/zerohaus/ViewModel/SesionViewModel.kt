package com.example.zerohaus.ViewModel

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Usuario
import com.example.zerohaus.Repositorios.RepositorioAutenticacion
import com.example.zerohaus.Repositorios.RepositorioChat
import com.example.zerohaus.Util.AppEstado
import com.google.firebase.auth.FirebaseAuth

class SesionViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val repo = RepositorioAutenticacion()
    private val handler = Handler(Looper.getMainLooper())
    private var intentos = 0

    var logueado = mutableStateOf(auth.currentUser != null)
    var usuario  = mutableStateOf<Usuario?>(null)
    // true cuando, tras varios intentos, no se pudo cargar el usuario. La UI
    // muestra un botón "Reintentar" en vez de un spinner eterno.
    var cargaFallida = mutableStateOf(false)

    init {
        if (auth.currentUser != null) {
            cargarUsuario()
            // Sesión persistente: refresca el claim sin forzar (toma el token
            // cacheado del SDK si está vigente). Mantiene AppEstado.esAdminCache
            // alineado con la realidad del servidor por si cambió.
            refrescarClaims(forzar = false)
        }
    }

    fun cargarUsuario() {
        cargaFallida.value = false
        // Watchdog INDEPENDIENTE del callback: si la query de Firestore ni
        // responde ni falla (App Check / red colgada), el callback nunca llega
        // y sin esto el spinner giraría para siempre. El temporizador salta igual.
        programarWatchdog()
        repo.obtenerUsuario { u ->
            if (u != null) {
                usuario.value = u
                AppEstado.guardarTipoUsuario(u.tipoUsuario)
                cargaFallida.value = false
                intentos = 0
                handler.removeCallbacksAndMessages(null)
            }
            // Si u == null no hacemos nada aquí: el watchdog decide reintento/fallo.
        }
    }

    /**
     * Refresca el flag de admin leyendo el custom claim del ID token. Lo
     * persiste en AppEstado/AppPreferencias para que el siguiente arranque
     * tenga un valor síncrono al decidir startDestination.
     *
     * Llamar con forzar=true tras un login (asegura claim recién emitido por
     * el servidor) y con forzar=false en arranques con sesión ya activa
     * (usa el token cacheado del SDK, instantáneo y sin red).
     */
    fun refrescarClaims(forzar: Boolean) {
        val user = auth.currentUser ?: run {
            AppEstado.guardarEsAdmin(false)
            return
        }
        user.getIdToken(forzar)
            .addOnSuccessListener { result ->
                AppEstado.guardarEsAdmin(result.claims["admin"] == true)
            }
            .addOnFailureListener {
                // Conservamos el valor cacheado: una caída de red puntual no
                // debe degradar a un admin legítimo a usuario normal.
            }
    }

    private fun programarWatchdog() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (auth.currentUser == null || usuario.value != null) return@postDelayed
            if (intentos < 2) {
                intentos++
                cargarUsuario()
            } else {
                // Agotados los reintentos: dejamos de girar y ofrecemos reintentar.
                cargaFallida.value = true
            }
        }, 3500L)
    }

    fun postLogin() {
        usuario.value = null
        logueado.value = true
        cargaFallida.value = false
        intentos = 0
        cargarUsuario()
        // Tras login: forzamos refresh del ID token para que el claim recién
        // emitido por el servidor llegue (un token cacheado anterior podría
        // ser de hace minutos y no traer aún la promoción/democión).
        refrescarClaims(forzar = true)
    }

    fun logout() {
        handler.removeCallbacksAndMessages(null)
        auth.signOut()
        AppEstado.setViviendaSeleccionadaId("")
        AppEstado.limpiarTipoUsuario()
        AppEstado.guardarEsAdmin(false)
        RepositorioChat.limpiarCacheNombre()
        usuario.value = null
        logueado.value = false
        cargaFallida.value = false
        intentos = 0
    }

    override fun onCleared() {
        super.onCleared()
        handler.removeCallbacksAndMessages(null)
    }
}
