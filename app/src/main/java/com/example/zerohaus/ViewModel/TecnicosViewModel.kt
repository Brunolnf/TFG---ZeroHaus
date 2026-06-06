package com.example.zerohaus.ViewModel

import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.SolicitudPresupuesto
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Repositorios.*

enum class OrdenTecnicos { VALORACION, PROXIMIDAD, PROYECTOS }

data class TecnicosEstado(
    val tecnicos: List<Tecnico> = emptyList(),
    val busqueda: String = "",
    val filtro: String? = null,
    val orden: OrdenTecnicos = OrdenTecnicos.VALORACION,
    val latUsuario: Double = 40.4168,   // Madrid por defecto (app solo España)
    val lngUsuario: Double = -3.7038,
    val cargando: Boolean = true,
    val mensajeExito: String? = null,
    val error: String? = null
)

class TecnicosViewModel : ViewModel() {
    var estado by mutableStateOf(TecnicosEstado())
        private set
    private val repo = RepositorioTecnicos()
    private val repoResenas = RepositorioResenas()
    private val repoAuth = RepositorioAutenticacion()

    fun cargarTecnicos(forzar: Boolean = false) {
        if (!forzar && estado.tecnicos.isNotEmpty()) return
        val tieneDatos = estado.tecnicos.isNotEmpty()
        estado = estado.copy(cargando = !tieneDatos)
        repo.obtenerTecnicos { lista ->
            if (lista.isEmpty()) { estado = estado.copy(cargando = false); return@obtenerTecnicos }
            val previos = estado.tecnicos.associateBy { it.id }
            val tecnicosConDistancia = lista.map { t ->
                val prev = previos[t.id]
                val base = if (prev != null) t.copy(rating = prev.rating, opiniones = prev.opiniones) else t.copy(rating = 0.0, opiniones = 0)
                // Calcular distancia si tenemos ubicación del usuario
                if (estado.latUsuario != 0.0) {
                    val (tLat, tLng) = coordsEfectivasTecnico(base)
                    if (tLat != 0.0) base.copy(distanciaKm = calcularDistanciaKm(estado.latUsuario, estado.lngUsuario, tLat, tLng))
                    else base
                } else base
            }
            estado = estado.copy(tecnicos = tecnicosConDistancia, cargando = false)
            lista.forEach { t ->
                repoResenas.obtenerResenas(t.id) { resenas ->
                    val count = resenas.size
                    val avg = if (count == 0) 0.0 else Math.round(resenas.map { it.puntuacion }.average() * 10.0) / 10.0
                    estado = estado.copy(tecnicos = estado.tecnicos.map { if (it.id == t.id) it.copy(rating = avg, opiniones = count) else it })
                }
            }
        }
    }

    fun actualizarUbicacion(latRaw: Double, lngRaw: Double) {
        // Si la ubicación está fuera de España (emulador con posición por defecto),
        // usar Madrid como referencia ya que la app es solo para España.
        val enEspana = latRaw in 27.0..44.0 && lngRaw in -19.0..5.0
        val lat = if (enEspana) latRaw else 40.4168  // Madrid
        val lng = if (enEspana) lngRaw else -3.7038

        val tecnicosActualizados = estado.tecnicos.map { t ->
            val (tLat, tLng) = coordsEfectivasTecnico(t)
            if (tLat != 0.0) t.copy(distanciaKm = calcularDistanciaKm(lat, lng, tLat, tLng))
            else t
        }
        estado = estado.copy(latUsuario = lat, lngUsuario = lng, tecnicos = tecnicosActualizados)
    }

    /** Devuelve las coordenadas reales del técnico, o las de su ciudad si no tiene GPS. */
    private fun coordsEfectivasTecnico(t: Tecnico): Pair<Double, Double> {
        if (t.latitud != 0.0 || t.longitud != 0.0) return t.latitud to t.longitud
        val coords = RepositorioTecnicos.coordenadasDeCiudad(t.ciudad)
        return coords ?: (0.0 to 0.0)
    }

    private fun calcularDistanciaKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, result)
        return Math.round(result[0] / 1000.0 * 10.0) / 10.0
    }

    fun cambiarBusqueda(q: String) { estado = estado.copy(busqueda = q) }
    fun cambiarFiltro(f: String?) { estado = estado.copy(filtro = f) }
    fun cambiarOrden(o: OrdenTecnicos) { estado = estado.copy(orden = o) }

    fun tecnicosFiltrados(): List<Tecnico> {
        val q = estado.busqueda.trim().lowercase()
        val filtrados = estado.tecnicos
            .filter { q.isBlank() || it.nombre.lowercase().contains(q) || it.especialidades.any { e -> e.lowercase().contains(q) } }
            .filter { t -> estado.filtro == null || t.especialidades.contains(estado.filtro) }
        return when (estado.orden) {
            OrdenTecnicos.VALORACION -> filtrados.sortedByDescending { it.rating }
            OrdenTecnicos.PROYECTOS  -> filtrados.sortedByDescending { it.proyectosCompletados }
            OrdenTecnicos.PROXIMIDAD -> filtrados.sortedBy { t ->
                // Si el técnico tiene coordenadas (reales o por ciudad), ordenar por km.
                // Si no tiene ninguna ubicación conocida, mandarlo al final.
                val (tLat, _) = coordsEfectivasTecnico(t)
                if (tLat == 0.0) Double.MAX_VALUE else t.distanciaKm
            }
        }
    }

    fun solicitarPresupuesto(tecnico: Tecnico) { solicitarPresupuestoConDescripcion(tecnico, "Solicitud de presupuesto") }
    fun solicitarPresupuestoConDescripcion(tecnico: Tecnico, descripcion: String) {
        repoAuth.obtenerUsuario { u ->
            val s = SolicitudPresupuesto(
                uidCliente = repoAuth.getUid() ?: "",
                nombreCliente = u?.nombre ?: "Usuario",
                tecnicoId = tecnico.id,
                tecnicoUid = tecnico.uid,
                tecnicoNombre = tecnico.nombre,
                descripcion = descripcion
            )
            repo.solicitarPresupuesto(s) { r ->
                r.onSuccess { estado = estado.copy(mensajeExito = "Solicitud enviada a ${tecnico.nombre}") }
                    .onFailure { estado = estado.copy(error = it.message) }
            }
        }
    }
    fun limpiarMensaje() { estado = estado.copy(mensajeExito = null, error = null) }
}
