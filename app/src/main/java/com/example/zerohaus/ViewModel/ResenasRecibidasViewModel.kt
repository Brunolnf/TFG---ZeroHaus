package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Resena
import com.example.zerohaus.Repositorios.RepositorioResenas
import com.example.zerohaus.Repositorios.RepositorioTecnicos

data class ResenasRecibidasEstado(
    val resenas: List<Resena> = emptyList(),
    val media: Double = 0.0,
    val totales: Int = 0,
    val distribucion: Map<Int, Int> = emptyMap(), // 1..5 -> nº reseñas
    val cargando: Boolean = true
)

class ResenasRecibidasViewModel : ViewModel() {

    var estado by mutableStateOf(ResenasRecibidasEstado())
        private set

    private val repoResenas = RepositorioResenas()
    private val repoTecnicos = RepositorioTecnicos()

    fun cargar() {
        estado = estado.copy(cargando = true)
        repoTecnicos.obtenerMiPerfilTecnico { miPerfil ->
            if (miPerfil == null) {
                estado = ResenasRecibidasEstado(cargando = false)
                return@obtenerMiPerfilTecnico
            }
            repoResenas.obtenerResenas(miPerfil.id) { lista ->
                val media = if (lista.isEmpty()) 0.0
                            else Math.round(lista.map { it.puntuacion }.average() * 10.0) / 10.0
                val distribucion = (1..5).associateWith { p -> lista.count { it.puntuacion == p } }
                estado = ResenasRecibidasEstado(
                    resenas = lista,
                    media = media,
                    totales = lista.size,
                    distribucion = distribucion,
                    cargando = false
                )
            }
        }
    }
}
