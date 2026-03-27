
package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Repositorios.RepositorioInformes


data class GraficasEstado(
    val informes: List<InformeEnergetico> = emptyList(),
    val cargando: Boolean = true
)

class GraficasViewModel : ViewModel() {

    var estado by mutableStateOf(GraficasEstado())
        private set

    private val repo = RepositorioInformes()

    fun cargarDatos() {
        estado = estado.copy(cargando = true)
        repo.obtenerInformes { lista ->
            estado = estado.copy(informes = lista.sortedBy { it.fechaGeneracion }, cargando = false)
        }
    }
}