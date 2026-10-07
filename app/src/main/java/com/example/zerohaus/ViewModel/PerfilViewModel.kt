package com.example.zerohaus.ViewModel

import android.app.Application
import android.net.Uri
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.zerohaus.Modelos.Especialidades
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.Usuario
import com.example.zerohaus.Repositorios.RepositorioAutenticacion

import com.example.zerohaus.Repositorios.RepositorioTecnicos
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.Imagenes
import com.example.zerohaus.Util.Telefono
import com.example.zerohaus.Util.getCadenas
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado del perfil propio.
 */
data class PerfilEstado(
    val usuario: Usuario? = null,
    val nombre: String = "",
    val email: String = "",
    val tipoUsuario: String = "",
    val fotoPerfil: String = "",
    // Campos exclusivos de técnico
    val especialidades: Set<String> = emptySet(),   // valores de Especialidades.TODAS
    val descripcion: String = "",
    val telefono: String = "",
    val emailContacto: String = "",
    val tecnicoDocId: String = "",
    val ciudad: String = "",
    // Estado UI
    val cargando: Boolean = false,
    val guardando: Boolean = false,
    val subiendoFoto: Boolean = false,
    val exito: Boolean = false,
    val error: String? = null
) {
    /** Técnicos certificadores y empresas de reformas comparten el perfil profesional. */
    val esProfesional: Boolean get() = tipoUsuario == "Técnico" || tipoUsuario == "Empresa"
}

/**
 * Edición del perfil propio. Técnicos y empresas editan también su perfil profesional.
 */
class PerfilViewModel(application: Application) : AndroidViewModel(application) {

    var estado by mutableStateOf(PerfilEstado())
        private set

    private val repo = RepositorioAutenticacion()
    private val repoTecnicos = RepositorioTecnicos()

    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    init { if (auth.currentUser != null) cargarPerfil() }

    fun cargarPerfil() {
        estado = estado.copy(cargando = true)
        repo.obtenerUsuario { usuario ->
            if (usuario != null) {
                estado = estado.copy(
                    usuario = usuario,
                    nombre = usuario.nombre,
                    email = usuario.email,
                    tipoUsuario = usuario.tipoUsuario,
                    fotoPerfil = usuario.fotoPerfil
                )
                if (usuario.tipoUsuario == "Técnico" || usuario.tipoUsuario == "Empresa") {
                    // Buscamos por campo uid (no por ID de documento, que es diferente al Auth UID)
                    repoTecnicos.obtenerMiPerfilTecnico { tecnico ->
                        estado = estado.copy(
                            // Los perfiles antiguos (texto libre) se convierten al catálogo
                            especialidades = Especialidades.canonicas(tecnico?.especialidades.orEmpty()).toSet(),
                            descripcion = tecnico?.descripcion ?: "",
                            tecnicoDocId = tecnico?.id ?: "",
                            ciudad = tecnico?.ciudad ?: "",
                            cargando = false
                        )
                        estado = estado.copy(
                            telefono = Telefono.normalizar(tecnico?.telefono),
                            emailContacto = (tecnico?.emailContacto ?: "").ifBlank { usuario.email }
                        )
                    }
                } else {
                    estado = estado.copy(cargando = false)
                }
            } else {
                estado = estado.copy(cargando = false, error = getCadenas(AppEstado.idioma).perfilErrorCargar)
            }
        }
    }

    fun cambiarNombre(v: String) { estado = estado.copy(nombre = v, exito = false) }
    fun alternarEspecialidad(e: String) {
        val actuales = estado.especialidades
        estado = estado.copy(especialidades = if (e in actuales) actuales - e else actuales + e, exito = false)
    }
    fun cambiarDescripcion(v: String) { estado = estado.copy(descripcion = v, exito = false) }
    fun cambiarTelefono(v: String) {
        // Todos los números se asumen españoles: guardamos 9 dígitos y mostramos "+34"
        // como prefijo fijo en la UI. Si pegan "+34 612...", se limpia aquí.
        estado = estado.copy(telefono = Telefono.normalizar(v), exito = false)
    }
    fun cambiarEmailContacto(v: String) { estado = estado.copy(emailContacto = v, exito = false) }
    fun cambiarCiudad(v: String) { estado = estado.copy(ciudad = v, exito = false) }

