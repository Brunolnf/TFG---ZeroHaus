package com.example.zerohaus.ViewModel

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.Usuario
import com.example.zerohaus.Repositorios.RepositorioAutenticacion
import com.example.zerohaus.Repositorios.RepositorioTecnicos
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage

data class PerfilEstado(
    val usuario: Usuario? = null,
    val nombre: String = "",
    val email: String = "",
    val tipoUsuario: String = "",
    val fotoPerfil: String = "",
    // Campos exclusivos de técnico
    val especialidades: String = "",
    val descripcion: String = "",
    val telefono: String = "",
    val emailContacto: String = "",
    val tecnicoDocId: String = "",
    val ciudad: String = "",
    val paypalUsername: String = "",
    val bizumTelefono: String = "",
    // Estado UI
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
    private val repoTecnicos = RepositorioTecnicos()
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
                    fotoPerfil = usuario.fotoPerfil
                )
                if (usuario.tipoUsuario == "Técnico") {
                    // Buscamos por campo uid (no por ID de documento, que es diferente al Auth UID)
                    repoTecnicos.obtenerMiPerfilTecnico { tecnico ->
                        estado = estado.copy(
                            especialidades = tecnico?.especialidades?.joinToString(", ") ?: "",
                            descripcion = tecnico?.descripcion ?: "",
                            telefono = tecnico?.telefono ?: "",
                            emailContacto = tecnico?.emailContacto?.ifEmpty { usuario.email } ?: usuario.email,
                            tecnicoDocId = tecnico?.id ?: "",
                            ciudad = tecnico?.ciudad ?: "",
                            paypalUsername = tecnico?.paypalUsername ?: "",
                            bizumTelefono = tecnico?.bizumTelefono ?: "",
                            cargando = false
                        )
                    }
                } else {
                    estado = estado.copy(cargando = false)
                }
            } else {
                estado = estado.copy(cargando = false, error = "No se pudo cargar el perfil")
            }
        }
    }

    fun cambiarNombre(v: String) { estado = estado.copy(nombre = v, exito = false) }
    fun cambiarEspecialidades(v: String) { estado = estado.copy(especialidades = v, exito = false) }
    fun cambiarDescripcion(v: String) { estado = estado.copy(descripcion = v, exito = false) }
    fun cambiarTelefono(v: String) { estado = estado.copy(telefono = v, exito = false) }
    fun cambiarEmailContacto(v: String) { estado = estado.copy(emailContacto = v, exito = false) }
    fun cambiarPaypal(v: String) { estado = estado.copy(paypalUsername = v, exito = false) }
    fun cambiarBizum(v: String) { estado = estado.copy(bizumTelefono = v, exito = false) }
    fun cambiarCiudad(v: String) { estado = estado.copy(ciudad = v, exito = false) }

    fun guardarPerfil() {
        val usuario = estado.usuario ?: return
        estado = estado.copy(guardando = true, error = null, exito = false)
        val actualizado = usuario.copy(nombre = estado.nombre, fotoPerfil = estado.fotoPerfil)
        repo.actualizarUsuario(actualizado) { result ->
            result
                .onSuccess {
                    estado = estado.copy(usuario = actualizado)
                    if (usuario.tipoUsuario == "Técnico") {
                        val especialidadesLista = estado.especialidades
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }

                        // Si ya existe documento, hacemos UPDATE parcial (preserva rating, opiniones, etc.)
                        // Si no, hacemos SET completo.
                        if (estado.tecnicoDocId.isNotEmpty()) {
                            repoTecnicos.actualizarPerfilTecnico(
                                tecnicoId = estado.tecnicoDocId,
                                nombre = estado.nombre,
                                ciudad = estado.ciudad,
                                descripcion = estado.descripcion,
                                telefono = estado.telefono,
                                emailContacto = estado.emailContacto.ifEmpty { usuario.email },
                                especialidades = especialidadesLista,
                                paypalUsername = estado.paypalUsername.trim(),
                                bizumTelefono = estado.bizumTelefono.trim()
                            ) { _ -> estado = estado.copy(guardando = false, exito = true) }
                        } else {
                            val tecnico = Tecnico(
                                uid = usuario.uid,
                                nombre = estado.nombre,
                                ciudad = estado.ciudad,
                                especialidades = especialidadesLista,
                                descripcion = estado.descripcion,
                                telefono = estado.telefono,
                                emailContacto = estado.emailContacto.ifEmpty { usuario.email },
                                paypalUsername = estado.paypalUsername.trim(),
                                bizumTelefono = estado.bizumTelefono.trim()
                            )
                            repoTecnicos.registrarTecnico(tecnico) { _ ->
                                estado = estado.copy(guardando = false, exito = true)
                            }
                        }
                    } else {
                        estado = estado.copy(guardando = false, exito = true)
                    }
                }
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
