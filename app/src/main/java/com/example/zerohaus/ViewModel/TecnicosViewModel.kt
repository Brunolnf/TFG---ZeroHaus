package com.example.zerohaus.ViewModel

import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.SolicitudPresupuesto
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Repositorios.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

enum class OrdenTecnicos { VALORACION, PROXIMIDAD, PROYECTOS }

data class TecnicosEstado(
    val tecnicos: List<Tecnico> = emptyList(),
    val busqueda: String = "",
    val filtro: String? = null,
    val orden: OrdenTecnicos = OrdenTecnicos.VALORACION,
    val latUsuario: Double = 40.4168,   // Madrid por defecto (app solo España)
    val lngUsuario: Double = -3.7038,
    val cargando: Boolean = false,
    val enviandoSolicitud: Boolean = false,
    val mensajeExito: String? = null,
    val error: String? = null
)

class TecnicosViewModel : ViewModel() {
    var estado by mutableStateOf(TecnicosEstado())
        private set
    private val repo = RepositorioTecnicos()
    private val repoAuth = RepositorioAutenticacion()
    private val auth = FirebaseAuth.getInstance()

    private var listenerTec: ListenerRegistration? = null
    private var listenerRes: ListenerRegistration? = null
    private var uidEscuchado: String? = null

    /**
     * El VM es app-scoped (vive lo que la Activity). Si el listener se enganchó
     * con un usuario distinto al actual —o sin usuario, en cold-start antes del
     * login— las reglas de Firestore devuelven PERMISSION_DENIED, el listener
     * queda muerto y la lista se queda vacía para siempre. Reaccionamos a los
     * cambios de Auth re-enganchando con el uid nuevo.
     */
    private val authListener = FirebaseAuth.AuthStateListener { fa ->
        val nuevo = fa.currentUser?.uid
        if (nuevo != uidEscuchado) reengancharListeners()
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    /**
     * Suscribe el directorio de técnicos en tiempo real. Antes era un get cacheado:
     * un técnico recién registrado (caso "Adán") no aparecía hasta reabrir la app.
     */
    fun cargarTecnicos(forzar: Boolean = false) {
        val uidActual = auth.currentUser?.uid
        // Reenganchar si: el llamante lo pide, no hay listener, o cambió el uid
        // (caso típico: el VM es app-scoped y el usuario se ha registrado/cambiado
        // de cuenta sin que el AuthStateListener haya llegado a disparar todavía).
        if (forzar || listenerTec == null || uidActual != uidEscuchado) {
            reengancharListeners()
        }
    }

    private fun reengancharListeners() {
        listenerTec?.remove(); listenerTec = null
        listenerRes?.remove(); listenerRes = null
        uidEscuchado = auth.currentUser?.uid
        if (uidEscuchado == null) {
            // Sin sesión no podemos leer (reglas exigen auth). Esperamos al
            // siguiente disparo del AuthStateListener.
            estado = estado.copy(tecnicos = emptyList(), cargando = false)
            return
        }
        estado = estado.copy(cargando = estado.tecnicos.isEmpty())
        val (regT, regR) = repo.escucharTecnicos { lista ->
            val procesados = lista.map { t ->
                if (estado.latUsuario != 0.0) {
                    val (tLat, tLng) = coordsEfectivasTecnico(t)
                    if (tLat != 0.0) t.copy(distanciaKm = calcularDistanciaKm(estado.latUsuario, estado.lngUsuario, tLat, tLng))
                    else t
                } else t
            }
            estado = estado.copy(tecnicos = procesados, cargando = false)
        }
        listenerTec = regT
        listenerRes = regR
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

    /**
     * True si sabemos situar al técnico en el mapa (lat/lng propias o ciudad
     * reconocida). Hace falta para distinguir "distanciaKm == 0 porque está al
     * lado" de "distanciaKm == 0 porque no se ha podido calcular": ambos casos
     * tenían el mismo valor y la pastilla de km no aparecía en cards cercanas,
     * dando la sensación de que el orden por proximidad no funcionaba.
     */
    fun tieneUbicacionConocida(t: Tecnico): Boolean {
        val (tLat, _) = coordsEfectivasTecnico(t)
        return tLat != 0.0
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
        // Guard contra doble tap: el dialog se cierra al pulsar, pero un tap rápido
        // puede disparar dos onClick antes de la recomposición, creando dos docs
        // de solicitud idénticos. Bloqueamos reentradas mientras hay una en vuelo.
        if (estado.enviandoSolicitud) return
        estado = estado.copy(enviandoSolicitud = true)
        // obtenerUsuarioUnaVez (no obtenerUsuario): el callback debe ejecutarse una
        // sola vez. El doble disparo caché+servidor crearía dos solicitudes idénticas.
        repoAuth.obtenerUsuarioUnaVez { u ->
            val s = SolicitudPresupuesto(
                uidCliente = repoAuth.getUid() ?: "",
                nombreCliente = u?.nombre ?: "Usuario",
                tecnicoId = tecnico.id,
                tecnicoUid = tecnico.uid,
                tecnicoNombre = tecnico.nombre,
                descripcion = descripcion
            )
            repo.solicitarPresupuesto(s) { r ->
                r.onSuccess { estado = estado.copy(enviandoSolicitud = false, mensajeExito = "Solicitud enviada a ${tecnico.nombre}") }
                    .onFailure { estado = estado.copy(enviandoSolicitud = false, error = it.message) }
            }
        }
    }
    fun limpiarMensaje() { estado = estado.copy(mensajeExito = null, error = null) }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
        listenerTec?.remove()
        listenerRes?.remove()
        listenerTec = null
        listenerRes = null
    }
}
