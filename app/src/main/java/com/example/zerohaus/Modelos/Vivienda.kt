package com.example.zerohaus.Modelos

/**
 * Vivienda de un propietario (`/viviendas`). Sus campos son la entrada del
 * algoritmo energético; los valores de las opciones se guardan en español y
 * se traducen solo al mostrarlos ([com.example.zerohaus.Util.TextosEnergia]).
 */
data class Vivienda(
    val id: String = "",
    val uid: String = "",
    val nombre: String = "",
    val superficie: Int = 0,
    val anioConstruccion: Int = 2000,
    val tipoVentanas: String = "",
    val aislamiento: String = "",
    val calefaccion: String = "",
    val acs: String = "",
    val direccion: String = "",
    val provincia: String = "",
    val orientacion: String = "",
    val iluminacion: String = "",
    val tipoVivienda: String = "",
    val refrigeracion: String = "",
    val fotovoltaica: String = "",
    val ocupantes: Int = 0,
    val electrodomesticos: String = "",
    val fechaCreacion: Long = System.currentTimeMillis()
)