package com.example.zerohaus.ViewModel

import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.nivelPlan
import com.example.zerohaus.Repositorios.*
import com.example.zerohaus.Modelos.Especialidades
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.TextosEnergia
import com.example.zerohaus.Util.getCadenas
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

/**
 * Criterio de orden del directorio.
 */
enum class OrdenTecnicos { VALORACION, PROXIMIDAD }

/**
 * De dónde sale la posición desde la que se miden las distancias: el GPS del
 * móvil o, si no hay permiso, la capital de la provincia de la vivienda del
 * usuario (aproximada, pero real).
 */
enum class OrigenUbicacion { GPS, VIVIENDA }

data class TecnicosEstado(
    val tecnicos: List<Tecnico> = emptyList(),
    val busqueda: String = "",
    val filtro: String? = null,
    // null = todos; Tecnico.TIPO_TECNICO / Tecnico.TIPO_EMPRESA
    val filtroTipo: String? = null,
    val orden: OrdenTecnicos = OrdenTecnicos.VALORACION,
    // Ubicación del usuario; 0.0 = desconocida (sin GPS ni vivienda con
    // provincia). Mientras sea desconocida no se calculan ni se muestran distancias.
    val latUsuario: Double = 0.0,
    val lngUsuario: Double = 0.0,
    val origenUbicacion: OrigenUbicacion? = null,
    // Provincia usada como referencia cuando el origen es VIVIENDA
    val provinciaReferencia: String = "",
    val cargando: Boolean = false,
    val mensajeExito: String? = null,
    val error: String? = null
)

/**
 * Directorio de profesionales (compartido por la lista y el mapa):
 * escucha en tiempo real, filtros, orden y distancias con la ubicación real.
 */
class TecnicosViewModel : ViewModel() {
    var estado by mutableStateOf(TecnicosEstado())
        private set
    private val repo = RepositorioTecnicos()
    private val repoViviendas = RepositorioViviendas()
    private val auth = FirebaseAuth.getInstance()

    private var listenerTec: ListenerRegistration? = null
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
        uidEscuchado = auth.currentUser?.uid
        // La ubicación sacada de la vivienda es de la cuenta anterior
        if (estado.origenUbicacion == OrigenUbicacion.VIVIENDA) {
            estado = estado.copy(latUsuario = 0.0, lngUsuario = 0.0, origenUbicacion = null, provinciaReferencia = "")
        }
        if (uidEscuchado == null) {
            // Sin sesión no podemos leer (reglas exigen auth). Esperamos al
            // siguiente disparo del AuthStateListener.
            estado = estado.copy(tecnicos = emptyList(), cargando = false)
            return
        }
        if (estado.origenUbicacion == null) usarProvinciaDeLaVivienda()
        estado = estado.copy(cargando = estado.tecnicos.isEmpty())
        listenerTec = repo.escucharTecnicos { lista ->
            val procesados = lista.map { t ->
                if (estado.latUsuario != 0.0) {
                    val (tLat, tLng) = coordsEfectivasTecnico(t)
                    if (tLat != 0.0) t.copy(distanciaKm = calcularDistanciaKm(estado.latUsuario, estado.lngUsuario, tLat, tLng))
                    else t
                } else t
            }
            estado = estado.copy(tecnicos = procesados, cargando = false)
        }
    }

    /** Posición del GPS del móvil; tiene prioridad sobre la de la vivienda. */
    fun actualizarUbicacion(lat: Double, lng: Double) {
        // La app solo opera en España: una posición fuera (p. ej. la de
        // fábrica de un emulador) no sirve para calcular distancias. En ese
        // caso no se usa ninguna, en vez de fingir que el usuario está en Madrid.
        val enEspana = lat in 27.0..44.0 && lng in -19.0..5.0
        if (!enEspana) return
        aplicarUbicacion(lat, lng, OrigenUbicacion.GPS, provincia = "")
    }

    /**
     * Sin GPS, las distancias se miden desde la capital de la provincia de la
     * vivienda más reciente del usuario. Si no tiene viviendas (o es un
     * profesional), no hay ubicación y el directorio se ordena por valoración.
     */
    private fun usarProvinciaDeLaVivienda() {
        val uid = uidEscuchado ?: return
        repoViviendas.obtenerViviendas { viviendas ->
            // El GPS o un cambio de cuenta pueden haber llegado mientras tanto
            if (estado.origenUbicacion != null || uid != uidEscuchado) return@obtenerViviendas
            val provincia = viviendas
                .filter { it.provincia.isNotBlank() }
                .maxByOrNull { it.fechaCreacion }?.provincia ?: return@obtenerViviendas
            val (lat, lng) = RepositorioTecnicos.coordenadasDeProvincia(provincia) ?: return@obtenerViviendas
            aplicarUbicacion(lat, lng, OrigenUbicacion.VIVIENDA, provincia)
        }
    }

    private fun aplicarUbicacion(lat: Double, lng: Double, origen: OrigenUbicacion, provincia: String) {
        val tecnicosActualizados = estado.tecnicos.map { t ->
            val (tLat, tLng) = coordsEfectivasTecnico(t)
            if (tLat != 0.0) t.copy(distanciaKm = calcularDistanciaKm(lat, lng, tLat, tLng))
            else t
        }
        estado = estado.copy(
            latUsuario = lat, lngUsuario = lng,
            origenUbicacion = origen, provinciaReferencia = provincia,
            tecnicos = tecnicosActualizados
        )
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
    fun cambiarFiltroTipo(t: String?) { estado = estado.copy(filtroTipo = t) }
    fun cambiarOrden(o: OrdenTecnicos) { estado = estado.copy(orden = o) }

    fun tecnicosFiltrados(): List<Tecnico> {
        val q = estado.busqueda.trim().lowercase()
        val c = getCadenas(AppEstado.idioma)
        val filtrados = estado.tecnicos
            .filter {
                // La especialidad se busca por su texto guardado y por su nombre
                // en el idioma de la app ("insulation" encuentra "Aislamiento")
                q.isBlank() || it.nombre.lowercase().contains(q)
                    || it.ciudad.lowercase().contains(q)
                    || it.especialidades.any { e ->
                        e.lowercase().contains(q) || TextosEnergia.especialidad(e, c).lowercase().contains(q)
                    }
            }
            // Especialidades canónicas: también reconoce las escritas a mano en
            // perfiles antiguos ("placas solares" cuenta como Fotovoltaica)
            .filter { t -> estado.filtro == null || estado.filtro in Especialidades.canonicas(t.especialidades) }
            .filter { t -> estado.filtroTipo == null || t.tipoProfesional == estado.filtroTipo }
        // Verificados/destacados siempre primero, luego por criterio seleccionado.
        // Sin ubicación no hay distancias: "proximidad" ordena por valoración.
        return if (estado.orden == OrdenTecnicos.PROXIMIDAD && estado.latUsuario != 0.0) {
            filtrados.sortedWith(
                compareByDescending<Tecnico> { it.nivelPlan }.thenBy { t ->
                    val (tLat, _) = coordsEfectivasTecnico(t)
                    if (tLat == 0.0) Double.MAX_VALUE else t.distanciaKm
                }.thenByDescending { it.rating }
            )
        } else {
            filtrados.sortedWith(compareByDescending<Tecnico> { it.nivelPlan }.thenByDescending { it.rating })
        }
    }

    fun limpiarMensaje() { estado = estado.copy(mensajeExito = null, error = null) }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
        listenerTec?.remove()
        listenerTec = null
    }
}
