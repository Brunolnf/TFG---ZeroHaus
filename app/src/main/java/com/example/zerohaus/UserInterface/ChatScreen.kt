package com.example.zerohaus.UserInterface

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.zerohaus.Modelos.MensajeChat
import com.example.zerohaus.ViewModel.ChatViewModel
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    chatId: String,
    onVolver: () -> Unit = {},
    onVerPerfil: (String) -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val verdeClaro = Color(0xFF22C55E)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)

    val estado = viewModel.chatEstado
    val miUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // ───────────── LAUNCHERS ─────────────

    val imagenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.seleccionarImagen(it) } }

    val archivoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            var nombre = "archivo"; var bytes = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) nombre = c.getString(ni) ?: "archivo"
                    if (si >= 0) bytes = c.getLong(si)
                }
            }
            viewModel.enviarArchivo(chatId, uri, nombre, bytes)
        }
    }

    // ───────────── EFECTOS ─────────────

    LaunchedEffect(chatId) { viewModel.abrirChat(chatId) }
    DisposableEffect(Unit) { onDispose { viewModel.cerrarChat() } }
    LaunchedEffect(estado.mensajes.size) {
        if (estado.mensajes.isNotEmpty()) listState.animateScrollToItem(estado.mensajes.lastIndex)
    }

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = {
                    val clickMod = if (estado.otroTecnicoDocId.isNotEmpty())
                        Modifier.clickable { onVerPerfil(estado.otroTecnicoDocId) }
                    else Modifier
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = clickMod) {
                        val inicial = estado.nombreOtroUsuario.firstOrNull()?.uppercase() ?: "?"
                        Box(
                            Modifier.size(36.dp).background(verde, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(inicial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                estado.nombreOtroUsuario.ifEmpty { "Chat" },
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            when {
                                estado.otroTecnicoDocId.isNotEmpty() ->
                                    Text("Ver perfil", color = verde, fontSize = 11.sp)
                                estado.nombreOtroUsuario.isNotEmpty() ->
                                    Text("Conversación", color = gris, fontSize = 11.sp)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {

            // ── Mensajes ──
            if (estado.mensajes.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send, null,
                            tint = gris.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No hay mensajes aún", color = gris)
                        Text("Escribe el primer mensaje", color = gris.copy(0.7f), fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(estado.mensajes, key = { it.id }) { msg ->
                        val esMio = msg.emisorUid == miUid
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = if (esMio) Alignment.End else Alignment.Start
                        ) {
                            if (!esMio && estado.nombreOtroUsuario.isNotEmpty()) {
                                Text(
                                    msg.emisorNombre, fontSize = 11.sp, color = gris,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                )
                            }
                            BurbujaMensaje(msg, esMio, gris, verde, sdf, context)
                        }
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
            }

            // ── Barra inferior ──
            Surface(
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    if (estado.subiendoMedia) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = verde)
                    }

                    if (estado.imagenPendiente != null) {
                        BarraPreviewImagen(
                            uri = estado.imagenPendiente!!,
                            caption = estado.captionImagen,
                            onCaptionChange = { viewModel.cambiarCaptionImagen(it) },
                            onEnviar = { viewModel.enviarImagenPendiente(chatId) },
                            onCancelar = { viewModel.cancelarImagenPendiente() },
                            verde = verde, gris = gris,
                            enviando = estado.subiendoMedia
                        )
                    } else {
                        // ── Botones adjuntar ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    imagenLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, verde.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = verde),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.AddCircle, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Foto", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            OutlinedButton(
                                onClick = { archivoLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, verde.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = verde),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Description, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Archivo", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF3F4F6))

                        // ── Texto + enviar ──
                        Row(
                            modifier = Modifier
                                .padding(start = 10.dp, end = 8.dp, top = 5.dp, bottom = 5.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = estado.texto,
                                onValueChange = { viewModel.cambiarTexto(it) },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Escribe un mensaje…", color = gris) },
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color(0xFFE5E7EB),
                                    focusedBorderColor = verde,
                                    unfocusedContainerColor = Color(0xFFF9FAFB),
                                    focusedContainerColor = Color.White
                                ),
                                maxLines = 4
                            )
                            Spacer(Modifier.width(8.dp))
                            FilledIconButton(
                                onClick = { viewModel.enviarMensaje(chatId) },
                                enabled = estado.texto.isNotBlank() && !estado.enviando,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = verde)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ───────────── BARRA PREVIEW IMAGEN ─────────────

@Composable
private fun BarraPreviewImagen(
    uri: Uri,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onEnviar: () -> Unit,
    onCancelar: () -> Unit,
    verde: Color,
    gris: Color,
    enviando: Boolean
) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = Color(0xFFE5E7EB))
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AsyncImage(
                    model = uri, contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.TopEnd)
                        .background(Color(0xFF111827).copy(alpha = 0.65f), CircleShape)
                        .clickable(enabled = !enviando, onClick = onCancelar),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, "Cancelar", tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = caption,
                onValueChange = onCaptionChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Añadir descripción…", color = gris, fontSize = 13.sp) },
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                enabled = !enviando,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFFE5E7EB),
                    focusedBorderColor = verde,
                    unfocusedContainerColor = Color(0xFFF9FAFB),
                    focusedContainerColor = Color.White
                )
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onEnviar,
                enabled = !enviando,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = verde)
            ) {
                if (enviando) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = Color.White)
                }
            }
        }
    }
}

