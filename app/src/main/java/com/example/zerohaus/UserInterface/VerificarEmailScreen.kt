package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Repositorios.ErrorVerificacion
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.ViewModel.VerificarEmailViewModel

/**
 * Paso obligatorio tras registrarse o iniciar sesión con un email sin
 * verificar: hasta introducir el código de 6 dígitos no se entra en la app.
 */
@Composable
fun VerificarEmailScreen(
    viewModel: VerificarEmailViewModel,
    onVerificado: () -> Unit,
    onUsarOtraCuenta: () -> Unit
) {
    val c = LocalCadenas.current
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val estado = viewModel.estado
    val foco = remember { FocusRequester() }

    LaunchedEffect(Unit) { viewModel.iniciar() }
    LaunchedEffect(estado.verificado) { if (estado.verificado) onVerificado() }
    LaunchedEffect(estado.comprobandoSesion) {
        if (!estado.comprobandoSesion) runCatching { foco.requestFocus() }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(84.dp).background(verde.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MarkEmailRead, null, tint = verde, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(c.verTitulo, fontWeight = FontWeight.Bold, fontSize = 24.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(c.verSub, color = gris, fontSize = 15.sp, textAlign = TextAlign.Center)
            Text(estado.email, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))

            if (estado.comprobandoSesion) {
                CircularProgressIndicator(color = verde)
                return@Column
            }

            CampoCodigo(
                codigo = estado.codigo,
                onCambio = viewModel::cambiarCodigo,
                habilitado = !estado.verificando,
                hayError = estado.error != null,
                foco = foco,
                descripcion = c.verTitulo
            )
            Spacer(Modifier.height(14.dp))

            val mensajeError = when (val e = estado.error) {
                is ErrorVerificacion.Incorrecto -> "${c.verErrIncorrecto} ${e.intentosRestantes}"
                ErrorVerificacion.Caducado -> c.verErrCaducado
                ErrorVerificacion.DemasiadosIntentos -> c.verErrIntentos
                ErrorVerificacion.LimiteEnvios -> c.verErrLimite
                ErrorVerificacion.NoEnviado -> c.verErrEnvio
                ErrorVerificacion.SinConexion -> c.errorRed
                ErrorVerificacion.Servicio -> c.verErrServicio
                null -> null
            }
            when {
                mensajeError != null ->
                    Text(mensajeError, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, textAlign = TextAlign.Center)
                estado.codigoEnviado ->
                    Text("${c.verCodigoEnviado} ${c.verRevisaSpam}", color = gris, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = viewModel::verificar,
                enabled = estado.codigo.length == 6 && !estado.verificando,
                colors = ButtonDefaults.buttonColors(containerColor = verde),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (estado.verificando) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(c.verBoton, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = viewModel::enviarCodigo,
                enabled = estado.esperaSeg == 0 && !estado.enviando,
                border = BorderStroke(1.dp, if (estado.esperaSeg == 0) verde else gris.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                when {
                    estado.enviando -> CircularProgressIndicator(Modifier.size(18.dp), color = verde, strokeWidth = 2.dp)
                    estado.esperaSeg > 0 -> Text("${c.verReenviarEn} ${estado.esperaSeg} s", color = gris)
                    else -> Text(c.verReenviar, color = verde)
                }
            }
            Spacer(Modifier.height(20.dp))

            TextButton(onClick = {
                viewModel.reiniciar()
                onUsarOtraCuenta()
            }) { Text(c.verOtraCuenta, color = gris) }
        }
    }
}

/** 6 casillas sobre un único campo de texto numérico (pegar el código funciona). */
@Composable
private fun CampoCodigo(
    codigo: String,
    onCambio: (String) -> Unit,
    habilitado: Boolean,
    hayError: Boolean,
    foco: FocusRequester,
    descripcion: String
) {
    val verde = MaterialTheme.colorScheme.primary
    val colorError = MaterialTheme.colorScheme.error
    val borde = MaterialTheme.colorScheme.outline
    BasicTextField(
        value = codigo,
        onValueChange = onCambio,
        enabled = habilitado,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.focusRequester(foco).semantics { contentDescription = descripcion },
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { i ->
                    val digito = codigo.getOrNull(i)?.toString() ?: ""
                    val activa = i == codigo.length
                    val color = when {
                        hayError -> colorError
                        activa || digito.isNotEmpty() -> verde
                        else -> borde
                    }
                    Box(
                        Modifier
                            .size(width = 46.dp, height = 56.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .border(if (activa) 2.dp else 1.dp, color, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(digito, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )
}
