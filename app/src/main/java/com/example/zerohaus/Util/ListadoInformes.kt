package com.example.zerohaus.Util

import com.example.zerohaus.Modelos.InformeEnergetico

/**
 * Lo que muestran el historial y las gráficas. Tres casos distintos:
 *  - lista vacía sin error  → «todavía no tienes informes»
 *  - [datosGuardados]       → «mostrando datos guardados» (caché por el tope de espera)
 *  - [errorCarga]           → «no se ha podido cargar», con botón de reintentar
 */
data class ListadoInformes(
    val informes: List<InformeEnergetico> = emptyList(),
    val datosGuardados: Boolean = false,
    val errorCarga: Boolean = false
)

fun listadoDe(lectura: Lectura<List<InformeEnergetico>>): ListadoInformes = when (lectura) {
    is Lectura.Servidor -> ListadoInformes(lectura.datos)
    is Lectura.Guardada -> ListadoInformes(lectura.datos, datosGuardados = true)
    is Lectura.Fallo -> ListadoInformes(errorCarga = true)
}
