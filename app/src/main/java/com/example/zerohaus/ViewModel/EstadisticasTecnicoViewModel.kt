package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Planes
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Modelos.esDestacado
import com.example.zerohaus.Modelos.nivelPlan
import com.example.zerohaus.Repositorios.EstadisticasProfesional
import com.example.zerohaus.Repositorios.RepositorioEstadisticas
import com.example.zerohaus.Repositorios.RepositorioMonetizacion
import com.example.zerohaus.Repositorios.RepositorioTecnicos
import com.google.firebase.firestore.ListenerRegistration
import java.text.Normalizer

/** Lo que falta para que el perfil convenza (se muestra como checklist). */
enum class MejoraPerfil { DESCRIPCION, ESPECIALIDADES, TELEFONO, CIUDAD, UBICACION }

/** Posición en el directorio de su ciudad, ordenado igual que ve el cliente. */
data class PosicionDirectorio(
    val ciudad: String,
    val puesto: Int,
    val total: Int,
    val puestoConDestacado: Int?   // null si ya es destacado o no mejoraría
)

data class EstadisticasTecnicoEstado(
    val tecnico: Tecnico? = null,
    val cargando: Boolean = false,
    val estadisticas: EstadisticasProfesional = EstadisticasProfesional(),
    val posicion: PosicionDirectorio? = null
) {
    /** Checklist del perfil: pendientes (vacío = perfil completo). */
    val pendientesPerfil: List<MejoraPerfil>
        get() {
            val t = tecnico ?: return emptyList()
            return buildList {
                if (t.descripcion.trim().length < 80) add(MejoraPerfil.DESCRIPCION)
                if (t.especialidades.isEmpty()) add(MejoraPerfil.ESPECIALIDADES)
                if (t.telefono.isBlank()) add(MejoraPerfil.TELEFONO)
                if (t.ciudad.isBlank()) add(MejoraPerfil.CIUDAD)
                // Las coordenadas salen de la ciudad al guardar el perfil: si hay
                // ciudad pero no coordenadas, no se reconoció y no sale en el mapa.
                if (t.ciudad.isNotBlank() && t.latitud == 0.0 && t.longitud == 0.0) add(MejoraPerfil.UBICACION)
            }
        }

    val completitudPerfil: Int
        get() = 100 - pendientesPerfil.size * 100 / MejoraPerfil.entries.size
}

class EstadisticasTecnicoViewModel : ViewModel() {

    var estado by mutableStateOf(EstadisticasTecnicoEstado())
        private set

    private val repoTecnicos = RepositorioTecnicos()
    private val repoMonetizacion = RepositorioMonetizacion()
    private val repoEstadisticas = RepositorioEstadisticas()
    private var listenerPerfil: ListenerRegistration? = null
    private var listenerEstadisticas: ListenerRegistration? = null

    init { cargar() }

    fun cargar(forzar: Boolean = false) {
        if (listenerPerfil != null && !forzar) return
        estado = estado.copy(cargando = true)
        repoTecnicos.obtenerMiPerfilTecnico { tec ->
            if (tec == null) {
                estado = EstadisticasTecnicoEstado(cargando = false)
                return@obtenerMiPerfilTecnico
            }
            listenerPerfil?.remove()
            listenerPerfil = repoMonetizacion.escucharMiPerfilProfesional { t ->
                estado = estado.copy(tecnico = t ?: tec, cargando = false)
            }
            listenerEstadisticas?.remove()
            listenerEstadisticas = repoEstadisticas.escucharEstadisticas(tec.id.ifBlank { tec.uid }) { e ->
                estado = estado.copy(estadisticas = e)
            }
            calcularPosicion(tec)
        }
    }

    private fun calcularPosicion(yo: Tecnico) {
        if (yo.ciudad.isBlank()) return
        val ciudad = normalizar(yo.ciudad)
        repoTecnicos.obtenerTecnicos { todos ->
            val deMiCiudad = todos.filter { normalizar(it.ciudad) == ciudad }
                .let { lista -> if (lista.none { it.id == yo.id }) lista + yo else lista }
            val puesto = puestoEn(deMiCiudad, yo.id)
            val conDestacado = if (yo.esDestacado) null else {
                val simulado = yo.copy(
                    planActivo = Planes.PLAN_DESTACADO,
                    suscripcionHasta = System.currentTimeMillis() + 86_400_000L
                )
                puestoEn(deMiCiudad.map { if (it.id == yo.id) simulado else it }, yo.id)
                    .takeIf { it < puesto }
            }
            estado = estado.copy(
                posicion = PosicionDirectorio(yo.ciudad, puesto, deMiCiudad.size, conDestacado)
            )
        }
    }

    /** Mismo orden que el directorio (TecnicosViewModel, orden por valoración). */
    private fun puestoEn(lista: List<Tecnico>, id: String): Int =
        lista.sortedWith(compareByDescending<Tecnico> { it.nivelPlan }.thenByDescending { it.rating })
            .indexOfFirst { it.id == id } + 1

    private fun normalizar(s: String) = Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

    override fun onCleared() {
        super.onCleared()
        listenerPerfil?.remove()
        listenerEstadisticas?.remove()
        listenerPerfil = null
        listenerEstadisticas = null
    }
}
