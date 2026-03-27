package com.example.zerohaus.ViewModel

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.Certificado
import com.example.zerohaus.Repositorios.RepositorioCertificados

data class CertificadoEstado(
    val nombre: String = "",
    val tipo: String = "Selecciona un tipo",
    val archivoUri: Uri? = null,
    val archivoNombre: String = "",
    val cargando: Boolean = false,
    val exito: Boolean = false,
    val error: String? = null,
    val certificados: List<Certificado> = emptyList()
)

class CertificadoViewModel : ViewModel() {

    var estado by mutableStateOf(CertificadoEstado())
        private set

    private val repo = RepositorioCertificados()

    fun cambiarNombre(v: String) { estado = estado.copy(nombre = v) }
    fun cambiarTipo(v: String) { estado = estado.copy(tipo = v) }
    fun seleccionarArchivo(uri: Uri, nombre: String) {
        estado = estado.copy(archivoUri = uri, archivoNombre = nombre)
    }

    fun subirCertificado() {
        val uri = estado.archivoUri ?: run {
            estado = estado.copy(error = "Selecciona un archivo")
            return
        }
        if (estado.nombre.isBlank()) {
            estado = estado.copy(error = "Introduce un nombre")
            return
        }
        estado = estado.copy(cargando = true, error = null)
        repo.subirCertificado(estado.nombre, estado.tipo, uri) { result ->
            result
                .onSuccess {
                    estado = estado.copy(
                        cargando = false,
                        exito = true,
                        nombre = "",
                        tipo = "Selecciona un tipo",
                        archivoUri = null,
                        archivoNombre = ""
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

    fun limpiar() {
        estado = CertificadoEstado()
    }
}