package com.example.zerohaus.ViewModel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Usuario
import com.example.zerohaus.Repositorios.RepositorioAutenticacion
import com.example.zerohaus.Repositorios.RepositorioChat
import com.google.firebase.auth.FirebaseAuth

class SesionViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val repo = RepositorioAutenticacion()

    var logueado = mutableStateOf<Boolean?>(null)
    var usuario = mutableStateOf<Usuario?>(null)

    // Flag para que comprobarSesion() no machaque el estado tras logout
    private var sesionCerrada = false

    fun comprobarSesion() {
        // Si ya se hizo logout, no volver a comprobar (evita el spinner)
        if (sesionCerrada) return

        val u = auth.currentUser
        logueado.value = u != null
        if (u != null) cargarUsuario()
    }

    fun cargarUsuario() {
        repo.obtenerUsuario { u ->
            usuario.value = u
        }
    }

    fun logout() {
        sesionCerrada = true
        auth.signOut()
        RepositorioChat.limpiarCacheNombre()
        usuario.value = null
        logueado.value = false
    }
}
