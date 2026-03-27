package com.example.zerohaus.ViewModel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Usuario
import com.example.zerohaus.Repositorios.RepositorioAutenticacion
import com.google.firebase.auth.FirebaseAuth

class SesionViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val repo = RepositorioAutenticacion()

    var logueado = mutableStateOf<Boolean?>(null)
    var usuario = mutableStateOf<Usuario?>(null)

    fun comprobarSesion() {
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
        auth.signOut()
        usuario.value = null
        logueado.value = false
    }
}
