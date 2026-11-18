package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.ContextoDenuncia
import com.example.zerohaus.Modelos.Moderacion
import com.example.zerohaus.Modelos.MotivoDenuncia
import com.example.zerohaus.Modelos.Resena
import com.example.zerohaus.Modelos.ResultadoDenuncia
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
    val chatIdConversacion: String? = null,
    // Uid del dueño del perfil (campo `uid`; vacío si el perfil no lo tiene: sin denunciar ni bloquear)
    val uidProfesional: String = "",
    // Uids que he bloqueado
    val bloqueados: Set<String> = emptySet()
) {
    val profesionalBloqueado: Boolean get() = uidProfesional.isNotEmpty() && uidProfesional in bloqueados
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
    private val repoModeracion = RepositorioModeracion()
    private val listenerBloqueos = repoModeracion.escucharMisBloqueos { b -> estado = estado.copy(bloqueados = b) }

    override fun onCleared() {
        listenerBloqueos.remove()
        super.onCleared()
    }

    /** Denuncia al profesional dueño del perfil (contexto perfil, referencia = id del perfil). */
    fun denunciarProfesional(tecnicoId: String, motivo: MotivoDenuncia, comentario: String, onResultado: (ResultadoDenuncia) -> Unit) {
        val objetivo = estado.uidProfesional
        if (objetivo.isEmpty() || estado.esMiPerfil) { onResultado(ResultadoDenuncia.ERROR); return }
        repoModeracion.denunciar(objetivo, ContextoDenuncia.PERFIL, tecnicoId, motivo, comentario, onResultado)
    }

    /** Denuncia al autor de una reseña ajena (contexto resena, referencia = id de la reseña). */
    fun denunciarResena(tecnicoId: String, resena: Resena, motivo: MotivoDenuncia, comentario: String, onResultado: (ResultadoDenuncia) -> Unit) {
        val referencia = Moderacion.referenciaResena(resena.id, resena.uid, tecnicoId)
        repoModeracion.denunciar(resena.uid, ContextoDenuncia.RESENA, referencia, motivo, comentario, onResultado)
    }

    /** Bloquea o desbloquea al profesional; onResultado recibe (ahora bloqueado, ok). */
    fun alternarBloqueo(onResultado: (Boolean, Boolean) -> Unit) {
        val objetivo = estado.uidProfesional
        if (objetivo.isEmpty() || estado.esMiPerfil) { onResultado(false, false); return }
        if (estado.profesionalBloqueado) repoModeracion.desbloquear(objetivo) { ok -> onResultado(false, ok) }
        else repoModeracion.bloquear(objetivo) { ok -> onResultado(true, ok) }
    }
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
                uidProfesional = tecnico?.uid.orEmpty(),
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
