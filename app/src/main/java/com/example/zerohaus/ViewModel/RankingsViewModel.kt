package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Repositorios.RepositorioTecnicos
import com.google.firebase.firestore.ListenerRegistration

/**
 * Ranking de profesionales por valoración media.
 */
class RankingsViewModel : ViewModel() {

    var ranking by mutableStateOf<List<Tecnico>>(emptyList())
        private set
    var cargando by mutableStateOf(false)
        private set

    private val repo = RepositorioTecnicos()
    private var listenerTec: ListenerRegistration? = null

    init { cargarRanking() }

    /**
     * Tiempo real: el ranking se reordena solo cuando aparece o cambia un
     * profesional, también cuando el servidor actualiza su nota tras una reseña.
     * Con [forzar] se vuelve a enganchar (botón «Reintentar»).
     */
    fun cargarRanking(forzar: Boolean = false) {
        if (listenerTec != null && !forzar) return
        listenerTec?.remove()
        cargando = true
        listenerTec = repo.escucharTecnicos { lista ->
            ranking = lista.sortedByDescending { it.rating }
            cargando = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerTec?.remove()
        listenerTec = null
    }
}
