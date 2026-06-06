package com.example.zerohaus.ViewModel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Certificado
import com.example.zerohaus.Modelos.Usuario
import com.example.zerohaus.Repositorios.RepositorioAdmin
import com.example.zerohaus.Repositorios.RepositorioCertificados

class AdminViewModel : ViewModel() {

    private val repo = RepositorioAdmin()
    private val repoCerts = RepositorioCertificados()

    val usuarios = mutableStateListOf<Usuario>()
    val cargando = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val mensaje = mutableStateOf<String?>(null)
    val filtro = mutableStateOf("")

    // Certificados pendientes de revisión
    val certificadosPendientes = mutableStateListOf<Certificado>()
    val cargandoCerts = mutableStateOf(false)

    fun cargar() {
        cargando.value = true
        repo.listarUsuarios { lista ->
            usuarios.clear()
            usuarios.addAll(lista)
            cargando.value = false
        }
        cargarCertificadosPendientes()
    }

    fun cargarCertificadosPendientes() {
        cargandoCerts.value = true
        repoCerts.obtenerPendientesVerificacion { lista ->
            certificadosPendientes.clear()
            certificadosPendientes.addAll(lista)
            cargandoCerts.value = false
        }
    }

    fun aprobarCertificado(cert: Certificado) {
        repoCerts.aprobarCertificado(cert.id) { result ->
            result
                .onSuccess {
                    mensaje.value = "Certificado '${cert.nombre}' verificado ✓"
                    cargarCertificadosPendientes()
                }
                .onFailure { error.value = it.message }
        }
    }

    fun rechazarCertificado(cert: Certificado, motivo: String) {
        repoCerts.rechazarCertificado(cert.id, motivo) { result ->
            result
                .onSuccess {
                    mensaje.value = "Certificado '${cert.nombre}' rechazado"
                    cargarCertificadosPendientes()
                }
                .onFailure { error.value = it.message }
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
        cargando.value = true
        repo.eliminarUsuario(usuario.uid) { result ->
            cargando.value = false
            result
                .onSuccess {
                    mensaje.value = "Usuario y todos sus datos eliminados"
                    cargar()
                }
                .onFailure { error.value = it.message }
        }
    }

    fun limpiarError() { error.value = null }
    fun limpiarMensaje() { mensaje.value = null }
}
