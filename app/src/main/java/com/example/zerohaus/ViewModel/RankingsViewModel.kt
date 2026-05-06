package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Repositorios.RepositorioResenas
import com.example.zerohaus.Repositorios.RepositorioTecnicos

class RankingsViewModel : ViewModel() {

    var ranking by mutableStateOf<List<Tecnico>>(emptyList())
        private set
    var cargando by mutableStateOf(true)
        private set

    private val repo = RepositorioTecnicos()
    private val repoResenas = RepositorioResenas()

    fun cargarRanking() {
        cargando = true
        repo.obtenerRanking { lista ->
            if (lista.isEmpty()) { cargando = false; return@obtenerRanking }
            ranking = lista.map { it.copy(rating = 0.0, opiniones = 0) }
            cargando = false
            lista.forEach { t ->
                repoResenas.obtenerResenas(t.id) { resenas ->
                    val count = resenas.size
                    val avg = if (count == 0) 0.0
                              else Math.round(resenas.map { it.puntuacion }.average() * 10.0) / 10.0
                    ranking = ranking.map { if (it.id == t.id) it.copy(rating = avg, opiniones = count) else it }
                        .sortedByDescending { it.rating }
                }
            }
        }
    }
}
