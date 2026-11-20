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
import com.example.zerohaus.Util.esRespuestaVigente
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

    // Cada petición de IA lleva un número: solo la última puede apagar generandoIA,
    // y al cambiar de informe se invalidan las que estaban en vuelo.
    private var peticionIA = 0

    private fun invalidarPeticionesIA() {
        peticionIA++
        generandoIA = false
    }

    private fun vigente(informeId: String, idioma: String) =
        esRespuestaVigente(informeId, idioma, informe?.id.orEmpty(), AppEstado.idioma)

    /** Muestra los consejos ya generados para este informe e idioma (sin gastar consulta). */
    fun cargarSugerenciasGuardadas() {
        val id = informe?.id.orEmpty()
        val idioma = AppEstado.idioma
        val clave = "$id|$idioma"
        if (id.isEmpty() || cacheIAConsultada == clave) return
        // Otro informe u otro idioma: lo que estuviera generándose ya no vale
        invalidarPeticionesIA()
        cacheIAConsultada = clave
        sugerencias = null
        errorIA = null
        repo.obtenerSugerenciasGuardadas(id, idioma) { s ->
            if (s != null && vigente(id, idioma)) sugerencias = s
        }
    }

    fun generarSugerencias(regenerar: Boolean = false) {
        val id = informe?.id.orEmpty()
        if (id.isEmpty() || generandoIA) return
        val idioma = AppEstado.idioma
        val peticion = ++peticionIA
        generandoIA = true
        errorIA = null
        repoIA.generarSugerencias(id, idioma, regenerar) { r ->
            // Solo la petición más reciente apaga el indicador; una respuesta
            // ignorada no lo deja encendido porque al cambiar se invalidó
            if (peticion == peticionIA) generandoIA = false
            if (!vigente(id, idioma)) return@generarSugerencias
            r.onSuccess { sugerencias = it }
                .onFailure { errorIA = (it as? ErrorIAException)?.tipo ?: ErrorIA.NO_DISPONIBLE }
        }
    }

    fun cargarUltimoInforme() {
        cargando = true
        repo.obtenerUltimoInforme { result ->
            if (result?.id != informe?.id) invalidarPeticionesIA()
            informe = result
            cargando = false
        }
    }

    fun cargarInforme(inf: InformeEnergetico) {
        if (inf.id != informe?.id) invalidarPeticionesIA()
        informe = inf
        cargando = false
    }

    /**
     * Carga la vivienda del informe actual (si aún existe) para el simulador.
     * Se vuelve a leer con cada informe aunque sea la misma vivienda: puede
     * haberse editado en el preestudio desde la última vez.
     */
    fun cargarViviendaDelInforme() {
        val id = informe?.viviendaId.orEmpty()
        if (id.isEmpty()) return
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
                invalidarPeticionesIA()
                informe = nuevo
                seleccionadas = emptySet()
                // Los consejos de IA eran del informe anterior
                sugerencias = null
                errorIA = null
                cacheIAConsultada = null
            }
        }
    }

    /** Al cerrar sesión: nada del informe de esta cuenta debe verse con la siguiente. */
    fun limpiar() {
        informe = null
        cargando = false
        vivienda = null
        mejoras = emptyList()
        seleccionadas = emptySet()
        sugerencias = null
        invalidarPeticionesIA()
        errorIA = null
        cacheIAConsultada = null
        recalculando = false
    }

    fun alternarMejora(titulo: String) {
        seleccionadas = if (titulo in seleccionadas) seleccionadas - titulo else seleccionadas + titulo
    }

    fun simulacion(): AlgoritmoEnergetico.Simulacion? {
        val v = vivienda ?: return null
        return AlgoritmoEnergetico.simular(v, mejoras.filter { it.titulo in seleccionadas })
    }
}
