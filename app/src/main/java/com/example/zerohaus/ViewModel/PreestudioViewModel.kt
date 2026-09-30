package com.example.zerohaus.ViewModel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zerohaus.Modelos.*
import com.example.zerohaus.Repositorios.*
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.getCadenas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Estado del formulario del preestudio.
 */
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
    val cargandoViviendas: Boolean = false,
    // Factura de la luz leída con IA (opcional): su precio real sustituye al
    // medio en el cálculo del coste
    val factura: DatosFactura? = null,
    val fechaFactura: Long = 0L,
    val leyendoFactura: Boolean = false,
    val errorFactura: ErrorFactura? = null
)

/**
 * Valida el preestudio, guarda la vivienda si es nueva y genera el informe.
 */
class PreestudioViewModel : ViewModel() {

    var estado by mutableStateOf(PreestudioEstado())
        private set

    private val repoViviendas = RepositorioViviendas()
    private val repoInformes = RepositorioInformes()
    private val repoFactura = RepositorioFactura()

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
        val c = getCadenas(AppEstado.idioma)
        val anioActual = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

        val error = when {
            estado.nombreVivienda.isBlank() -> c.errNombreVivienda
            superficie == null || superficie <= 0 || superficie > 5000 -> c.errSuperficie
            anio == null || anio < 1900 || anio > anioActual -> "${c.errAnio} (1900–$anioActual)"
            estado.provincia !in algo.provinciasOrdenadas -> "${c.errSeleccionaCampo}: ${c.preProvincia}"
            estado.iluminacion !in algo.opcionesIluminacion -> "${c.errSeleccionaCampo}: ${c.preIluminacion}"
            estado.tipoVivienda !in algo.opcionesTipoVivienda -> "${c.errSeleccionaCampo}: ${c.preTipoVivienda}"
            estado.refrigeracion !in algo.opcionesRefrigeracion -> "${c.errSeleccionaCampo}: ${c.preRefrigeracion}"
            estado.fotovoltaica !in algo.opcionesFotovoltaica -> "${c.errSeleccionaCampo}: ${c.preFotovoltaica}"
            estado.electrodomesticos !in algo.opcionesElectrodomesticos -> "${c.errSeleccionaCampo}: ${c.preElectrodomesticos}"
            ocupantes == null || ocupantes !in 1..20 -> c.errOcupantes
            else -> null
        }
        if (error != null) { estado = estado.copy(error = error); return }

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
            electrodomesticos = estado.electrodomesticos,
            consumoLuzFacturaKwh = estado.factura?.consumoAnualKwh ?: 0.0,
            precioLuzFactura     = estado.factura?.precioMedio ?: 0.0,
            potenciaContratadaKw = estado.factura?.potenciaKw ?: 0.0,
            fechaFactura         = if (estado.factura != null) estado.fechaFactura else 0L
        )

        viewModelScope.launch {
            // Si el usuario eligió una vivienda existente, la reutilizamos sin crear una nueva
            val viviendaConId: Vivienda
            val idExistente = estado.viviendaSeleccionada?.id

            if (!idExistente.isNullOrEmpty()) {
                // Solo usar el ID; NO escribir en Firestore (no crear ni sobrescribir)
                viviendaConId = viviendaBase.copy(id = idExistente)
                // Salvo la factura: si se ha añadido, cambiado o quitado, se
                // guarda en la vivienda para los próximos informes
                val anterior = estado.viviendaSeleccionada
                if (anterior != null && (anterior.precioLuzFactura != viviendaConId.precioLuzFactura
                        || anterior.consumoLuzFacturaKwh != viviendaConId.consumoLuzFacturaKwh)) {
                    repoViviendas.actualizarFactura(viviendaConId)
                }
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

    /**
     * Lee con IA la factura de la luz elegida (foto o PDF). El archivo se
     * prepara fuera del hilo principal (las fotos se reducen) y se envía al
     * servidor, que devuelve solo los números. [temporal] es la foto hecha con
     * la cámara: se borra en cuanto está preparada para no dejarla en el móvil.
     */
    fun leerFactura(context: Context, uri: Uri, temporal: java.io.File? = null) {
        if (estado.leyendoFactura) return
        estado = estado.copy(leyendoFactura = true, errorFactura = null)
        viewModelScope.launch {
            val archivo = try {
                withContext(Dispatchers.IO) {
                    try { RepositorioFactura.prepararArchivo(context, uri) } finally { temporal?.delete() }
                }
            } catch (e: ErrorFacturaException) {
                estado = estado.copy(leyendoFactura = false, errorFactura = e.tipo)
                return@launch
            } catch (e: Exception) {
                estado = estado.copy(leyendoFactura = false, errorFactura = ErrorFactura.NO_LEGIBLE)
                return@launch
            }
            repoFactura.leer(archivo.first, archivo.second) { r ->
                r.onSuccess {
                    estado = estado.copy(leyendoFactura = false, factura = it, fechaFactura = System.currentTimeMillis())
                }.onFailure {
                    estado = estado.copy(leyendoFactura = false, errorFactura = (it as? ErrorFacturaException)?.tipo ?: ErrorFactura.NO_DISPONIBLE)
                }
            }
        }
    }

    /** No hay cámara o el selector falló al abrirse. */
    fun errorAlElegirFactura() { estado = estado.copy(errorFactura = ErrorFactura.NO_LEGIBLE) }

    fun quitarFactura() { estado = estado.copy(factura = null, fechaFactura = 0L, errorFactura = null) }

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
            error            = null,
            // Su última factura (solo quedan los datos anuales, no los del periodo)
            factura          = if (vivienda.consumoLuzFacturaKwh > 0 && vivienda.precioLuzFactura > 0) DatosFactura(
                consumoKwh = 0.0, dias = 0, importeTotal = 0.0,
                potenciaKw = vivienda.potenciaContratadaKw,
                consumoAnualKwh = vivienda.consumoLuzFacturaKwh,
                precioMedio = vivienda.precioLuzFactura
            ) else null,
            fechaFactura     = vivienda.fechaFactura,
            errorFactura     = null
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