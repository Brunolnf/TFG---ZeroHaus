package com.example.zerohaus

import com.example.zerohaus.Modelos.ErrorPago
import com.example.zerohaus.Modelos.ErroresPago
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FirebaseFunctionsException no se puede construir en un test JVM, así que se
 * prueba la parte pura con lo que le pasa el repositorio: red, `code.name` y `details`.
 */
class ErroresPagoTest {

    private fun motivo(m: String) = mapOf("motivo" to m)

    @Test fun motivosDelContrato() {
        assertEquals(ErrorPago.PAGOS_NO_DISPONIBLES, ErroresPago.clasificar(false, "FAILED_PRECONDITION", motivo("pagos_deshabilitados")))
        assertEquals(ErrorPago.IMPORTE_SOBRE_MAXIMO, ErroresPago.clasificar(false, "INVALID_ARGUMENT", motivo("importe_maximo")))
        assertEquals(ErrorPago.TRANSICION_NO_PERMITIDA, ErroresPago.clasificar(false, "FAILED_PRECONDITION", motivo("transicion_no_permitida")))
    }

    @Test fun codigosDeErrorPagoDelServidor() {
        val esperado = mapOf(
            "pagos_deshabilitados" to ErrorPago.PAGOS_NO_DISPONIBLES,
            "stripe_no_configurado" to ErrorPago.PAGOS_NO_DISPONIBLES,
            "importe_bajo_minimo" to ErrorPago.IMPORTE_BAJO_MINIMO,
            "importe_minimo" to ErrorPago.IMPORTE_BAJO_MINIMO,
            "importe_sobre_maximo" to ErrorPago.IMPORTE_SOBRE_MAXIMO,
            "importe_maximo" to ErrorPago.IMPORTE_SOBRE_MAXIMO,
            "importe_no_entero" to ErrorPago.IMPORTE_NO_VALIDO,
            "descripcion_no_valida" to ErrorPago.DESCRIPCION_NO_VALIDA,
            "motivo_no_valido" to ErrorPago.MOTIVO_NO_VALIDO,
            "transicion_no_permitida" to ErrorPago.TRANSICION_NO_PERMITIDA,
            "actor_no_permitido" to ErrorPago.NO_AUTORIZADO,
            "uid_no_autorizado" to ErrorPago.NO_AUTORIZADO,
            "partes_no_validas" to ErrorPago.NO_AUTORIZADO,
            "no_es_profesional" to ErrorPago.NO_AUTORIZADO,
            "bloqueo" to ErrorPago.BLOQUEO,
            "chat_no_valido" to ErrorPago.CHAT_NO_VALIDO,
            "cobro_no_valido" to ErrorPago.COBRO_NO_VALIDO,
            "plazo_vencido" to ErrorPago.PLAZO_VENCIDO,
            "cuenta_en_borrado" to ErrorPago.CUENTA_EN_BORRADO,
            "reembolso_fallido" to ErrorPago.REEMBOLSO_FALLIDO,
            "transferencia_revertida" to ErrorPago.TRANSFERENCIA_REVERTIDA,
            "admin_es_parte" to ErrorPago.ADMIN_ES_PARTE,
            "cuenta_no_activa" to ErrorPago.CUENTA_STRIPE_NO_ACTIVA,
            "sin_cuenta" to ErrorPago.CUENTA_STRIPE_NO_ACTIVA,
            "pago_no_encontrado" to ErrorPago.PAGO_NO_ENCONTRADO,
            "operacion_en_curso" to ErrorPago.OPERACION_EN_CURSO,
            "cobro_en_curso" to ErrorPago.OPERACION_EN_CURSO,
            "limite_solicitudes" to ErrorPago.LIMITE,
            "stripe" to ErrorPago.STRIPE_NO_RESPONDE,
            "email_sin_verificar" to ErrorPago.EMAIL_SIN_VERIFICAR,
        )
        assertEquals(esperado, ErroresPago.MOTIVOS)
        esperado.forEach { (m, e) -> assertEquals(m, e, ErroresPago.clasificar(false, "INTERNAL", motivo(m))) }
    }

