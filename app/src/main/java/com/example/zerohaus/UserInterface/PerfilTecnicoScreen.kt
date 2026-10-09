package com.example.zerohaus.UserInterface

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.Formato
import com.example.zerohaus.Modelos.esDestacado
import com.example.zerohaus.Modelos.esEmpresa
import com.example.zerohaus.Modelos.esVerificado
import com.example.zerohaus.Repositorios.RepositorioEstadisticas
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.Util.Telefono
import com.example.zerohaus.ViewModel.PerfilTecnicoViewModel
import com.example.zerohaus.Util.TextosEnergia
import java.util.*
import com.example.zerohaus.Modelos.Especialidades

/**
 * Perfil público de un profesional: datos, plan, contacto (chat y llamada) y
 * valoraciones. Registra la visita y los contactos para sus estadísticas y
 * solo deja valorar si existe una conversación previa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilTecnicoScreen(
    viewModel: PerfilTecnicoViewModel,
    tecnicoId: String,
    onVolver: () -> Unit = {},
    onContactar: (tecnicoUid: String, tecnicoNombre: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val c = LocalCadenas.current
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val fondo = MaterialTheme.colorScheme.background
    val borde = MaterialTheme.colorScheme.outline
    val morado = Color(0xFF7C3AED)
    val dorado = Color(0xFFF59E0B)
    val estado = viewModel.estado
    val sdf = remember(AppEstado.idioma) { Formato.fechas("dd/MM/yyyy") }

    var mostrarFormResena by remember { mutableStateOf(false) }
    var puntuacion by remember { mutableIntStateOf(5) }
    var comentario by remember { mutableStateOf("") }
    var errorLocal by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tecnicoId) { viewModel.cargarTecnico(tecnicoId) }

    Scaffold(
        containerColor = fondo,
        snackbarHost = {
            ZeroToast(
                mensaje   = errorLocal ?: estado.error,
                tipo      = ToastTipo.ERROR,
                alOcultar = { errorLocal = null; viewModel.limpiarMensajes() }
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (estado.tecnico?.esEmpresa == true) c.perfTituloEmpresa else c.perfTituloTecnico,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (estado.tecnico != null) {
                            Text(estado.tecnico.nombre, color = gris, fontSize = 12.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, c.volver) }
                }
            )
        }
    ) { pv ->
        if (estado.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = verde)
            }
        } else if (estado.tecnico == null) {
            Box(Modifier.fillMaxSize().padding(pv), contentAlignment = Alignment.Center) {
                Text(c.perfNoEncontrado, color = gris)
            }
        } else {
            val t = estado.tecnico
            LazyColumn(
                modifier = Modifier
                    .padding(pv)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // ---- VISTA PREVIA (el profesional mirando su propio perfil) ----
                if (estado.esMiPerfil) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0891B2).copy(0.1f)),
                            border = BorderStroke(1.dp, Color(0xFF0891B2).copy(0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Visibility, null, tint = Color(0xFF0891B2), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    c.perfVistaPrevia,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // ---- HERO CARD ----
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, if (t.esDestacado) dorado.copy(0.5f) else borde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                color = (if (t.esEmpresa) morado else verde).copy(alpha = 0.12f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (t.esEmpresa) {
                                        Icon(Icons.Default.Apartment, null, tint = morado, modifier = Modifier.size(38.dp))
                                    } else {
                                        Text(
                                            t.nombre.take(1).uppercase(),
                                            color = verde,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 32.sp
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Text(t.nombre, fontWeight = FontWeight.Bold, fontSize = 22.sp)

                            Spacer(Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background((if (t.esEmpresa) morado else verde).copy(0.12f))
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        if (t.esEmpresa) Icons.Default.Apartment else Icons.Default.Engineering,
                                        null,
                                        tint = if (t.esEmpresa) morado else verde,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        if (t.esEmpresa) c.tecEmpresaReformas else c.tecTecnicoCertificador,
                                        color = if (t.esEmpresa) morado else verde,
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                                // Badge de verificación por suscripción
                                if (t.esDestacado) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(dorado.copy(0.13f))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Icon(Icons.Default.Star, null, tint = dorado, modifier = Modifier.size(14.dp))
                                        Text(c.estDestacado, color = Color(0xFF92400E), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                } else if (t.esVerificado) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xFF065F46).copy(0.13f))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF065F46), modifier = Modifier.size(14.dp))
                                        Text(c.estVerificado, color = Color(0xFF065F46), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            if (t.ciudad.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, null, tint = gris, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text(t.ciudad, color = gris, fontSize = 14.sp)
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilaEstrellas(t.rating, tamano = 22.dp)
                                Text(Formato.numero(t.rating), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(Formato.cantidad(t.opiniones, c.comValoracion, c.comValoraciones), color = gris, fontSize = 13.sp)
                        }
                    }
                }

                // ---- DESCRIPCIÓN ----
                if (t.descripcion.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, borde),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .background(verde)
                                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                                )
                                Text(
                                    t.descripcion,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(14.dp),
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }

                // ---- ESPECIALIDADES ----
                val especialidades = Especialidades.paraMostrar(t.especialidades)
                if (especialidades.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, borde),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(c.perfEspecialidades, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    especialidades.forEach { esp ->
                                        Surface(shape = RoundedCornerShape(20.dp), color = verde.copy(alpha = 0.08f)) {
                                            Text(
                                                TextosEnergia.especialidad(esp, c),
                                                color = verde,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- CONTACTO (siempre visible para clientes) ----
                if (!estado.esMiPerfil) {
                    item {
                        ContactoCard(
                            telefono = t.telefono,
                            email = t.emailContacto,
                            verde = verde, gris = gris, borde = borde,
                            onChatear = {
                                viewModel.registrarContacto(tecnicoId, RepositorioEstadisticas.CHAT)
                                onContactar(t.uid.ifBlank { t.id }, t.nombre)
                            },
                            onLlamar = { tel ->
                                viewModel.registrarContacto(tecnicoId, RepositorioEstadisticas.LLAMADA)
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Telefono.formatoMarcado(tel)}"))
                                // Tablets y móviles sin teléfono: no hay quien marque
                                try { context.startActivity(intent) }
                                catch (_: ActivityNotFoundException) { errorLocal = c.perfSinLlamadas }
                            }
                        )
                    }
                }

                // ---- BOTÓN VALORAR ----
                if (estado.puedeValorar) {
                    item {
                        OutlinedButton(
                            onClick = { mostrarFormResena = true },
                            border = BorderStroke(1.dp, verde),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = verde)
                        ) {
                            Icon(Icons.Default.Star, null, tint = verde, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (estado.yaValorado) c.perfEditarValoracion else c.perfEscribirValoracion,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else if (!estado.esMiPerfil && !estado.cargando) {
                    item {
                        Text(
                            c.perfValorarRequiereChat,
                            color = gris,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        )
                    }
                }

                // ---- ÉXITO RESEÑA ----
                if (estado.exitoResena) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5))
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = verde)
                                Spacer(Modifier.width(8.dp))
                                Text(c.perfValoracionPublicada, color = Color(0xFF065F46))
                            }
                        }
                    }
                }

                // ---- SECCIÓN VALORACIONES ----
                item {
                    Text(
                        "${c.perfValoraciones} (${estado.resenas.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }

                if (estado.resenas.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.StarBorder,
                                    null,
                                    tint = gris.copy(0.4f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(c.perfSinValoraciones, color = gris)
                            }
                        }
                    }
                }

                items(estado.resenas) { r ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, borde),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    color = gris.copy(alpha = 0.15f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            r.nombreUsuario.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = gris
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(r.nombreUsuario, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(sdf.format(Date(r.fecha)), color = gris, fontSize = 11.sp)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            FilaEstrellas(r.puntuacion.toDouble(), tamano = 16.dp)
                            if (r.comentario.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(r.comentario, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- DIÁLOGO RESEÑA ----
    if (mostrarFormResena) {
        val labelColor = when (puntuacion) {
            5 -> Color(0xFF16A34A)
            4 -> Color(0xFF2563EB)
            3 -> Color(0xFFD97706)
            2 -> Color(0xFFDC2626)
            else -> Color(0xFF7F1D1D)
        }
        val labelTexto = when (puntuacion) {
            5 -> c.perfExcelente; 4 -> c.perfBueno; 3 -> c.perfRegular; 2 -> c.perfMalo; else -> c.perfMuyMalo
        }
        LaunchedEffect(estado.exitoResena) {
            if (estado.exitoResena && mostrarFormResena) {
                mostrarFormResena = false
                comentario = ""
                puntuacion = 5
            }
        }
        AlertDialog(
            onDismissRequest = { if (!estado.enviandoResena) mostrarFormResena = false },
            title = { Text(c.perfValorarProfesional, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(c.perfPuntuacion, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { i ->
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (puntuacion >= i) Color(0xFFFFC107) else Color(0xFFBDBDBD),
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable { puntuacion = i }
                            )
                        }
                    }
                    Text(labelTexto, color = labelColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    OutlinedTextField(
                        value = comentario,
                        onValueChange = { if (it.length <= 1000) comentario = it },
                        label = { Text(c.perfComentarioLabel) },
                        placeholder = { Text(c.perfComentarioPlaceholder, fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        supportingText = {
                            Text(
                                "${comentario.length} / 1000",
                                fontSize = 11.sp,
                                color = if (comentario.length >= 950)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    if (estado.yaValorado) {
                        Text(c.perfSustituida, fontSize = 12.sp, color = gris)
                    }
                    if (estado.enviandoResena) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF16A34A))
                    }
                    estado.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.publicarResena(tecnicoId, puntuacion, comentario) },
                    enabled = !estado.enviandoResena && comentario.trim().length >= 10,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    if (estado.enviandoResena) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(c.perfPublicar, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { mostrarFormResena = false },
                    enabled = !estado.enviandoResena
                ) { Text(c.cancelar) }
            }
        )
    }
}

@Composable
private fun ContactoCard(
    telefono: String,
    email: String,
    verde: Color,
    gris: Color,
    borde: Color,
    onChatear: () -> Unit,
    onLlamar: (String) -> Unit
) {
    val c = LocalCadenas.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, verde.copy(0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ContactPhone, null, tint = verde, modifier = Modifier.size(18.dp))
                Text(c.perfContacto, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = verde)
            }
            if (telefono.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Phone, null, tint = gris, modifier = Modifier.size(16.dp))
                    Text(Telefono.formatoVisible(telefono), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (email.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.MailOutline, null, tint = gris, modifier = Modifier.size(16.dp))
                    Text(email, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (telefono.isBlank() && email.isBlank()) {
                Text(
                    c.perfSinContacto,
                    color = gris, fontSize = 12.sp
                )
            }
            HorizontalDivider(color = borde)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onChatear,
                    colors = ButtonDefaults.buttonColors(containerColor = verde),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(c.perfChatear, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                if (telefono.isNotBlank()) {
                    OutlinedButton(
                        onClick = { onLlamar(telefono) },
                        border = BorderStroke(1.dp, verde),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = verde),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Phone, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(c.perfLlamar, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

