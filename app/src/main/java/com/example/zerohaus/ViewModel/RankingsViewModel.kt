package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Repositorios.RepositorioTecnicos
import com.google.firebase.firestore.ListenerRegistration

class RankingsViewModel : ViewModel() {

    var ranking by mutableStateOf<List<Tecnico>>(emptyList())
        private set
    var cargando by mutableStateOf(false)
        private set

    private val repo = RepositorioTecnicos()
    private var listenerTec: ListenerRegistration? = null
    private var listenerRes: ListenerRegistration? = null

    init { cargarRanking() }

    /**
     * Tiempo real: el ranking se reordena solo cuando llega una reseña nueva o
     * cuando aparece/se modifica un técnico. Demo-friendly: el cliente ve el
     * cambio de rating de un técnico en cuanto otro cliente publica la reseña.
     */
    fun cargarRanking(forzar: Boolean = false) {
        if (listenerTec != null) return
        cargando = true
        val (regT, regR) = repo.escucharTecnicos { lista ->
            ranking = lista.sortedByDescending { it.rating }
            cargando = false
        }
        listenerTec = regT
        listenerRes = regR
    }

    override fun onCleared() {
        super.onCleared()
        listenerTec?.remove()
        listenerRes?.remove()
        listenerTec = null
        listenerRes = null
    }
}
