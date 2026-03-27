package com.example.zerohaus.ViewModel

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Usuario
import com.example.zerohaus.Repositorios.RepositorioAutenticacion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage

data class PerfilEstado(
    val usuario: Usuario? = null,
    val nombre: String = "",
    val email: String = "",
    val tipoUsuario: String = "",
    val fotoPerfil: String = "",
    val cargando: Boolean = true,
    val guardando: Boolean = false,
    val subiendoFoto: Boolean = false,
    val exito: Boolean = false,
    val error: String? = null
)

class PerfilViewModel : ViewModel() {

    var estado by mutableStateOf(PerfilEstado())
        private set

    private val repo = RepositorioAutenticacion()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun cargarPerfil() {
        estado = estado.copy(cargando = true)
        repo.obtenerUsuario { usuario ->
            if (usuario != null) {
                estado = estado.copy(
                    usuario = usuario,
                    nombre = usuario.nombre,
                    email = usuario.email,
                    tipoUsuario = usuario.tipoUsuario,
                    fotoPerfil = usuario.fotoPerfil,
                    cargando = false
                )
            } else {
                estado = estado.copy(cargando = false, error = "No se pudo cargar el perfil")
            }
        }
    }

    fun cambiarNombre(v: String) {
        estado = estado.copy(nombre = v, exito = false)
    }

    fun guardarPerfil() {
        val usuario = estado.usuario ?: return
        estado = estado.copy(guardando = true, error = null, exito = false)
        val actualizado = usuario.copy(nombre = estado.nombre, fotoPerfil = estado.fotoPerfil)
        repo.actualizarUsuario(actualizado) { result ->
            result
                .onSuccess { estado = estado.copy(usuario = actualizado, guardando = false, exito = true) }
                .onFailure { estado = estado.copy(guardando = false, error = it.message) }
        }
    }

    fun subirFotoPerfil(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        estado = estado.copy(subiendoFoto = true, error = null)
        val ref = storage.reference.child("perfiles/$uid/foto_perfil")
        ref.putFile(uri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { url ->
                    estado = estado.copy(fotoPerfil = url.toString(), subiendoFoto = false)
                }
            }
            .addOnFailureListener { e ->
                estado = estado.copy(subiendoFoto = false, error = e.message ?: "Error subiendo foto")
            }
    }

    fun limpiarMensajes() {
        estado = estado.copy(exito = false, error = null)
    }
}