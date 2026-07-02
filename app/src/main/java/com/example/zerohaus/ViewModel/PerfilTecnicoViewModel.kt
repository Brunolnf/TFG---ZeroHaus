package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Resena
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Repositorios.*


data class PerfilTecnicoEstado(
    val tecnico: Tecnico? = null,
    val resenas: List<Resena> = emptyList(),
    val yaValorado: Boolean = false,
    val puedeValorar: Boolean = false,
    val cargando: Boolean = false,
    val enviandoResena: Boolean = false,
    val exitoResena: Boolean = false,
    val error: String? = null,
    val tieneCertificadosVerificados: Boolean = false
)

class PerfilTecnicoViewModel : ViewModel() {

    var estado by mutableStateOf(PerfilTecnicoEstado())
        private set

    private val repoTecnicos = RepositorioTecnicos()
    private val repoResenas = RepositorioResenas()
    private val repoAuth = RepositorioAutenticacion()
    private val repoCerts = RepositorioCertificados()

    fun cargarTecnico(tecnicoId: String) {
        estado = estado.copy(cargando = true)
        // Limpieza best-effort: borra reseñas duplicadas mías sobre este técnico que
        // pudieran haber quedado de antes del fix (1 reseña por proyecto).
        repoResenas.limpiarResenasDuplicadas(tecnicoId)
        repoTecnicos.obtenerTecnico(tecnicoId) { tecnico ->
            estado = estado.copy(tecnico = tecnico)
            repoResenas.obtenerResenas(tecnicoId) { resenas ->
                val realOpiniones = resenas.size
                val realRating = if (resenas.isEmpty()) 0.0
                                 else Math.round(resenas.map { it.puntuacion }.average() * 10.0) / 10.0
                // Mostramos la cifra calculada en tiempo real sobre lo que vemos.
                // El recálculo persistente en /tecnicos lo gestiona la Cloud
                // Function `on_resena_changed` cuando cambian las reseñas.
                val tecnicoCorregido = tecnico?.copy(opiniones = realOpiniones, rating = realRating)
                repoTecnicos.contarSolicitudesCompletadas(tecnicoId) { completadas ->
                    repoResenas.contarResenas(tecnicoId) { resenaCount ->
                        val uid = tecnico?.uid?.takeIf { it.isNotBlank() } ?: tecnicoId
                        repoCerts.tieneCertificadosVerificados(uid) { verificados ->
                            estado = estado.copy(
                                tecnico = tecnicoCorregido,
                                resenas = resenas,
                                yaValorado = resenaCount > 0,
                                puedeValorar = completadas > resenaCount,
                                tieneCertificadosVerificados = verificados,
                                cargando = false
                            )
                        }
                    }
                }
            }
        }
    }

    fun publicarResena(tecnicoId: String, puntuacion: Int, comentario: String) {
        // Guard contra doble tap: el botón "Publicar" cierra el diálogo al pulsarlo,
        // pero un tap rápido puede disparar dos onClick antes de la recomposición y
        // crear dos documentos /resenas idénticos para la misma solicitud completada.
        if (estado.enviandoResena) return
        if (!estado.puedeValorar) {
            estado = estado.copy(error = "No puedes valorar a este técnico todavía")
            return
        }
        estado = estado.copy(enviandoResena = true, error = null, exitoResena = false)
        // obtenerUsuarioUnaVez (no obtenerUsuario): el callback debe ejecutarse una
        // sola vez. El doble disparo caché+servidor publicaría dos reseñas idénticas.
        repoAuth.obtenerUsuarioUnaVez { usuario ->
            val resena = Resena(
                tecnicoId = tecnicoId,
                nombreUsuario = usuario?.nombre ?: "Usuario",
                puntuacion = puntuacion,
                comentario = comentario
            )
            repoResenas.publicarResena(resena) { result ->
                result
                    .onSuccess {
                        estado = estado.copy(
                            enviandoResena = false,
                            exitoResena = true,
                            yaValorado = true,
                            puedeValorar = false
                        )
                        cargarTecnico(tecnicoId)
                    }
                    .onFailure { estado = estado.copy(enviandoResena = false, error = it.message) }
            }
        }
    }

    fun limpiar() { estado = PerfilTecnicoEstado() }
}