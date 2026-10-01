package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.InformeEnergetico
import com.example.zerohaus.Modelos.Vivienda
import com.example.zerohaus.Repositorios.AlgoritmoEnergetico
import com.example.zerohaus.Repositorios.ErrorIA
import com.example.zerohaus.Repositorios.ErrorIAException
import com.example.zerohaus.Repositorios.RepositorioIA
import com.example.zerohaus.Repositorios.SugerenciasIA
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Repositorios.RepositorioInformes
import com.example.zerohaus.Repositorios.RepositorioViviendas

/**
 * Informe mostrado, vivienda de la que sale (para el simulador) y consejos de IA.
 */
class InformeViewModel : ViewModel() {

    var informe by mutableStateOf<InformeEnergetico?>(null)
        private set
    var cargando by mutableStateOf(false)
        private set

    // Simulador "¿qué pasa si…?": vivienda del informe y mejoras marcadas
    var vivienda by mutableStateOf<Vivienda?>(null)
        private set
    var mejoras by mutableStateOf<List<AlgoritmoEnergetico.Mejora>>(emptyList())
        private set
    var seleccionadas by mutableStateOf<Set<String>>(emptySet())
        private set

    // Consejos personalizados con IA
    var sugerencias by mutableStateOf<SugerenciasIA?>(null)
        private set
    var generandoIA by mutableStateOf(false)
        private set
    var errorIA by mutableStateOf<ErrorIA?>(null)
        private set
    private var cacheIAConsultada: String? = null

    private val repo = RepositorioInformes()
    private val repoViviendas = RepositorioViviendas()
    private val repoIA = RepositorioIA()

    /** Muestra los consejos ya generados para este informe e idioma (sin gastar consulta). */
    fun cargarSugerenciasGuardadas() {
        val id = informe?.id.orEmpty()
        val clave = "$id|${AppEstado.idioma}"
        if (id.isEmpty() || cacheIAConsultada == clave) return
        cacheIAConsultada = clave
        sugerencias = null
        errorIA = null
        repo.obtenerSugerenciasGuardadas(id, AppEstado.idioma) { s -> if (s != null) sugerencias = s }
    }

    fun generarSugerencias(regenerar: Boolean = false) {
        val id = informe?.id.orEmpty()
        if (id.isEmpty() || generandoIA) return
        generandoIA = true
        errorIA = null
        repoIA.generarSugerencias(id, AppEstado.idioma, regenerar) { r ->
            generandoIA = false
            r.onSuccess { sugerencias = it }
                .onFailure { errorIA = (it as? ErrorIAException)?.tipo ?: ErrorIA.NO_DISPONIBLE }
        }
    }

    fun cargarUltimoInforme() {
        cargando = true
        repo.obtenerUltimoInforme { result ->
            informe = result
            cargando = false
        }
    }

    fun cargarInforme(inf: InformeEnergetico) {
        informe = inf
        cargando = false
    }

    /** Carga la vivienda del informe actual (si aún existe) para el simulador. */
    fun cargarViviendaDelInforme() {
        val id = informe?.viviendaId.orEmpty()
        if (id.isEmpty() || vivienda?.id == id) return
        repoViviendas.obtenerViviendas { lista ->
            val v = lista.firstOrNull { it.id == id }
            vivienda = v
            mejoras = v?.let { AlgoritmoEnergetico.mejorasAplicables(it) } ?: emptyList()
            seleccionadas = emptySet()
        }
    }

    var recalculando by mutableStateOf(false)
        private set

    /**
     * Genera un informe nuevo de la misma vivienda con el método de cálculo
     * actual (para informes hechos con el anterior). El antiguo se queda en
     * el historial.
     */
    fun recalcular() {
        val v = vivienda ?: return
        if (recalculando) return
        recalculando = true
        repo.generarInforme(v) { r ->
            recalculando = false
            r.onSuccess { nuevo ->
                informe = nuevo
                seleccionadas = emptySet()
                // Los consejos de IA eran del informe anterior
                sugerencias = null
                errorIA = null
                cacheIAConsultada = null
            }
        }
    }

    fun alternarMejora(titulo: String) {
        seleccionadas = if (titulo in seleccionadas) seleccionadas - titulo else seleccionadas + titulo
    }

    fun simulacion(): AlgoritmoEnergetico.Simulacion? {
        val v = vivienda ?: return null
        return AlgoritmoEnergetico.simular(v, mejoras.filter { it.titulo in seleccionadas })
    }
}
