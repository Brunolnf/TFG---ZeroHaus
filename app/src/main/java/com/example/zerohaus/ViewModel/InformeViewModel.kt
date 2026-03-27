package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Repositorios.RepositorioInformes

class InformeViewModel : ViewModel() {

    var informe by mutableStateOf<InformeEnergetico?>(null)
        private set
    var cargando by mutableStateOf(true)
        private set

    private val repo = RepositorioInformes()

    fun cargarUltimoInforme() {
        cargando = true
        repo.obtenerUltimoInforme { result ->
            informe = result
            cargando = false
        }
    }

    fun cargarInforme(inf: InformeEnergetico) {

        informe = inf
        cargando = false
    }
}