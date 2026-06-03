package com.example.zerohaus.ViewModel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Usuario
import com.example.zerohaus.Repositorios.RepositorioAdmin

class AdminViewModel : ViewModel() {

    private val repo = RepositorioAdmin()

    val usuarios = mutableStateListOf<Usuario>()
    val cargando = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val mensaje = mutableStateOf<String?>(null)
    val filtro = mutableStateOf("")

    fun cargar() {
        cargando.value = true
        repo.listarUsuarios { lista ->
            usuarios.clear()
            usuarios.addAll(lista)
            cargando.value = false
        }
    }

    fun usuariosFiltrados(): List<Usuario> {
        val q = filtro.value.trim().lowercase()
        if (q.isEmpty()) return usuarios
        return usuarios.filter {
            it.nombre.lowercase().contains(q) || it.email.lowercase().contains(q)
        }
    }

    fun crear(nombre: String, email: String, password: String, tipo: String, onDone: () -> Unit) {
        cargando.value = true
        repo.crearUsuario(nombre, email, password, tipo) { result ->
            cargando.value = false
            result
                .onSuccess {
                    mensaje.value = "Usuario creado correctamente"
                    cargar()
                    onDone()
                }
                .onFailure { error.value = it.message }
        }
    }

    fun actualizar(uid: String, nombre: String, tipo: String, onDone: () -> Unit) {
        cargando.value = true
        repo.actualizarUsuario(uid, nombre, tipo) { result ->
            cargando.value = false
            result
                .onSuccess {
                    mensaje.value = "Cambios guardados"
                    cargar()
                    onDone()
                }
                .onFailure { error.value = it.message }
        }
    }

    fun toggleBloqueo(usuario: Usuario) {
        val nuevo = !usuario.bloqueado
        repo.setBloqueado(usuario.uid, nuevo) { result ->
            result
                .onSuccess {
                    mensaje.value = if (nuevo) "Usuario bloqueado" else "Usuario desbloqueado"
                    cargar()
                }
                .onFailure { error.value = it.message }
        }
    }

    fun eliminar(usuario: Usuario) {
        repo.eliminarUsuario(usuario.uid) { result ->
            result
                .onSuccess {
                    mensaje.value = "Usuario eliminado"
                    cargar()
                }
                .onFailure { error.value = it.message }
        }
    }

    fun eliminarDefinitivamente(usuario: Usuario) {
        cargando.value = true
        repo.eliminarDefinitivamente(usuario.uid) { result ->
            cargando.value = false
            result
                .onSuccess { data ->
                    // La Cloud Function devuelve {ok, uid, stats:{coleccion:n, ..., auth:1}}.
                    // Construimos un resumen humano para el snackbar.
                    @Suppress("UNCHECKED_CAST")
                    val stats = data["stats"] as? Map<String, Any> ?: emptyMap()
                    val totalDocs = stats.values
                        .mapNotNull { (it as? Number)?.toLong() }
                        .sum()
                    val authBorrado = (stats["auth"] as? Number)?.toLong() == 1L
                    mensaje.value = buildString {
                        append("Usuario eliminado definitivamente ")
                        append("($totalDocs docs borrados")
                        if (authBorrado) append(" + cuenta Auth")
                        append(")")
                    }
                    cargar()
                }
                .onFailure { error.value = it.message }
        }
    }

    fun restaurar(usuario: Usuario) {
        repo.restaurarUsuario(usuario.uid) { result ->
            result
                .onSuccess {
                    mensaje.value = "Usuario restaurado"
                    cargar()
                }
                .onFailure { error.value = it.message }
        }
    }

    fun limpiarError() { error.value = null }
    fun limpiarMensaje() { mensaje.value = null }
}
