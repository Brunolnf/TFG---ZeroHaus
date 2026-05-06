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
    val cargando: Boolean = true,
    val enviandoResena: Boolean = false,
    val exitoResena: Boolean = false,
    val error: String? = null
)

class PerfilTecnicoViewModel : ViewModel() {

    var estado by mutableStateOf(PerfilTecnicoEstado())
        private set

    private val repoTecnicos = RepositorioTecnicos()
    private val repoResenas = RepositorioResenas()
    private val repoAuth = RepositorioAutenticacion()

    fun cargarTecnico(tecnicoId: String) {
        estado = estado.copy(cargando = true)
        repoTecnicos.obtenerTecnico(tecnicoId) { tecnico ->
            estado = estado.copy(tecnico = tecnico)
            repoResenas.obtenerResenas(tecnicoId) { resenas ->
                val realOpiniones = resenas.size
                val realRating = if (resenas.isEmpty()) 0.0
                                 else Math.round(resenas.map { it.puntuacion }.average() * 10.0) / 10.0
                val tecnicoCorregido = tecnico?.copy(opiniones = realOpiniones, rating = realRating)
                repoResenas.actualizarRatingTecnico(tecnicoId)
                repoResenas.yaValorado(tecnicoId) { yaValorado ->
                    repoTecnicos.puedeValorar(tecnicoId) { puede ->
                        estado = estado.copy(
                            tecnico = tecnicoCorregido,
                            resenas = resenas,
                            yaValorado = yaValorado,
                            puedeValorar = puede,
                            cargando = false
                        )
                    }
                }
            }
        }
    }

    fun publicarResena(tecnicoId: String, puntuacion: Int, comentario: String) {
        if (!estado.puedeValorar || estado.yaValorado) {
            estado = estado.copy(error = "No puedes valorar a este técnico todavía")
            return
        }
        estado = estado.copy(enviandoResena = true, error = null, exitoResena = false)
        repoAuth.obtenerUsuario { usuario ->
            val resena = Resena(
                tecnicoId = tecnicoId,
                nombreUsuario = usuario?.nombre ?: "Usuario",
                puntuacion = puntuacion,
                comentario = comentario
            )
            repoResenas.publicarResena(resena) { result ->
                result
                    .onSuccess {
                        estado = estado.copy(enviandoResena = false, exitoResena = true, yaValorado = true)
                        cargarTecnico(tecnicoId)
                    }
                    .onFailure { estado = estado.copy(enviandoResena = false, error = it.message) }
            }
        }
    }

    fun limpiar() { estado = PerfilTecnicoEstado() }
}