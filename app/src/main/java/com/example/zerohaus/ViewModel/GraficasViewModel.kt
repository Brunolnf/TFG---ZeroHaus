
package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Repositorios.RepositorioInformes
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class GraficasEstado(
    val informes: List<InformeEnergetico> = emptyList(),
    val cargando: Boolean = true
)

class GraficasViewModel : ViewModel() {

    var estado by mutableStateOf(GraficasEstado())
        private set

    private val repo = RepositorioInformes()

    fun cargarDatos() {
        viewModelScope.launch {
            estado = estado.copy(cargando = true)
            val lista = suspendCancellableCoroutine { cont ->
                repo.obtenerInformes { cont.resume(it) }
            }
            estado = estado.copy(
                informes = lista.sortedBy { it.fechaGeneracion },
                cargando = false
            )
        }
    }
}