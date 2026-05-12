package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Repositorios.RepositorioTecnicos

data class EditarPerfilTecnicoEstado(
    val tecnico: Tecnico? = null,
    val nombre: String = "",
    val ciudad: String = "",
    val descripcion: String = "",
    val telefono: String = "",
    val emailContacto: String = "",
    val especialidades: List<String> = emptyList(),
    val nuevaEspecialidad: String = "",
    val cargando: Boolean = true,
    val guardando: Boolean = false,
    val mensaje: String? = null,
    val error: String? = null
)

class EditarPerfilTecnicoViewModel : ViewModel() {

    var estado by mutableStateOf(EditarPerfilTecnicoEstado())
        private set

    private val repo = RepositorioTecnicos()

    fun cargar() {
        estado = estado.copy(cargando = true)
        repo.obtenerMiPerfilTecnico { tec ->
            if (tec == null) {
                estado = estado.copy(cargando = false, error = "No se encontró tu perfil de técnico")
                return@obtenerMiPerfilTecnico
            }
            estado = EditarPerfilTecnicoEstado(
                tecnico = tec,
                nombre = tec.nombre,
                ciudad = tec.ciudad,
                descripcion = tec.descripcion,
                telefono = tec.telefono,
                emailContacto = tec.emailContacto,
                especialidades = tec.especialidades,
                cargando = false
            )
        }
    }

    fun cambiarNombre(v: String) { estado = estado.copy(nombre = v) }
    fun cambiarCiudad(v: String) { estado = estado.copy(ciudad = v) }
    fun cambiarDescripcion(v: String) { estado = estado.copy(descripcion = v) }
    fun cambiarTelefono(v: String) { estado = estado.copy(telefono = v) }
    fun cambiarEmail(v: String) { estado = estado.copy(emailContacto = v) }
    fun cambiarNuevaEspecialidad(v: String) { estado = estado.copy(nuevaEspecialidad = v) }

    fun anadirEspecialidad() {
        val nueva = estado.nuevaEspecialidad.trim()
        if (nueva.isBlank() || nueva in estado.especialidades) return
        estado = estado.copy(
            especialidades = estado.especialidades + nueva,
            nuevaEspecialidad = ""
        )
    }

    fun quitarEspecialidad(esp: String) {
        estado = estado.copy(especialidades = estado.especialidades - esp)
    }

    fun guardar() {
        val tec = estado.tecnico ?: return
        if (estado.nombre.isBlank()) {
            estado = estado.copy(error = "El nombre no puede estar vacío"); return
        }
        estado = estado.copy(guardando = true, error = null)
        repo.actualizarPerfilTecnico(
            tecnicoId = tec.id,
            nombre = estado.nombre.trim(),
            ciudad = estado.ciudad.trim(),
            descripcion = estado.descripcion.trim(),
            telefono = estado.telefono.trim(),
            emailContacto = estado.emailContacto.trim(),
            especialidades = estado.especialidades,
            paypalUsername = tec.paypalUsername,
            bizumTelefono = tec.bizumTelefono
        ) { result ->
            result.onSuccess {
                estado = estado.copy(guardando = false, mensaje = "Perfil actualizado")
                cargar()
            }
            result.onFailure { e ->
                estado = estado.copy(guardando = false, error = e.message)
            }
        }
    }

    fun limpiarMensaje() { estado = estado.copy(mensaje = null, error = null) }
}
