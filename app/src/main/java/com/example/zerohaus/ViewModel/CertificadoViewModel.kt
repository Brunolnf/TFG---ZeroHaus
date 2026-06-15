package com.example.zerohaus.ViewModel

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Certificado
import com.example.zerohaus.Repositorios.RepositorioCertificados

data class ArchivoSeleccionado(val uri: Uri, val nombre: String)

data class CertificadoEstado(
    val nombre: String = "",
    val tipo: String = "",                              // vacío = sin seleccionar
    val archivos: List<ArchivoSeleccionado> = emptyList(),
    val cargando: Boolean = false,
    val exito: Boolean = false,
    val error: String? = null,
    val certificados: List<Certificado> = emptyList()
) {
    val tipoSeleccionado: Boolean get() = tipo.isNotBlank()
    val puedeAgregar: Boolean      get() = archivos.size < 6
    /** Cumple los 3 requisitos para poder subir. */
    val listo: Boolean             get() = nombre.isNotBlank() && tipoSeleccionado && archivos.isNotEmpty()
    // Helpers para PanelTecnicoScreen
    val tieneAlguno: Boolean       get() = certificados.isNotEmpty()
    val tieneVerificados: Boolean  get() = certificados.any { it.verificado }
    val tieneRechazados: Boolean   get() = certificados.any { it.rechazado }
    val tienePendientes: Boolean   get() = certificados.any { !it.verificado && !it.rechazado }
}

class CertificadoViewModel : ViewModel() {

    var estado by mutableStateOf(CertificadoEstado())
        private set

    private val repo = RepositorioCertificados()

    fun cambiarNombre(v: String) { estado = estado.copy(nombre = v) }
    fun cambiarTipo(v: String)   { estado = estado.copy(tipo = v, error = null) }

    fun agregarArchivo(uri: Uri, nombre: String) {
        if (!estado.puedeAgregar) return
        estado = estado.copy(
            archivos = estado.archivos + ArchivoSeleccionado(uri, nombre),
            error = null
        )
    }

    fun eliminarArchivo(index: Int) {
        estado = estado.copy(
            archivos = estado.archivos.toMutableList().also { it.removeAt(index) }
        )
    }

    fun subirCertificado() {
        when {
            estado.nombre.isBlank()    -> { estado = estado.copy(error = "Introduce un nombre"); return }
            !estado.tipoSeleccionado   -> { estado = estado.copy(error = "Selecciona un tipo"); return }
            estado.archivos.isEmpty()  -> { estado = estado.copy(error = "Añade al menos un archivo"); return }
        }
        estado = estado.copy(cargando = true, error = null)
        val uris = estado.archivos.map { it.uri }
        repo.subirCertificado(estado.nombre, estado.tipo, uris) { result ->
            result
                .onSuccess {
                    estado = estado.copy(
                        cargando = false, exito = true,
                        nombre = "", tipo = "", archivos = emptyList()
                    )
                    cargarCertificados()
                }
                .onFailure { estado = estado.copy(cargando = false, error = it.message) }
        }
    }

    fun cargarCertificados() {
        repo.obtenerCertificados { lista ->
            estado = estado.copy(certificados = lista)
        }
    }

    fun limpiar() { estado = CertificadoEstado() }
}
