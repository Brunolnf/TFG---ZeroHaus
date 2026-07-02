package com.example.zerohaus.ViewModel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Certificado
import com.example.zerohaus.Modelos.Usuario
import com.example.zerohaus.Repositorios.RepositorioAdmin
import com.example.zerohaus.Repositorios.RepositorioCertificados
import com.google.firebase.auth.FirebaseAuth

class AdminViewModel : ViewModel() {

    private val repo = RepositorioAdmin()
    private val repoCerts = RepositorioCertificados()

    val usuarios = mutableStateListOf<Usuario>()
    val cargando = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val mensaje = mutableStateOf<String?>(null)
    val filtro = mutableStateOf("")

    val todosCertificados = mutableStateListOf<Certificado>()
    val cargandoCerts = mutableStateOf(false)
    val filtroCerts = mutableStateOf("Todos")

    val pendientesCount: Int get() = todosCertificados.count { !it.verificado && !it.rechazado }

    fun certificadosFiltrados(): List<Certificado> = when (filtroCerts.value) {
        "Pendientes" -> todosCertificados.filter { !it.verificado && !it.rechazado }
        "Verificados" -> todosCertificados.filter { it.verificado }
        "Rechazados" -> todosCertificados.filter { it.rechazado }
        else -> todosCertificados.toList()
    }

    fun cargar() {
        cargando.value = true
        val uidAdmin = FirebaseAuth.getInstance().currentUser?.uid
        // 1) Limpieza de cuentas /usuarios cuyo nombre es genérico ("tecnico",
        //    "Tecnico1", "técnico 2", vacío…). Cascade completo.
        repo.limpiarUsuariosGenericos(uidAdmin) { borradosUsr ->
            // 2) Limpieza de /tecnicos HUÉRFANOS con nombre genérico — son los
            //    que aparecían en el buscador del cliente y no tenían entrada
            //    en /usuarios, por eso el paso (1) no los veía.
            repo.limpiarTecnicosHuerfanosGenericos { borradosTec ->
                val totalBorrados = borradosUsr + borradosTec
                if (totalBorrados > 0) {
                    mensaje.value = "Eliminados $totalBorrados técnico(s) de prueba"
                }
                // 3) Auto-reparación de técnicos legítimos huérfanos.
                repo.restaurarTecnicosDesdeUsuarios { restaurados ->
                    if (restaurados > 0 && mensaje.value == null)
                        mensaje.value = "Restaurados $restaurados técnico(s) que faltaban"
                    // 4) Recarga lista ya limpia.
                    repo.listarUsuarios { lista ->
                        usuarios.clear()
                        usuarios.addAll(lista)
                        cargando.value = false
                    }
                }
            }
        }
        cargarCertificados()
    }

    fun cargarCertificados() {
        cargandoCerts.value = true
        repoCerts.obtenerTodosCertificados { lista ->
            todosCertificados.clear()
            todosCertificados.addAll(lista)
            cargandoCerts.value = false
        }
    }

    fun aprobarCertificado(cert: Certificado) {
        repoCerts.aprobarCertificado(cert.id) { result ->
            result
                .onSuccess { mensaje.value = "Certificado '${cert.nombre}' verificado"; cargarCertificados() }
                .onFailure { error.value = it.message }
        }
    }

    fun rechazarCertificado(cert: Certificado, motivo: String) {
        repoCerts.rechazarCertificado(cert.id, motivo) { result ->
            result
                .onSuccess { mensaje.value = "Certificado '${cert.nombre}' rechazado"; cargarCertificados() }
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

    /**
     * Acción destructiva del admin: borra los técnicos del directorio que no tienen
     * cuenta Auth real (seed/demo). El usuario debe haber confirmado el diálogo antes.
     */
    fun limpiarTecnicosFake() {
        cargando.value = true
        repo.limpiarTecnicosFake { result ->
            cargando.value = false
            result
                .onSuccess { n ->
                    mensaje.value = when (n) {
                        0 -> "No había técnicos sin cuenta para limpiar"
                        1 -> "Eliminado 1 técnico sin cuenta y sus datos"
                        else -> "Eliminados $n técnicos sin cuenta y sus datos"
                    }
                    cargar()
                }
                .onFailure { error.value = it.message }
        }
    }
}
