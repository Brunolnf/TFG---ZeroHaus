package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.zerohaus.Modelos.*
import com.example.zerohaus.Repositorios.*

data class PreestudioEstado(
    val nombreVivienda: String = "Mi vivienda principal",
    val superficie: String = "100",
    val anio: String = "2000",
    val ventanas: String = "Vidrio simple",
    val aislamiento: String = "Aislamiento parcial",
    val calefaccion: String = "Caldera de gas",
    val acs: String = "Gas",
    val direccion: String = "",
    val orientacion: String = "Sur",
    val cargando: Boolean = false,
    val error: String? = null,
    val informeGenerado: InformeEnergetico? = null
)

class PreestudioViewModel : ViewModel() {

    var estado by mutableStateOf(PreestudioEstado())
        private set

    private val repoViviendas = RepositorioViviendas()
    private val repoInformes = RepositorioInformes()

    fun cambiarNombre(v: String) { estado = estado.copy(nombreVivienda = v) }
    fun cambiarSuperficie(v: String) { estado = estado.copy(superficie = v) }
    fun cambiarAnio(v: String) { estado = estado.copy(anio = v) }
    fun cambiarVentanas(v: String) { estado = estado.copy(ventanas = v) }
    fun cambiarAislamiento(v: String) { estado = estado.copy(aislamiento = v) }
    fun cambiarCalefaccion(v: String) { estado = estado.copy(calefaccion = v) }
    fun cambiarAcs(v: String) { estado = estado.copy(acs = v) }
    fun cambiarDireccion(v: String) { estado = estado.copy(direccion = v) }
    fun cambiarOrientacion(v: String) { estado = estado.copy(orientacion = v) }

    fun generarInforme() {
        estado = estado.copy(cargando = true, error = null)

        val vivienda = Vivienda(
            nombre = estado.nombreVivienda,
            superficie = estado.superficie.toIntOrNull() ?: 100,
            anioConstruccion = estado.anio.toIntOrNull() ?: 2000,
            tipoVentanas = estado.ventanas,
            aislamiento = estado.aislamiento,
            calefaccion = estado.calefaccion,
            acs = estado.acs,
            direccion = estado.direccion,
            orientacion = estado.orientacion
        )

        // Primero guardamos la vivienda, luego generamos el informe
        repoViviendas.guardarVivienda(vivienda) { resultVivienda ->
            resultVivienda
                .onSuccess { viviendaId ->
                    val viviendaConId = vivienda.copy(id = viviendaId)
                    repoInformes.generarInforme(viviendaConId) { resultInforme ->
                        resultInforme
                            .onSuccess { informe ->
                                estado = estado.copy(
                                    informeGenerado = informe,
                                    cargando = false
                                )
                            }
                            .onFailure { e ->
                                estado = estado.copy(
                                    error = e.message,
                                    cargando = false
                                )
                            }
                    }
                }
                .onFailure { e ->
                    estado = estado.copy(error = e.message, cargando = false)
                }
        }
    }

    fun limpiarInforme() {
        estado = estado.copy(informeGenerado = null)
    }
}