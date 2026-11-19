package com.example.zerohaus

import com.example.zerohaus.Util.BorradoCuenta
import com.example.zerohaus.Util.BorradoCuenta.TipoError
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FirebaseFunctionsException no se puede construir en un test JVM (su enum Code
 * usa android.util.SparseArray), así que se prueba la parte pura con los mismos
 * datos que extrae el cableado: código UNAVAILABLE, details y ausencia de causa.
 */
class ClasificarErrorBorradoTest {
    private val play = mapOf("motivo" to "play")

    @Test fun unavailableConMotivoPlayDaMensajeDePlayYNoDeRed() {
        assertEquals(TipoError.PLAY, BorradoCuenta.clasificar(false, "UNAVAILABLE", play))
    }

    @Test fun unavailableSinMotivoNoEsDePlay() {
        assertEquals(TipoError.OTRO, BorradoCuenta.clasificar(false, "UNAVAILABLE", null))
    }

    @Test fun laRedTienePrioridad() {
        assertEquals(TipoError.RED, BorradoCuenta.clasificar(true, "UNAVAILABLE", play))
    }

    @Test fun otroErrorDelServidor() {
        assertEquals(TipoError.OTRO, BorradoCuenta.clasificar(false, "INTERNAL", play))
    }

    @Test fun pagosEnCursoBloqueanElBorrado() {
        val pagos = mapOf("motivo" to "pagos")
        assertEquals(TipoError.PAGOS, BorradoCuenta.clasificar(false, "FAILED_PRECONDITION", pagos))
        // Otro motivo o código: no es el bloqueo por pagos
        assertEquals(TipoError.OTRO, BorradoCuenta.clasificar(false, "FAILED_PRECONDITION", null))
        assertEquals(TipoError.OTRO, BorradoCuenta.clasificar(false, "INTERNAL", pagos))
        assertEquals(TipoError.RED, BorradoCuenta.clasificar(true, "FAILED_PRECONDITION", pagos))
    }
}
