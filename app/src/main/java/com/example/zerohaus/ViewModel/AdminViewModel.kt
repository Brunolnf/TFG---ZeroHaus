package com.example.zerohaus.ViewModel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Usuario
import com.example.zerohaus.Repositorios.RepositorioAdmin

/**
 * Lógica del panel de administración (ver [com.example.zerohaus.Repositorios.RepositorioAdmin]).
 */
class AdminViewModel : ViewModel() {

    private val repo = RepositorioAdmin()

    val usuarios = mutableStateListOf<Usuario>()
    val cargando = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val mensaje = mutableStateOf<String?>(null)
    val filtro = mutableStateOf("")

    // Abrir el panel NUNCA borra nada automáticamente: con usuarios reales,
    // borrar por patrones de nombre eliminaría cuentas legítimas. Los borrados
    // son siempre una acción explícita y confirmada del admin.
    fun cargar() {
        cargando.value = true
        // Reparación idempotente: recrea el perfil /tecnicos de profesionales
        // cuya cuenta existe pero cuyo perfil falta (no borra nada).
        repo.restaurarTecnicosDesdeUsuarios { restaurados ->
            if (restaurados > 0) mensaje.value = "Restaurados $restaurados perfil(es) profesional(es) que faltaban"
            repo.listarUsuarios { lista ->
                usuarios.clear()
                usuarios.addAll(lista)
                cargando.value = false
            }
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
                .onSuccess { mensaje.value = "Usuario creado correctamente"; cargar(); onDone() }
                .onFailure { error.value = it.message }
        }
    }

    fun actualizar(uid: String, nombre: String, tipo: String, onDone: () -> Unit) {
        cargando.value = true
        repo.actualizarUsuario(uid, nombre, tipo) { result ->
            cargando.value = false
            result
                .onSuccess { mensaje.value = "Cambios guardados"; cargar(); onDone() }
                .onFailure { error.value = it.message }
        }
    }

    fun toggleBloqueo(usuario: Usuario) {
        val nuevo = !usuario.bloqueado
        repo.setBloqueado(usuario.uid, nuevo) { result ->
            result
                .onSuccess { mensaje.value = if (nuevo) "Usuario bloqueado" else "Usuario desbloqueado"; cargar() }
                .onFailure { error.value = it.message }
        }
    }

    fun eliminar(usuario: Usuario) {
        cargando.value = true
        repo.eliminarUsuario(usuario.uid) { result ->
            cargando.value = false
            result
                .onSuccess { mensaje.value = "Usuario y todos sus datos eliminados"; cargar() }
                .onFailure { error.value = it.message }
        }
    }

    fun limpiarError() { error.value = null }
    fun limpiarMensaje() { mensaje.value = null }
}