    @Test fun fallosQueLaAppNoDeberiaProvocarSonDesconocidos() {
        for (m in listOf("version_no_valida", "id_no_valido", "decision_no_valida")) {
            assertEquals(m, ErrorPago.DESCONOCIDO, ErroresPago.clasificar(false, "INVALID_ARGUMENT", motivo(m)))
        }
    }

    /** Cada código que lanza functions/pagos.py con ErrorPago("…") o es conocido o es un fallo a registrar. */
    @Test fun losCodigosDelServidorEstanClasificados() {
        val py = listOf(java.io.File("../functions/pagos.py"), java.io.File("functions/pagos.py")).firstOrNull { it.isFile }?.readText()
        org.junit.Assume.assumeTrue("No se encuentra functions/pagos.py", py != null)
        val delServidor = Regex("""ErrorPago\("([a-z_]+)"""").findAll(py!!).map { it.groupValues[1] }.toSet()
        val aRegistrar = setOf("version_no_valida", "id_no_valido", "decision_no_valida",
            "pago_no_valido", "fecha_no_valida", "plazo_no_vencido")
        val sinClasificar = delServidor - ErroresPago.MOTIVOS.keys - aRegistrar
        assertEquals("Códigos del servidor sin clasificar en la app", emptySet<String>(), sinClasificar)
    }

    @Test fun laRedTienePrioridadSobreElMotivo() {
        assertEquals(ErrorPago.RED, ErroresPago.clasificar(true, "INTERNAL", motivo("importe_maximo")))
        assertEquals(ErrorPago.RED, ErroresPago.clasificar(true, null, null))
    }

    @Test fun sinMotivoConocidoSeMiraElCodigo() {
        assertEquals(ErrorPago.SIN_SESION, ErroresPago.clasificar(false, "UNAUTHENTICATED", null))
        assertEquals(ErrorPago.NO_AUTORIZADO, ErroresPago.clasificar(false, "PERMISSION_DENIED", null))
        assertEquals(ErrorPago.LIMITE, ErroresPago.clasificar(false, "RESOURCE_EXHAUSTED", motivo("otro_cupo")))
        assertEquals(ErrorPago.RED, ErroresPago.clasificar(false, "UNAVAILABLE", null))
        // NOT_FOUND sin motivo puede ser una función sin desplegar: no es «pago no encontrado»
        assertEquals(ErrorPago.DESCONOCIDO, ErroresPago.clasificar(false, "NOT_FOUND", null))
        assertEquals(ErrorPago.DESCONOCIDO, ErroresPago.clasificar(false, "INTERNAL", null))
        assertEquals(ErrorPago.DESCONOCIDO, ErroresPago.clasificar(false, null, null))
    }

    @Test fun elMotivoMandaSobreElCodigo() {
        assertEquals(ErrorPago.PAGO_NO_ENCONTRADO, ErroresPago.clasificar(false, "NOT_FOUND", motivo("pago_no_encontrado")))
        assertEquals(ErrorPago.IMPORTE_BAJO_MINIMO, ErroresPago.clasificar(false, "PERMISSION_DENIED", motivo("importe_bajo_minimo")))
    }

    @Test fun detallesRaros() {
        assertEquals(ErrorPago.DESCONOCIDO, ErroresPago.clasificar(false, "INTERNAL", "importe_maximo"))
        assertEquals(ErrorPago.DESCONOCIDO, ErroresPago.clasificar(false, "INTERNAL", mapOf("motivo" to 3)))
        assertEquals(ErrorPago.DESCONOCIDO, ErroresPago.clasificar(false, "INTERNAL", motivo("IMPORTE_MAXIMO")))
        assertNull(ErroresPago.motivo(listOf("motivo")))
        assertEquals("x", ErroresPago.motivo(motivo("x")))
    }
}
