package com.example.zerohaus.Util

/**
 * Toda la app asume números españoles: se almacenan como 9 dígitos sin
 * prefijo y se muestran/marcan con "+34" añadido al vuelo.
 */
object Telefono {

    private const val PREFIJO = "+34"

    /** Quita prefijos (+34, 0034, 34) y cualquier no-dígito, y deja 9 cifras. */
    fun normalizar(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var digitos = raw.filter { it.isDigit() }
        if (digitos.startsWith("0034")) digitos = digitos.removePrefix("0034")
        else if (digitos.length > 9 && digitos.startsWith("34")) digitos = digitos.removePrefix("34")
        return digitos.take(9)
    }

    /** "+34 612 345 678" para mostrar. Si no hay 9 dígitos, devuelve lo que haya con el prefijo. */
    fun formatoVisible(raw: String?): String {
        val n = normalizar(raw)
        if (n.isEmpty()) return ""
        return if (n.length == 9) "$PREFIJO ${n.substring(0, 3)} ${n.substring(3, 6)} ${n.substring(6, 9)}"
        else "$PREFIJO $n"
    }

    /** "+34612345678" para Intent ACTION_DIAL. */
    fun formatoMarcado(raw: String?): String {
        val n = normalizar(raw)
        return if (n.isEmpty()) "" else "$PREFIJO$n"
    }
}