// ───────────── BURBUJAS ─────────────

@Composable
private fun BurbujaMensaje(
    msg: MensajeChat,
    esMio: Boolean,
    gris: Color,
    verde: Color,
    sdf: SimpleDateFormat,
    context: android.content.Context
) {
    val shape = RoundedCornerShape(
        topStart = if (esMio) 18.dp else 4.dp,
        topEnd = if (esMio) 4.dp else 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp
    )
    val hora = sdf.format(Date(msg.fecha))

    when (msg.tipo) {
        "imagen" -> {
            Card(
                shape = shape,
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .clickable {
                        if (msg.mediaUrl.isNotEmpty())
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(msg.mediaUrl)))
                    }
            ) {
                Column {
                    AsyncImage(
                        model = msg.mediaUrl,
                        contentDescription = "Imagen",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        contentScale = ContentScale.Crop
                    )
                    if (msg.texto.isNotEmpty()) {
                        Text(
                            msg.texto, fontSize = 13.sp, color = Color(0xFF374151),
                            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 2.dp)
                        )
                    }
                    Text(
                        hora, fontSize = 10.sp, color = gris,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 8.dp, bottom = 4.dp, top = 2.dp)
                    )
                }
            }
        }

        "archivo" -> {
            val ext = msg.mediaNombre.substringAfterLast('.', "").uppercase()
            val extColor = archivoColor(ext, gris)
            Card(
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = if (esMio) verde else Color.White),
                modifier = Modifier
                    .widthIn(max = 270.dp)
                    .clickable {
                        if (msg.mediaUrl.isNotEmpty())
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(msg.mediaUrl)))
                    }
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                if (esMio) Color.White.copy(alpha = 0.18f)
                                else extColor.copy(alpha = 0.12f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            archivoIcono(ext), null,
                            tint = if (esMio) Color.White else extColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            msg.mediaNombre,
                            color = if (esMio) Color.White else Color(0xFF111827),
                            fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (ext.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (esMio) Color.White.copy(alpha = 0.22f)
                                            else extColor.copy(alpha = 0.14f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        ext, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = if (esMio) Color.White else extColor
                                    )
                                }
                            }
                            if (msg.mediaBytes > 0L) {
                                Text(
                                    formatBytes(msg.mediaBytes),
                                    color = if (esMio) Color.White.copy(alpha = 0.75f) else gris,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                Text(
                    hora, fontSize = 10.sp,
                    color = if (esMio) Color.White.copy(alpha = 0.7f) else gris,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 12.dp, bottom = 8.dp)
                )
            }
        }

        else -> {
            Card(
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = if (esMio) verde else Color.White),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        msg.texto,
                        color = if (esMio) Color.White else Color(0xFF111827),
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        hora, fontSize = 10.sp,
                        color = if (esMio) Color.White.copy(alpha = 0.7f) else gris,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

// ───────────── HELPERS ─────────────

private fun archivoColor(ext: String, fallback: Color): Color = when (ext) {
    "PDF" -> Color(0xFFDC2626)
    "DOC", "DOCX" -> Color(0xFF2563EB)
    "XLS", "XLSX" -> Color(0xFF16A34A)
    "PPT", "PPTX" -> Color(0xFFEA580C)
    "ZIP", "RAR", "7Z" -> Color(0xFFF59E0B)
    "MP4", "MOV", "AVI", "MKV", "WEBM" -> Color(0xFF9333EA)
    "MP3", "WAV", "M4A", "OGG", "FLAC" -> Color(0xFFEC4899)
    "JPG", "JPEG", "PNG", "WEBP", "GIF" -> Color(0xFF0891B2)
    else -> fallback
}

private fun archivoIcono(ext: String): ImageVector = when (ext) {
    "PDF" -> Icons.Default.PictureAsPdf
    "DOC", "DOCX", "TXT" -> Icons.Default.Article
    "XLS", "XLSX" -> Icons.Default.TableChart
    "PPT", "PPTX" -> Icons.Default.Slideshow
    "ZIP", "RAR", "7Z" -> Icons.Default.Archive
    "MP4", "MOV", "AVI", "MKV", "WEBM" -> Icons.Default.Movie
    "MP3", "WAV", "M4A", "OGG", "FLAC" -> Icons.Default.MusicNote
    else -> Icons.Default.Description
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes < 1_024L -> "$bytes B"
    bytes < 1_048_576L -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
}
