
package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.SolicitudPresupuesto
import com.example.zerohaus.Modelos.Tecnico
import com.example.zerohaus.Repositorios.*

data class TecnicosEstado(val tecnicos: List<Tecnico> = emptyList(), val busqueda: String = "", val filtro: String? = null, val cargando: Boolean = true, val mensajeExito: String? = null, val error: String? = null)

class TecnicosViewModel : ViewModel() {
    var estado by mutableStateOf(TecnicosEstado())
        private set
    private val repo = RepositorioTecnicos()
    private val repoResenas = RepositorioResenas()
    private val repoAuth = RepositorioAutenticacion()

    fun cargarTecnicos(forzar: Boolean = false) {
        if (!forzar && estado.tecnicos.isNotEmpty()) return
        // Si ya había técnicos, mantenemos la lista visible mientras refrescamos en segundo plano.
        val tieneDatos = estado.tecnicos.isNotEmpty()
        estado = estado.copy(cargando = !tieneDatos)
        repo.obtenerTecnicos { lista ->
            if (lista.isEmpty()) { estado = estado.copy(cargando = false); return@obtenerTecnicos }
            // Conserva ratings ya conocidos para evitar parpadeo a 0.0 al refrescar.
            val previos = estado.tecnicos.associateBy { it.id }
            estado = estado.copy(
                tecnicos = lista.map { t ->
                    val prev = previos[t.id]
                    if (prev != null) t.copy(rating = prev.rating, opiniones = prev.opiniones) else t.copy(rating = 0.0, opiniones = 0)
                },
                cargando = false
            )
            lista.forEach { t ->
                repoResenas.obtenerResenas(t.id) { resenas ->
                    val count = resenas.size
                    val avg = if (count == 0) 0.0
                              else Math.round(resenas.map { it.puntuacion }.average() * 10.0) / 10.0
                    estado = estado.copy(tecnicos = estado.tecnicos.map { if (it.id == t.id) it.copy(rating = avg, opiniones = count) else it })
                }
            }
        }
    }
    fun cambiarBusqueda(q: String) { estado = estado.copy(busqueda = q) }
    fun cambiarFiltro(f: String?) { estado = estado.copy(filtro = f) }
    fun tecnicosFiltrados(): List<Tecnico> { val q = estado.busqueda.trim().lowercase(); return estado.tecnicos.filter { q.isBlank() || it.nombre.lowercase().contains(q) || it.especialidades.any { e -> e.lowercase().contains(q) } }.filter { t -> estado.filtro == null || t.especialidades.contains(estado.filtro) } }
    fun solicitarPresupuesto(tecnico: Tecnico) { solicitarPresupuestoConDescripcion(tecnico, "Solicitud de presupuesto") }
    fun solicitarPresupuestoConDescripcion(tecnico: Tecnico, descripcion: String) {
        repoAuth.obtenerUsuario { u ->
            val s = SolicitudPresupuesto(
                uidCliente = repoAuth.getUid() ?: "",
                nombreCliente = u?.nombre ?: "Usuario",
                tecnicoId = tecnico.id,
                tecnicoUid = tecnico.uid,   // Auth UID real del técnico (para notificaciones)
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
