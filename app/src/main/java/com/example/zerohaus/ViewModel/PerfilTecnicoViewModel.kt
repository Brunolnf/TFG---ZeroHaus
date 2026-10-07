package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Resena
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.esVerificado
import com.example.zerohaus.Repositorios.*
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.getCadenas


/**
 * Estado del perfil público de un profesional.
 */
data class PerfilTecnicoEstado(
    val tecnico: Tecnico? = null,
    val resenas: List<Resena> = emptyList(),
    val yaValorado: Boolean = false,
    val cargando: Boolean = false,
    val enviandoResena: Boolean = false,
    val exitoResena: Boolean = false,
    val error: String? = null,
    val esMiPerfil: Boolean = false,
    // Chat con mensajes entre el cliente y el profesional: requisito para valorar
    val chatIdConversacion: String? = null
) {
    val puedeValorar: Boolean get() = !esMiPerfil && chatIdConversacion != null
}

/**
 * Perfil público de un profesional: datos, valoraciones, publicación de
 * reseñas (con chat previo) y registro de visitas y contactos.
 */
class PerfilTecnicoViewModel : ViewModel() {

    var estado by mutableStateOf(PerfilTecnicoEstado())
        private set

    private val repoTecnicos = RepositorioTecnicos()
    private val repoResenas = RepositorioResenas()
    private val repoAuth = RepositorioAutenticacion()
    private val repoChat = RepositorioChat()
    private val repoEstadisticas = RepositorioEstadisticas()
    private var visitaRegistrada: String? = null

    /** Contacto desde el perfil (chat o llamada) para las estadísticas del profesional. */
    fun registrarContacto(tecnicoId: String, tipo: String) {
        if (!estado.esMiPerfil) repoEstadisticas.registrarEvento(tecnicoId, tipo)
    }

    fun cargarTecnico(tecnicoId: String) {
        estado = estado.copy(cargando = true)
        // Las lecturas van a la vez (antes en cadena: perfil → reseñas → «ya
        // valorado» → chats) y «ya valorado» sale de las propias reseñas.
        var tecnico: Tecnico? = null
        var resenas: List<Resena> = emptyList()
        var chatId: String? = null
        var pendientes = 3
        fun terminar() {
            if (--pendientes > 0) return
            val miUid = repoAuth.getUid()
            val uidProfesional = tecnico?.uid?.takeIf { it.isNotBlank() } ?: tecnicoId
            val esMiPerfil = miUid == uidProfesional
            val rating = if (resenas.isEmpty()) 0.0
                         else Math.round(resenas.map { it.puntuacion }.average() * 10.0) / 10.0
            estado = estado.copy(
                tecnico = tecnico?.copy(opiniones = resenas.size, rating = rating),
                resenas = resenas,
                yaValorado = miUid != null && resenas.any { it.uid == miUid },
                esMiPerfil = esMiPerfil,
                chatIdConversacion = chatId,
                cargando = false
            )
            if (!esMiPerfil && tecnico != null && visitaRegistrada != tecnicoId) {
                visitaRegistrada = tecnicoId
                repoEstadisticas.registrarEvento(tecnicoId, RepositorioEstadisticas.VISITA)
            }
        }
        repoTecnicos.obtenerTecnico(tecnicoId) { t ->
            tecnico = t
            // Perfiles antiguos reclamados: el id del documento no es el uid del
            // profesional, y el chat está a nombre del uid
            val uid = t?.uid
            if (!uid.isNullOrBlank() && uid != tecnicoId) {
                pendientes++
                repoChat.buscarConversacionCon(uid) { c -> chatId = chatId ?: c; terminar() }
            }
            terminar()
        }
        repoResenas.obtenerResenas(tecnicoId) { r -> resenas = r; terminar() }
        repoChat.buscarConversacionCon(tecnicoId) { c -> chatId = chatId ?: c; terminar() }
    }

    fun publicarResena(tecnicoId: String, puntuacion: Int, comentario: String) {
        if (estado.enviandoResena) return
        val chatId = estado.chatIdConversacion
        if (!estado.puedeValorar || chatId == null) {
            estado = estado.copy(error = getCadenas(AppEstado.idioma).perfNoPuedesValorar)
            return
        }
        estado = estado.copy(enviandoResena = true, error = null, exitoResena = false)
        repoAuth.obtenerUsuarioUnaVez { usuario ->
            val resena = Resena(
                tecnicoId = tecnicoId,
                nombreUsuario = usuario?.nombre ?: "Usuario",
                puntuacion = puntuacion,
                comentario = comentario,
                chatId = chatId
            )
            repoResenas.publicarResena(resena) { result ->
                result
                    .onSuccess {
                        estado = estado.copy(
                            enviandoResena = false,
                            exitoResena = true,
                            yaValorado = true
                        )
                        cargarTecnico(tecnicoId)
                    }
                    .onFailure { estado = estado.copy(enviandoResena = false, error = getCadenas(AppEstado.idioma).authErrGenerico) }
            }
        }
    }

    fun limpiarMensajes() {
        estado = estado.copy(error = null)
    }
}
