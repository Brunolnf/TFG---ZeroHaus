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
    val provincia: String = "",
    val orientacion: String = "Sur",
    val iluminacion: String = "Mixta",
    val tipoVivienda: String = "Piso interior",
    val refrigeracion: String = "Sin refrigeración",
    val fotovoltaica: String = "Sin fotovoltaica",
    val ocupantes: String = "3",
    val electrodomesticos: String = "Clase B-C",
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
    fun cambiarProvincia(v: String) { estado = estado.copy(provincia = v) }
    fun cambiarOrientacion(v: String) { estado = estado.copy(orientacion = v) }
    fun cambiarIluminacion(v: String) { estado = estado.copy(iluminacion = v) }
    fun cambiarTipoVivienda(v: String) { estado = estado.copy(tipoVivienda = v) }
    fun cambiarRefrigeracion(v: String) { estado = estado.copy(refrigeracion = v) }
    fun cambiarFotovoltaica(v: String) { estado = estado.copy(fotovoltaica = v) }
    fun cambiarOcupantes(v: String) { estado = estado.copy(ocupantes = v) }
    fun cambiarElectrodomesticos(v: String) { estado = estado.copy(electrodomesticos = v) }

    fun generarInforme() {
        if (estado.cargando) return  // evitar llamadas duplicadas

        val superficie = estado.superficie.toIntOrNull()
        val anio = estado.anio.toIntOrNull()
        val ocupantes = estado.ocupantes.toIntOrNull()
        val algo = com.example.zerohaus.Repositorios.AlgoritmoEnergetico

        when {
            estado.nombreVivienda.isBlank() ->
                { estado = estado.copy(error = "El nombre de la vivienda no puede estar vacío"); return }
            superficie == null || superficie <= 0 || superficie > 5000 ->
                { estado = estado.copy(error = "Introduce una superficie válida (1–5000 m²)"); return }
            anio == null || anio < 1900 || anio > java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) ->
                { estado = estado.copy(error = "Introduce un año de construcción válido (1900–${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)})"); return }
            estado.provincia !in algo.provinciasOrdenadas ->
                { estado = estado.copy(error = "Selecciona una provincia válida de la lista"); return }
            estado.iluminacion !in algo.opcionesIluminacion ->
                { estado = estado.copy(error = "Selecciona el tipo de iluminación"); return }
            estado.tipoVivienda !in algo.opcionesTipoVivienda ->
                { estado = estado.copy(error = "Selecciona el tipo de vivienda"); return }
            estado.refrigeracion !in algo.opcionesRefrigeracion ->
                { estado = estado.copy(error = "Selecciona el sistema de refrigeración"); return }
            estado.fotovoltaica !in algo.opcionesFotovoltaica ->
                { estado = estado.copy(error = "Indica si hay instalación fotovoltaica"); return }
            estado.electrodomesticos !in algo.opcionesElectrodomesticos ->
                { estado = estado.copy(error = "Selecciona la clase de electrodomésticos"); return }
            ocupantes == null || ocupantes !in 1..20 ->
                { estado = estado.copy(error = "Introduce un número de ocupantes válido (1–20)"); return }
        }

        estado = estado.copy(cargando = true, error = null)

        val viviendaBase = Vivienda(
            nombre            = estado.nombreVivienda,
            superficie        = superficie!!,
            anioConstruccion  = anio!!,
            tipoVentanas      = estado.ventanas,
            aislamiento       = estado.aislamiento,
            calefaccion       = estado.calefaccion,
            acs               = estado.acs,
            direccion         = estado.direccion,
            provincia         = estado.provincia,
            orientacion       = estado.orientacion,
            iluminacion       = estado.iluminacion,
            tipoVivienda      = estado.tipoVivienda,
            refrigeracion     = estado.refrigeracion,
            fotovoltaica      = estado.fotovoltaica,
            ocupantes         = ocupantes!!,
            electrodomesticos = estado.electrodomesticos
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
            provincia        = vivienda.provincia,
            orientacion      = vivienda.orientacion.ifBlank { "Sur" },
            iluminacion      = vivienda.iluminacion.ifBlank { "Mixta" },
            tipoVivienda     = vivienda.tipoVivienda.ifBlank { "Piso interior" },
            refrigeracion    = vivienda.refrigeracion.ifBlank { "Sin refrigeración" },
            fotovoltaica     = vivienda.fotovoltaica.ifBlank { "Sin fotovoltaica" },
            ocupantes        = if (vivienda.ocupantes > 0) vivienda.ocupantes.toString() else "3",
            electrodomesticos = vivienda.electrodomesticos.ifBlank { "Clase B-C" },
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