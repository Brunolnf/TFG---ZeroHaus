package com.example.zerohaus.Modelos

/**
 * Modelo de negocio de ZeroHaus (suscripciones para profesionales):
 *
 *  - Los CLIENTES acceden a todo gratis: perfiles, teléfono, chat, valoraciones.
 *  - Los PROFESIONALES pagan una suscripción para aparecer como "Verificado" o
 *    "Destacado" en el directorio. Los verificados/destacados salen primero.
 *
 * Todos los documentos de suscripción los escribe EXCLUSIVAMENTE la Cloud Function
 * `activar_suscripcion` con Admin SDK: las reglas de Firestore prohíben cualquier
 * escritura directa desde el cliente.
 */

/** Catálogo de planes de suscripción (Google Play Billing, subscriptions). */
object Planes {
    const val VERIFICADO_TRIMESTRAL       = "verificado_trimestral"
    const val VERIFICADO_ANUAL            = "verificado_anual"
    const val DESTACADO_MENSUAL           = "destacado_mensual"
    const val DESTACADO_ANUNCIOS_MENSUAL  = "destacado_anuncios_mensual"

    const val PLAN_VERIFICADO          = "verificado"
    const val PLAN_DESTACADO           = "destacado"
    const val PLAN_DESTACADO_ANUNCIOS  = "destacado_anuncios"


    fun planDeProducto(productoId: String): String = when (productoId) {
        VERIFICADO_TRIMESTRAL, VERIFICADO_ANUAL -> PLAN_VERIFICADO
        DESTACADO_MENSUAL -> PLAN_DESTACADO
        DESTACADO_ANUNCIOS_MENSUAL -> PLAN_DESTACADO_ANUNCIOS
        else -> ""
    }

    fun nivelPlan(plan: String): Int = when (plan) {
        PLAN_DESTACADO_ANUNCIOS -> 3
        PLAN_DESTACADO -> 2
        PLAN_VERIFICADO -> 1
        else -> 0
    }
}

/**
 * Registro de una suscripción. docId = purchaseToken (idempotencia).
 * Solo lo escribe la Cloud Function.
 */
data class Suscripcion(
    val id: String = "",
    val uid: String = "",
    val planId: String = "",
    val plan: String = "",
    val origen: String = "",
    val fechaInicio: Long = 0,
    val fechaFin: Long = 0,
    val activa: Boolean = false
)
