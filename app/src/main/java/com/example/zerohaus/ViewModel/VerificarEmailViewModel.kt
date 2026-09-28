package com.example.zerohaus.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zerohaus.Repositorios.ErrorVerificacion
import com.example.zerohaus.Repositorios.RepositorioVerificacion
import com.example.zerohaus.Repositorios.VerificacionException
import com.example.zerohaus.Util.AppEstado
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Estado de la pantalla de verificación del email.
 */
data class VerificarEmailEstado(
    val email: String = "",
    val codigo: String = "",
    val comprobandoSesion: Boolean = true,
    val enviando: Boolean = false,
    val verificando: Boolean = false,
    val esperaSeg: Int = 0,            // cuenta atrás para poder reenviar
    val codigoEnviado: Boolean = false,
    val error: ErrorVerificacion? = null,
    val verificado: Boolean = false
)

class VerificarEmailViewModel : ViewModel() {

    var estado by mutableStateOf(VerificarEmailEstado())
        private set

    private val repo = RepositorioVerificacion()
    private var cuentaAtras: Job? = null
    private var iniciado = false

    /** Al abrir la pantalla: si ya está verificado (p. ej. en otro móvil) sigue;
     *  si no, envía el primer código automáticamente. */
    fun iniciar() {
        if (iniciado) return
        iniciado = true
        estado = estado.copy(email = repo.emailActual, comprobandoSesion = true)
        repo.refrescarSesion { verificado ->
            estado = estado.copy(comprobandoSesion = false, verificado = verificado)
            if (!verificado) enviarCodigo()
        }
    }

    fun enviarCodigo() {
        if (estado.enviando || estado.esperaSeg > 0) return
        estado = estado.copy(enviando = true, error = null)
        repo.enviarCodigo(AppEstado.idioma) { r ->
            r.onSuccess { envio ->
                if (envio.yaVerificado) {
                    repo.refrescarSesion { estado = estado.copy(enviando = false, verificado = true) }
                    return@onSuccess
                }
                estado = estado.copy(
                    enviando = false,
                    codigoEnviado = estado.codigoEnviado || envio.enviado,
                    codigo = if (envio.enviado) "" else estado.codigo
                )
                iniciarCuentaAtras(envio.esperaSeg)
            }.onFailure { e ->
                estado = estado.copy(enviando = false, error = (e as? VerificacionException)?.error ?: ErrorVerificacion.SinConexion)
            }
        }
    }

    fun cambiarCodigo(valor: String) {
        val limpio = valor.filter(Char::isDigit).take(6)
        estado = estado.copy(codigo = limpio, error = null)
        if (limpio.length == 6) verificar()
    }

    fun verificar() {
        val codigo = estado.codigo
        if (codigo.length != 6 || estado.verificando) return
        estado = estado.copy(verificando = true, error = null)
        repo.verificarCodigo(codigo) { r ->
            r.onSuccess {
                estado = estado.copy(verificando = false, verificado = true)
            }.onFailure { e ->
                val error = (e as? VerificacionException)?.error ?: ErrorVerificacion.SinConexion
                // Tras un código caducado o agotado, el siguiente paso es pedir otro
                estado = estado.copy(verificando = false, error = error, codigo = "")
            }
        }
    }

    private fun iniciarCuentaAtras(segundos: Int) {
        cuentaAtras?.cancel()
        if (segundos <= 0) return
        cuentaAtras = viewModelScope.launch {
            for (s in segundos downTo 1) {
                estado = estado.copy(esperaSeg = s)
                delay(1000)
            }
            estado = estado.copy(esperaSeg = 0)
        }
    }

    /** Al cerrar sesión desde esta pantalla, que la próxima cuenta empiece de cero. */
    fun reiniciar() {
        cuentaAtras?.cancel()
        iniciado = false
        estado = VerificarEmailEstado()
    }
}
