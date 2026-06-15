package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zerohaus.Modelos.*
import com.example.zerohaus.Repositorios.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class PreestudioEstado(
    val nombreVivienda: String = "Mi vivienda principal",
    val superficie: String = "100",
    val anio: String = "2000",
    val ventanas: String = "Vidrio simple",
    val aislamiento: String = "Aislamiento parcial",
    val calefaccion: String = "Caldera de gas",
    val acs: String = "Gas",
    val direccion: String = "",
    val orientacion: String = "Sur",
    val cargando: Boolean = false,
    val error: String? = null,
    val informeGenerado: InformeEnergetico? = null,
    val viviendas: List<Vivienda> = emptyList(),
    val viviendaSeleccionada: Vivienda? = null,   // null = formulario manual / nueva vivienda
    val cargandoViviendas: Boolean = false
)

class PreestudioViewModel : ViewModel() {

    var estado by mutableStateOf(PreestudioEstado())
        private set

    private val repoViviendas = RepositorioViviendas()
    private val repoInformes = RepositorioInformes()

    fun cambiarNombre(v: String) { estado = estado.copy(nombreVivienda = v) }
    fun cambiarSuperficie(v: String) { estado = estado.copy(superficie = v) }
    fun cambiarAnio(v: String) { estado = estado.copy(anio = v) }
    fun cambiarVentanas(v: String) { estado = estado.copy(ventanas = v) }
    fun cambiarAislamiento(v: String) { estado = estado.copy(aislamiento = v) }
    fun cambiarCalefaccion(v: String) { estado = estado.copy(calefaccion = v) }
    fun cambiarAcs(v: String) { estado = estado.copy(acs = v) }
    fun cambiarDireccion(v: String) { estado = estado.copy(direccion = v) }
    fun cambiarOrientacion(v: String) { estado = estado.copy(orientacion = v) }

    fun generarInforme() {
        if (estado.cargando) return  // evitar llamadas duplicadas

        val superficie = estado.superficie.toIntOrNull()
        val anio = estado.anio.toIntOrNull()

        when {
            estado.nombreVivienda.isBlank() ->
                { estado = estado.copy(error = "El nombre de la vivienda no puede estar vacío"); return }
            superficie == null || superficie <= 0 || superficie > 5000 ->
                { estado = estado.copy(error = "Introduce una superficie válida (1–5000 m²)"); return }
            anio == null || anio < 1900 || anio > java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) ->
                { estado = estado.copy(error = "Introduce un año de construcción válido (1900–${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)})"); return }
        }

        estado = estado.copy(cargando = true, error = null)

        val viviendaBase = Vivienda(
            nombre          = estado.nombreVivienda,
            superficie      = superficie!!,
            anioConstruccion = anio!!,
            tipoVentanas    = estado.ventanas,
            aislamiento     = estado.aislamiento,
            calefaccion     = estado.calefaccion,
            acs             = estado.acs,
            direccion       = estado.direccion,
            orientacion     = estado.orientacion
        )

        viewModelScope.launch {
            // Si el usuario eligió una vivienda existente, la reutilizamos sin crear una nueva
            val viviendaConId: Vivienda
            val idExistente = estado.viviendaSeleccionada?.id

            if (!idExistente.isNullOrEmpty()) {
                // Solo usar el ID; NO escribir en Firestore (no crear ni sobrescribir)
                viviendaConId = viviendaBase.copy(id = idExistente)
            } else {
                // Vivienda nueva → guardar en Firestore
                val resultVivienda = suspendCancellableCoroutine { cont ->
                    repoViviendas.guardarVivienda(viviendaBase) { cont.resume(it) }
                }
                val viviendaId = resultVivienda.getOrElse { e ->
                    estado = estado.copy(error = e.message, cargando = false)
                    return@launch
                }
                viviendaConId = viviendaBase.copy(id = viviendaId)
            }

            val resultInforme = suspendCancellableCoroutine { cont ->
                repoInformes.generarInforme(viviendaConId) { cont.resume(it) }
            }
            resultInforme
                .onSuccess { informe -> estado = estado.copy(informeGenerado = informe, cargando = false) }
                .onFailure { e -> estado = estado.copy(error = e.message, cargando = false) }
        }
    }

    fun limpiarInforme() {
        estado = estado.copy(informeGenerado = null)
    }

    /** Carga las viviendas guardadas del usuario para el selector. */
    fun cargarViviendas() {
        estado = estado.copy(cargandoViviendas = true)
        repoViviendas.obtenerViviendas { lista ->
            estado = estado.copy(viviendas = lista, cargandoViviendas = false)
        }
    }

    /** Rellena el formulario con los datos de una vivienda existente. */
    fun seleccionarViviendaExistente(vivienda: Vivienda) {
        estado = estado.copy(
            viviendaSeleccionada = vivienda,
            nombreVivienda   = vivienda.nombre,
            superficie       = vivienda.superficie.toString(),
            anio             = vivienda.anioConstruccion.toString(),
            ventanas         = vivienda.tipoVentanas.ifBlank { "Vidrio simple" },
            aislamiento      = vivienda.aislamiento.ifBlank { "Aislamiento parcial" },
            calefaccion      = vivienda.calefaccion.ifBlank { "Caldera de gas" },
            acs              = vivienda.acs.ifBlank { "Gas" },
            direccion        = vivienda.direccion,
            orientacion      = vivienda.orientacion.ifBlank { "Sur" },
            error            = null
        )
    }

    /** Limpia la selección y resetea el formulario para introducir una vivienda nueva. */
    fun usarNuevaVivienda() {
        estado = PreestudioEstado(
            viviendas = estado.viviendas,
            viviendaSeleccionada = null
        )
    }
}