
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
    private val repo = RepositorioTecnicos(); private val repoAuth = RepositorioAutenticacion()

    fun cargarTecnicos() { estado = estado.copy(cargando = true); repo.obtenerTecnicos { l -> estado = estado.copy(tecnicos = l, cargando = false) } }
    fun cambiarBusqueda(q: String) { estado = estado.copy(busqueda = q) }
    fun cambiarFiltro(f: String?) { estado = estado.copy(filtro = f) }
    fun tecnicosFiltrados(): List<Tecnico> { val q = estado.busqueda.trim().lowercase(); return estado.tecnicos.filter { q.isBlank() || it.nombre.lowercase().contains(q) || it.especialidades.any { e -> e.lowercase().contains(q) } }.filter { t -> estado.filtro == null || t.especialidades.contains(estado.filtro) } }
    fun solicitarPresupuesto(tecnico: Tecnico) { solicitarPresupuestoConDescripcion(tecnico, "Solicitud de presupuesto") }
    fun solicitarPresupuestoConDescripcion(tecnico: Tecnico, descripcion: String) {
        repoAuth.obtenerUsuario { u -> val s = SolicitudPresupuesto(uidCliente = repoAuth.getUid() ?: "", nombreCliente = u?.nombre ?: "Usuario", tecnicoId = tecnico.id, tecnicoNombre = tecnico.nombre, descripcion = descripcion)
            repo.solicitarPresupuesto(s) { r -> r.onSuccess { estado = estado.copy(mensajeExito = "Solicitud enviada a ${tecnico.nombre}") }.onFailure { estado = estado.copy(error = it.message) } }
        }
    }
    fun limpiarMensaje() { estado = estado.copy(mensajeExito = null, error = null) }
}
