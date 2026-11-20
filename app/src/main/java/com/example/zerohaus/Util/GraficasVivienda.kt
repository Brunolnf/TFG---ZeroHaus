package com.example.zerohaus.Util

import com.example.zerohaus.Modelos.InformeEnergetico

/**
 * Informes de una misma vivienda, del más antiguo al más reciente. Los informes
 * antiguos sin `viviendaId` forman un grupo propio ([viviendaId] vacío).
 */
data class GrupoVivienda(
    val viviendaId: String,
    /** Nombre de la vivienda en su informe más reciente; vacío en el grupo «Sin vivienda». */
    val nombre: String,
    val informes: List<InformeEnergetico>
) {
    val sinVivienda: Boolean get() = viviendaId.isEmpty()
}

/** Lo que dibujan las gráficas de una vivienda. */
data class DatosGrafica(
    /** Últimos [MAX_PUNTOS] informes comparables, del más antiguo al más reciente. */
    val datos: List<InformeEnergetico>,
    /** Informes del método de cálculo anterior que no se mezclan con los actuales. */
    val excluidos: Int
) {
    /** Con un solo informe se muestra su consumo, pero no hay evolución que comparar. */
    val hayEvolucion: Boolean get() = datos.size >= 2
}

const val MAX_PUNTOS = 5

/**
 * Agrupa por vivienda. Restar el último informe al primero solo tiene sentido
 * dentro de la misma vivienda: entre un piso y un chalet la «mejora» sería falsa.
 * El primer grupo es el del informe más reciente.
 */
fun agruparPorVivienda(informes: List<InformeEnergetico>): List<GrupoVivienda> =
    informes.groupBy { it.viviendaId }
        .map { (id, lista) ->
            val ordenados = lista.sortedBy { it.fechaGeneracion }
            GrupoVivienda(id, ordenados.last().nombreVivienda, ordenados)
        }
        .sortedByDescending { it.informes.last().fechaGeneracion }

/**
 * Dentro de la vivienda no se mezclan informes del método de cálculo anterior
 * con los del actual: la diferencia parecería una mejora que no es real.
 */
fun datosGrafica(informesOrdenados: List<InformeEnergetico>): DatosGrafica {
    if (informesOrdenados.isEmpty()) return DatosGrafica(emptyList(), 0)
    val metodoActual = informesOrdenados.last().energiaPrimariaM2 > 0
    val comparables = if (metodoActual) informesOrdenados.filter { it.energiaPrimariaM2 > 0 } else informesOrdenados
    return DatosGrafica(comparables.takeLast(MAX_PUNTOS), informesOrdenados.size - comparables.size)
}

/** Vivienda a mostrar: la elegida si sigue existiendo y, si no, la del informe más reciente. */
fun grupoElegido(grupos: List<GrupoVivienda>, elegida: String?): GrupoVivienda? =
    grupos.firstOrNull { it.viviendaId == elegida } ?: grupos.firstOrNull()
