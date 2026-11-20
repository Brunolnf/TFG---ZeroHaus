
package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Repositorios.RepositorioInformes
import com.example.zerohaus.Util.GrupoVivienda
import com.example.zerohaus.Util.agruparPorVivienda
import com.example.zerohaus.Util.conservandoAnterior
import com.example.zerohaus.Util.listadoDe
import com.example.zerohaus.Util.grupoElegido
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Estado de la pantalla de gráficas.
 */
data class GraficasEstado(
    val informes: List<InformeEnergetico> = emptyList(),
    val cargando: Boolean = false,
    /** Los datos vienen de la caché del móvil (el servidor no respondió a tiempo). */
    val datosGuardados: Boolean = false,
    /** No se han podido leer los informes (distinto de «todavía no tienes informes»). */
    val errorCarga: Boolean = false,
    /** Vivienda elegida en el selector (null = la del informe más reciente). */
    val viviendaElegida: String? = null,
    // Calculados solo cuando cambian los informes o la vivienda elegida (no en cada recomposición)
    val grupos: List<GrupoVivienda> = emptyList(),
    val grupoActual: GrupoVivienda? = null
)

/**
 * Carga los informes del usuario para las gráficas de evolución.
 */
class GraficasViewModel : ViewModel() {

    var estado by mutableStateOf(GraficasEstado())
        private set

    private val repo = RepositorioInformes()

    init { cargarDatos() }

    fun cargarDatos(forzar: Boolean = false) {
        if (!forzar && estado.informes.isNotEmpty()) return
        viewModelScope.launch {
            estado = estado.copy(cargando = true, errorCarga = false)
            val lectura = suspendCancellableCoroutine { cont ->
                repo.obtenerInformesConOrigen { cont.resume(it) }
            }
            val listado = conservandoAnterior(estado.informes, listadoDe(lectura))
            val informes = listado.informes.sortedBy { it.fechaGeneracion }
            val grupos = agruparPorVivienda(informes)
            estado = estado.copy(
                informes = informes,
                grupos = grupos,
                grupoActual = grupoElegido(grupos, estado.viviendaElegida),
                cargando = false,
                datosGuardados = listado.datosGuardados,
                errorCarga = listado.errorCarga
            )
        }
    }

    fun elegirVivienda(viviendaId: String) {
        estado = estado.copy(viviendaElegida = viviendaId, grupoActual = grupoElegido(estado.grupos, viviendaId))
    }
}
