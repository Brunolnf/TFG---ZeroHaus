package com.example.zerohaus.Modelos

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Perfil público de un profesional (técnico certificador o empresa de
 * reformas) en `/tecnicos`. `rating` y `opiniones` los recalcula el servidor;
 * `planActivo` y `suscripcionHasta` solo los escribe la Cloud Function de
 * suscripciones.
 */
@IgnoreExtraProperties
data class Tecnico(
    val id: String = "",
    val uid: String = "",
    val nombre: String = "",
    val ciudad: String = "",
    val rating: Double = 0.0,
    val opiniones: Int = 0,
    // Legacy (modelo de presupuestos retirado): congelado a 0 por las rules
    // y no se muestra. Se conserva para no romper documentos existentes.
    val proyectosCompletados: Int = 0,
    val distanciaKm: Double = 0.0,
    val especialidades: List<String> = emptyList(),
    val descripcion: String = "",
    val telefono: String = "",
    val emailContacto: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val tipoProfesional: String = TIPO_TECNICO,
    // Suscripción: gestionada por la Cloud Function activar_suscripcion.
    // Las rules congelan ambos campos para el cliente.
    val planActivo: String = "",
    val suscripcionHasta: Long = 0,
    // false = cuenta aún sin el email verificado: no sale en el directorio.
    // Solo lo escribe el servidor (las reglas lo impiden al cliente), así que
    // nunca se serializa: lo rellena RepositorioTecnicos al leer el perfil.
    // null = perfil anterior a este campo (se muestra).
    @get:Exclude val emailVerificado: Boolean? = null
) {
    companion object {
        const val TIPO_TECNICO = "TECNICO"
        const val TIPO_EMPRESA = "EMPRESA"
    }
}

/**
 * Perfiles que muestran el directorio y el mapa: se ocultan los de cuentas
 * que aún no han verificado su email (`emailVerificado == false`), salvo el
 * propio, para que el profesional siga viendo el suyo.
 */
fun visiblesEnDirectorio(tecnicos: List<Tecnico>, miUid: String?): List<Tecnico> =
    tecnicos.filter { it.emailVerificado != false || (!miUid.isNullOrEmpty() && it.uid == miUid) }

val Tecnico.esEmpresa: Boolean get() = tipoProfesional == Tecnico.TIPO_EMPRESA

val Tecnico.esVerificado: Boolean get() = planActivo.isNotBlank() && suscripcionHasta > System.currentTimeMillis()

val Tecnico.esDestacado: Boolean get() = esVerificado && (planActivo == Planes.PLAN_DESTACADO || planActivo == Planes.PLAN_DESTACADO_ANUNCIOS)

val Tecnico.nivelPlan: Int get() = if (esVerificado) Planes.nivelPlan(planActivo) else 0
