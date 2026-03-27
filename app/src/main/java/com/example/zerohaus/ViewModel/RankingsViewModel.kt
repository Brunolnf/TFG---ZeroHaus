package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Repositorios.RepositorioTecnicos

class RankingsViewModel : ViewModel() {

    var ranking by mutableStateOf<List<Tecnico>>(emptyList())
        private set
    var cargando by mutableStateOf(true)
        private set

    private val repo = RepositorioTecnicos()

    fun cargarRanking() {
        cargando = true
        repo.obtenerRanking { lista ->
            ranking = lista
            cargando = false
        }
    }
}
