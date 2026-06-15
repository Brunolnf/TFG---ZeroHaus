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
        if (auth.currentUser != null) cargarUsuario()
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
    }

    fun logout() {
        handler.removeCallbacksAndMessages(null)
        auth.signOut()
        AppEstado.setViviendaSeleccionadaId("")
        AppEstado.limpiarTipoUsuario()
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
