package com.example.zerohaus

import com.example.zerohaus.Util.ResultadoRestauracion
import com.example.zerohaus.Util.clasificarRestauracion
import com.example.zerohaus.Util.esDeOtraCuenta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestaurarComprasTest {

    @Test fun sinComprasNoHayNadaQueRestaurar() {
        assertEquals(ResultadoRestauracion.NadaQueRestaurar, clasificarRestauracion(0, 0))
    }

    @Test fun cuentaSoloLasEnviadas() {
        assertEquals(ResultadoRestauracion.Enviadas(1), clasificarRestauracion(1, 2))
    }

    @Test fun siTodasSonDeOtraCuentaLoDiceEnVezDeCargarParaSiempre() {
        assertEquals(ResultadoRestauracion.SoloDeOtraCuenta, clasificarRestauracion(0, 1))
    }

    @Test fun etiquetaDeOtraCuentaSeDescartaYSinEtiquetaSeAcepta() {
        assertTrue(esDeOtraCuenta("hash-otro", "hash-mio"))
        assertFalse(esDeOtraCuenta("hash-mio", "hash-mio"))
        assertFalse(esDeOtraCuenta(null, "hash-mio"))
    }
}
