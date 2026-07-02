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
import kotlin.coroutines.resumeWithException

data class HistorialEstado(
    val informes: List<InformeEnergetico> = emptyList(),
    val informeSeleccionado: InformeEnergetico? = null,
    val informeComparar: InformeEnergetico? = null,
    val modoComparar: Boolean = false,
    val cargando: Boolean = false,
    val error: String? = null
)

class HistorialInformesViewModel : ViewModel() {

    var estado by mutableStateOf(HistorialEstado())
        private set

    private val repo = RepositorioInformes()

    init { cargarInformes() }

    fun cargarInformes(forzar: Boolean = false) {
        if (!forzar && estado.informes.isNotEmpty()) return
        viewModelScope.launch {
            estado = estado.copy(cargando = true, error = null)
            try {
                val lista = suspendCancellableCoroutine<List<InformeEnergetico>> { cont ->
                    repo.obtenerInformes(
                        onSuccess = { cont.resume(it) },
                        onError = { cont.resumeWithException(it) }
                    )
                }
                estado = estado.copy(informes = lista, cargando = false)
            } catch (e: Exception) {
                estado = estado.copy(
                    cargando = false,
                    error = "Error al cargar informes. Comprueba tu conexión e inténtalo de nuevo."
                )
            }
        }
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

    fun eliminarInforme(informeId: String) {
        viewModelScope.launch {
            val ok = suspendCancellableCoroutine { cont ->
                repo.eliminarInforme(informeId) { cont.resume(it) }
            }
            if (ok) {
                estado = estado.copy(
                    informes = estado.informes.filter { it.id != informeId },
                    informeSeleccionado = if (estado.informeSeleccionado?.id == informeId) null else estado.informeSeleccionado,
                    informeComparar = if (estado.informeComparar?.id == informeId) null else estado.informeComparar
                )
            }
        }
    }
}