    fun guardarPerfil() {
        val usuario = estado.usuario ?: return
        val c = getCadenas(AppEstado.idioma)
        val telefono = Telefono.normalizar(estado.telefono)
        val emailContacto = estado.emailContacto.trim()
        if (estado.esProfesional) {
            if (telefono.length != 9) {
                estado = estado.copy(error = c.perfilTelefonoInvalido); return
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(emailContacto).matches()) {
                estado = estado.copy(error = c.emailError); return
            }
        }
        estado = estado.copy(guardando = true, error = null, exito = false)
        // "Guardado" solo si Firestore ha aceptado los cambios
        val terminar: (Result<Unit>) -> Unit = { r ->
            estado = if (r.isSuccess) estado.copy(guardando = false, exito = true)
                     else estado.copy(guardando = false, error = c.perfilErrorGuardar)
        }
        val actualizado = usuario.copy(nombre = estado.nombre.trim(), fotoPerfil = estado.fotoPerfil)
        repo.actualizarUsuario(actualizado) { result ->
            if (result.isFailure || !estado.esProfesional) {
                if (result.isSuccess) estado = estado.copy(usuario = actualizado)
                terminar(result)
                return@actualizarUsuario
            }
            estado = estado.copy(usuario = actualizado)
            // Siempre en el orden del catálogo
            val especialidadesLista = Especialidades.TODAS.filter { it in estado.especialidades }

            // Si ya existe documento, UPDATE parcial (conserva valoración, plan…);
            // si no, se crea.
            if (estado.tecnicoDocId.isNotEmpty()) {
                repoTecnicos.actualizarPerfilTecnico(
                    tecnicoId = estado.tecnicoDocId,
                    nombre = actualizado.nombre,
                    ciudad = estado.ciudad.trim(),
                    descripcion = estado.descripcion.trim(),
                    telefono = telefono,
                    emailContacto = emailContacto,
                    especialidades = especialidadesLista,
                    callback = terminar
                )
            } else {
                val tecnico = Tecnico(
                    uid = usuario.uid,
                    nombre = actualizado.nombre,
                    ciudad = estado.ciudad.trim(),
                    especialidades = especialidadesLista,
                    descripcion = estado.descripcion.trim(),
                    telefono = telefono,
                    emailContacto = emailContacto,
                    tipoProfesional = if (usuario.tipoUsuario == "Empresa") Tecnico.TIPO_EMPRESA
                                      else Tecnico.TIPO_TECNICO
                )
                repoTecnicos.registrarTecnico(tecnico) { r ->
                    if (r.isSuccess) estado = estado.copy(tecnicoDocId = usuario.uid)
                    terminar(r)
                }
            }
        }
    }

    /**
     * Sube la foto a Storage y **persiste inmediatamente** la URL en
     * Firestore (`/usuarios/{uid}.fotoPerfil`). Antes esto solo se reflejaba
     * en el estado en memoria hasta que el usuario pulsaba "Guardar", lo que
     * dejaba la imagen huérfana si cerraba la pantalla sin guardar.
     */
    fun subirFotoPerfil(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        val error = getCadenas(AppEstado.idioma).perfilErrorGuardar
        estado = estado.copy(subiendoFoto = true, error = null)
        viewModelScope.launch {
            // Comprimida antes de subir: Storage rechaza las de más de 5 MB,
            // que es lo que pesa casi cualquier foto hecha con el móvil
            val jpeg = withContext(Dispatchers.IO) {
                try { Imagenes.comprimir(getApplication(), uri, LADO_FOTO_PERFIL) } catch (_: OutOfMemoryError) { null }
            }
            if (jpeg == null) {
                estado = estado.copy(subiendoFoto = false, error = error)
                return@launch
            }
            val ref = storage.reference.child("perfiles/$uid/foto_perfil")
            ref.putBytes(jpeg, StorageMetadata.Builder().setContentType("image/jpeg").build())
                .continueWithTask { subida ->
                    if (!subida.isSuccessful) throw subida.exception ?: Exception("Subida fallida")
                    ref.downloadUrl
                }
                .addOnSuccessListener { url ->
                    val urlString = url.toString()
                    val usuario = estado.usuario
                    if (usuario != null) {
                        val actualizado = usuario.copy(fotoPerfil = urlString)
                        repo.actualizarUsuario(actualizado) { _ ->
                            estado = estado.copy(fotoPerfil = urlString, usuario = actualizado, subiendoFoto = false)
                        }
                    } else {
                        estado = estado.copy(fotoPerfil = urlString, subiendoFoto = false)
                    }
                }
                // También si falla la URL de descarga (antes se quedaba cargando)
                .addOnFailureListener { estado = estado.copy(subiendoFoto = false, error = error) }
        }
    }

    fun limpiarMensajes() {
        estado = estado.copy(exito = false, error = null)
    }

    companion object {
        private const val LADO_FOTO_PERFIL = 800   // px: se ve como mucho a ~120 dp
    }
}
