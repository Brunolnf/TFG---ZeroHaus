package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Repositorios.RepositorioInformes

data class HistorialEstado(
    val informes: List<InformeEnergetico> = emptyList(),
    val informeSeleccionado: InformeEnergetico? = null,
    val informeComparar: InformeEnergetico? = null,
    val modoComparar: Boolean = false,
    val cargando: Boolean = true
)

class HistorialInformesViewModel : ViewModel() {

    var estado by mutableStateOf(HistorialEstado())
        private set

    private val repo = RepositorioInformes()

    fun cargarInformes() {
        estado = estado.copy(cargando = true)
        repo.obtenerInformes { lista -> estado = estado.copy(informes = lista, cargando = false) }
    }

    fun activarModoComparar() {
        estado = estado.copy(modoComparar = true, informeSeleccionado = null, informeComparar = null)
    }

    fun seleccionarParaComparar(informe: InformeEnergetico) {
        if (estado.informeSeleccionado == null) {
            estado = estado.copy(informeSeleccionado = informe)
        } else if (estado.informeComparar == null && informe.id != estado.informeSeleccionado?.id) {
            estado = estado.copy(informeComparar = informe)
        }
    }

    fun limpiarComparacion() {
        estado = estado.copy(informeSeleccionado = null, informeComparar = null, modoComparar = false)
    }
}