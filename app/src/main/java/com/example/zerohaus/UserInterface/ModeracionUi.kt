package com.example.zerohaus.UserInterface

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Modelos.Moderacion
import com.example.zerohaus.Modelos.MotivoDenuncia
import com.example.zerohaus.Modelos.ResultadoDenuncia
import com.example.zerohaus.Util.AppCadenas
import com.example.zerohaus.Util.LocalCadenas

/** Mensaje de resultado de una acción de moderación (para el [ZeroToast] de cada pantalla). */
data class AvisoModeracion(val texto: String, val tipo: ToastTipo)

fun textoMotivo(m: MotivoDenuncia, c: AppCadenas): String = when (m) {
    MotivoDenuncia.SPAM -> c.modMotivoSpam
    MotivoDenuncia.ACOSO -> c.modMotivoAcoso
    MotivoDenuncia.FRAUDE -> c.modMotivoFraude
    MotivoDenuncia.INAPROPIADO -> c.modMotivoInapropiado
    MotivoDenuncia.SUPLANTACION -> c.modMotivoSuplantacion
    MotivoDenuncia.OTRO -> c.modMotivoOtro
}

fun avisoDenuncia(r: ResultadoDenuncia, c: AppCadenas) = when (r) {
    ResultadoDenuncia.ENVIADA -> AvisoModeracion(c.modDenunciaEnviada, ToastTipo.EXITO)
    ResultadoDenuncia.YA_DENUNCIADA -> AvisoModeracion(c.modYaDenunciado, ToastTipo.INFO)
    ResultadoDenuncia.ERROR -> AvisoModeracion(c.modDenunciaError, ToastTipo.ERROR)
}

/** Resultado de bloquear (bloqueado = true) o desbloquear. */
fun avisoBloqueo(bloqueado: Boolean, ok: Boolean, c: AppCadenas) = when {
    !ok -> AvisoModeracion(c.modErrorBloqueo, ToastTipo.ERROR)
    bloqueado -> AvisoModeracion(c.modBloqueado, ToastTipo.EXITO)
    else -> AvisoModeracion(c.modDesbloqueado, ToastTipo.EXITO)
}

/** Diálogo de denuncia: motivo (uno de los 6) y comentario opcional. */
@Composable
fun DialogoDenuncia(
    titulo: String,
    enviando: Boolean,
    onEnviar: (MotivoDenuncia, String) -> Unit,
    onCancelar: () -> Unit
) {
    val c = LocalCadenas.current
    val verde = MaterialTheme.colorScheme.primary
    var motivo by remember { mutableStateOf<MotivoDenuncia?>(null) }
    var comentario by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!enviando) onCancelar() },
        title = { Text(titulo, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(c.modDenunciaMotivo, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                MotivoDenuncia.values().forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !enviando) { motivo = m },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = motivo == m,
                            onClick = { motivo = m },
                            enabled = !enviando,
                            colors = RadioButtonDefaults.colors(selectedColor = verde)
                        )
                        Text(textoMotivo(m, c), fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = comentario,
                    onValueChange = { if (it.length <= Moderacion.MAX_COMENTARIO) comentario = it },
                    label = { Text(c.modComentarioLabel) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    enabled = !enviando,
                    supportingText = { Text("${comentario.length} / ${Moderacion.MAX_COMENTARIO}", fontSize = 11.sp) }
                )
                if (enviando) LinearProgressIndicator(Modifier.fillMaxWidth(), color = verde)
            }
        },
        confirmButton = {
            Button(
                onClick = { motivo?.let { onEnviar(it, comentario) } },
                enabled = motivo != null && !enviando,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(c.modEnviarDenuncia, color = Color.White) }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancelar, enabled = !enviando) { Text(c.cancelar) }
        }
    )
}

/** Confirmación de bloqueo: explica que no podréis escribiros. */
@Composable
fun DialogoBloquear(onConfirmar: () -> Unit, onCancelar: () -> Unit) {
    val c = LocalCadenas.current
    AlertDialog(
        onDismissRequest = onCancelar,
        icon = { Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(c.modBloquearTitulo, fontWeight = FontWeight.SemiBold) },
        text = { Text(c.modBloquearMsg) },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(c.modBloquear, color = Color.White) }
        },
        dismissButton = { OutlinedButton(onClick = onCancelar) { Text(c.cancelar) } }
    )
}
