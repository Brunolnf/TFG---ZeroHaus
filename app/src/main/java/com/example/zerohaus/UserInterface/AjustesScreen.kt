package com.example.zerohaus.UserInterface

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.BuildConfig
import com.example.zerohaus.ViewModel.AjustesViewModel
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.AppPreferencias
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.Util.NotificacionesLocales

// URLs y contacto de la app. Servidas por Firebase Hosting (carpeta /public).
// Si algún día conectas el dominio propio zerohaus.es en Firebase Hosting,
// basta con cambiar el host aquí.
private const val URL_PRIVACIDAD = "https://zerohaus-2a865.web.app/privacidad"
private const val URL_TERMINOS = "https://zerohaus-2a865.web.app/terminos"
private const val URL_DATOS = "https://zerohaus-2a865.web.app/datos"
private const val EMAIL_SOPORTE = "soporte@zerohaus.es"
private const val PLAY_ID = "es.zerohaus.app"

/**
 * Ajustes: notificaciones (push, email, sonido y por tipo), tema, idioma,
 * unidades, seguridad de la cuenta, privacidad y legal, ayuda e información.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    viewModel: AjustesViewModel,
    onVolver: () -> Unit = {},
    onCerrarSesion: () -> Unit = {}
) {
    val c = LocalCadenas.current
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val rojo = MaterialTheme.colorScheme.error
    val estado = viewModel.estado

    val context = LocalContext.current
    val prefs = remember { AppPreferencias(context) }

    var expT by remember { mutableStateOf(false) }
    var expI by remember { mutableStateOf(false) }
    var expE by remember { mutableStateOf(false) }
    var expM by remember { mutableStateOf(false) }

    var mostrarCambiarPass by remember { mutableStateOf(false) }
    var mostrarEliminar by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.cargarAjustes() }

    // Sincroniza preferencias locales y AppEstado con los valores remotos al cargar
    LaunchedEffect(estado.cargando) {
        if (!estado.cargando) {
            val a = estado.ajustes
            if (AppEstado.unidadEnergia != a.unidadEnergia) {
                AppEstado.unidadEnergia = a.unidadEnergia
                prefs.setUnidadEnergia(a.unidadEnergia)
            }
            if (AppEstado.unidadMoneda != a.unidadMoneda) {
                AppEstado.unidadMoneda = a.unidadMoneda
                prefs.setUnidadMoneda(a.unidadMoneda)
            }
            if (AppEstado.notificacionesPush != a.notificacionesPush) {
                AppEstado.notificacionesPush = a.notificacionesPush
                prefs.setNotificacionesPush(a.notificacionesPush)
            }
            if (AppEstado.notificacionesSonido != a.notificacionesSonido) {
                AppEstado.notificacionesSonido = a.notificacionesSonido
                prefs.setNotificacionesSonido(a.notificacionesSonido)
            }
        }
    }

    val temaDisplay = when (AppEstado.tema) {
        "Claro" -> c.ajustesTemaClaro
        "Oscuro" -> c.ajustesTemaDark
        else -> c.ajustesTemaSystem
    }

    val idiomasDisponibles = listOf(
        "Español", "English", "Català", "Euskara", "Galego", "Português",
        "Français", "Deutsch", "Italiano", "العربية", "中文", "Română",
        "Nederlands", "Polski"
    )

    // ── Helpers de intents del sistema (abrir web, valorar, compartir, email) ──
    fun abrirUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            viewModel.mostrarError(c.ajustesEliminarError)
        }
    }

    fun valorarApp() {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PLAY_ID")))
        } catch (_: ActivityNotFoundException) {
            abrirUrl("https://play.google.com/store/apps/details?id=$PLAY_ID")
        }
    }

    fun compartirApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, c.ajustesCompartirTexto)
        }
        context.startActivity(Intent.createChooser(intent, c.ajustesCompartir))
    }

    fun contactarSoporte() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$EMAIL_SOPORTE")
            putExtra(Intent.EXTRA_SUBJECT, c.ajustesContactarAsunto)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            viewModel.mostrarError(c.ajustesContactarSub)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            val msg = estado.mensajeToast ?: estado.error
            ZeroToast(
                mensaje   = msg,
                tipo      = if (estado.error != null) ToastTipo.ERROR else ToastTipo.EXITO,
                alOcultar = { viewModel.limpiarMensaje() }
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(c.ajustesTitulo, fontWeight = FontWeight.SemiBold)
                        Text(c.ajustesSubtitulo, color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, c.volver) }
                }
            )
        },
        bottomBar = {
            if (!estado.cargando) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Button(
                        onClick = { viewModel.guardar() },
                        enabled = !estado.guardando,
                        colors = ButtonDefaults.buttonColors(containerColor = verde),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        if (estado.guardando) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(c.ajustesGuardar, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { pv ->
        if (estado.cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = verde)
            }
        } else {
            Column(
                Modifier
                    .padding(pv)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Notificaciones ──
                AjustesCard(c.ajustesNotificaciones) {
                    SwitchRow(c.ajustesPush, c.ajustesPushSub, estado.ajustes.notificacionesPush, verde) {
                        viewModel.cambiarPush(it); AppEstado.notificacionesPush = it; prefs.setNotificacionesPush(it)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                    SwitchRow(c.ajustesEmail, c.ajustesEmailSub, estado.ajustes.notificacionesEmail, verde) {
                        viewModel.cambiarEmail(it)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                    SwitchRow(c.ajustesSonido, c.ajustesSonidoSub, estado.ajustes.notificacionesSonido, verde) {
                        viewModel.cambiarSonido(it); AppEstado.notificacionesSonido = it; prefs.setNotificacionesSonido(it)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                    SwitchRow(c.ajustesNotifMensajes, c.ajustesNotifMensajesSub, estado.ajustes.notificacionesMensajes, verde) {
                        viewModel.cambiarNotifMensajes(it)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                    SwitchRow(c.ajustesNotifValoraciones, c.ajustesNotifValoracionesSub, estado.ajustes.notificacionesValoraciones, verde) {
                        viewModel.cambiarNotifValoraciones(it)
                    }
                }

                // ── Apariencia (tema + idioma) ──
                AjustesCard(c.ajustesApariencia) {
                    Text(c.ajustesTema, fontSize = 13.sp, color = gris)
                    ExposedDropdownMenuBox(expanded = expT, onExpandedChange = { expT = it }) {
                        OutlinedTextField(
                            value = temaDisplay, onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expT) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            singleLine = true
                        )
                        ExposedDropdownMenu(expanded = expT, onDismissRequest = { expT = false }) {
                            listOf(
                                "Claro" to c.ajustesTemaClaro,
                                "Oscuro" to c.ajustesTemaDark,
                                "Sistema" to c.ajustesTemaSystem
                            ).forEach { (clave, etiqueta) ->
                                DropdownMenuItem(text = { Text(etiqueta) }, onClick = {
                                    viewModel.cambiarTema(clave); AppEstado.tema = clave; prefs.setTema(clave); expT = false
                                })
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(c.ajustesIdioma, fontSize = 13.sp, color = gris)
                    ExposedDropdownMenuBox(expanded = expI, onExpandedChange = { expI = it }) {
                        OutlinedTextField(
                            value = AppEstado.idioma, onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expI) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            singleLine = true
                        )
                        ExposedDropdownMenu(expanded = expI, onDismissRequest = { expI = false }) {
                            idiomasDisponibles.forEach { idioma ->
                                DropdownMenuItem(text = { Text(idioma) }, onClick = {
                                    viewModel.cambiarIdioma(idioma); AppEstado.idioma = idioma; prefs.setIdioma(idioma); expI = false
                                    NotificacionesLocales.crearCanales(context)
                                })
                            }
                        }
                    }
                }

                // ── Preferencias (unidades y moneda) ──
                AjustesCard(c.ajustesPreferencias) {
                    Text(c.ajustesUnidadEnergia, fontSize = 13.sp, color = gris)
                    ExposedDropdownMenuBox(expanded = expE, onExpandedChange = { expE = it }) {
                        OutlinedTextField(
                            value = estado.ajustes.unidadEnergia, onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expE) },
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable), singleLine = true
                        )
                        ExposedDropdownMenu(expanded = expE, onDismissRequest = { expE = false }) {
                            listOf("kWh", "MJ", "kcal").forEach { o ->
                                DropdownMenuItem(text = { Text(o) }, onClick = {
                                    viewModel.cambiarUnidadEnergia(o); AppEstado.unidadEnergia = o; prefs.setUnidadEnergia(o); expE = false
                                })
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(c.ajustesMoneda, fontSize = 13.sp, color = gris)
                    ExposedDropdownMenuBox(expanded = expM, onExpandedChange = { expM = it }) {
                        OutlinedTextField(
                            value = estado.ajustes.unidadMoneda, onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expM) },
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable), singleLine = true
                        )
                        ExposedDropdownMenu(expanded = expM, onDismissRequest = { expM = false }) {
                            listOf("EUR", "USD", "GBP").forEach { o ->
                                DropdownMenuItem(text = { Text(o) }, onClick = {
                                    viewModel.cambiarUnidadMoneda(o); AppEstado.unidadMoneda = o; prefs.setUnidadMoneda(o); expM = false
                                })
                            }
                        }
                    }
                }

                // ── Seguridad de la cuenta ──
                AjustesCard(c.ajustesSeguridadCuenta) {
                    AccionRow(Icons.Filled.Password, c.ajustesCambiarPassword, c.ajustesCambiarPasswordSub, verde) {
                        mostrarCambiarPass = true
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                    AccionRow(Icons.AutoMirrored.Filled.Logout, c.cerrarSesion, c.ajustesCerrarSesionSub, verde) {
                        onCerrarSesion()
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                    AccionRow(Icons.Filled.DeleteForever, c.ajustesEliminarCuenta, c.ajustesEliminarCuentaSub, rojo) {
                        mostrarEliminar = true
                    }
                }

                // ── Privacidad y legal ──
                AjustesCard(c.ajustesPrivacidadLegal) {
                    AccionRow(Icons.Filled.PrivacyTip, c.ajustesPoliticaPrivacidad, c.ajustesPoliticaPrivacidadSub, verde) {
                        abrirUrl(URL_PRIVACIDAD)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                    AccionRow(Icons.AutoMirrored.Filled.Article, c.ajustesTerminos, c.ajustesTerminosSub, verde) {
                        abrirUrl(URL_TERMINOS)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                    AccionRow(Icons.Filled.CloudDownload, c.ajustesGestionDatos, c.ajustesGestionDatosSub, verde) {
                        abrirUrl(URL_DATOS)
                    }
                }

                // ── Ayuda y soporte ──
                AjustesCard(c.ajustesAyudaSoporte) {
                    AccionRow(Icons.Filled.StarRate, c.ajustesValorar, c.ajustesValorarSub, verde) { valorarApp() }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                    AccionRow(Icons.Filled.Share, c.ajustesCompartir, c.ajustesCompartirSub, verde) { compartirApp() }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                    AccionRow(Icons.Filled.SupportAgent, c.ajustesContactar, c.ajustesContactarSub, verde) { contactarSoporte() }
                }

                // ── Información ──
                AjustesCard(c.ajustesInformacion) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = gris, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(c.ajustesVersionLabel, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(BuildConfig.VERSION_NAME, color = gris, fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }

    // ── Diálogo: cambiar contraseña ──
    if (mostrarCambiarPass) {
        CambiarPasswordDialog(
            cargando = estado.cambiandoPassword,
            onCerrar = { mostrarCambiarPass = false },
            onConfirmar = { actual, nueva ->
                viewModel.cambiarPassword(actual, nueva) {
                    mostrarCambiarPass = false
                    viewModel.mostrarMensaje(c.ajustesPasswordCambiada)
                }
            }
        )
    }

    // ── Diálogo: eliminar cuenta ──
    if (mostrarEliminar) {
        AlertDialog(
            onDismissRequest = { if (!estado.eliminandoCuenta) mostrarEliminar = false },
            icon = { Icon(Icons.Filled.DeleteForever, null, tint = rojo) },
            title = { Text(c.ajustesEliminarTitulo, fontWeight = FontWeight.SemiBold) },
            text = { Text(c.ajustesEliminarMensaje) },
            confirmButton = {
                TextButton(
                    enabled = !estado.eliminandoCuenta,
                    onClick = {
                        viewModel.eliminarCuenta {
                            mostrarEliminar = false
                            onCerrarSesion()
                        }
                    }
                ) {
                    if (estado.eliminandoCuenta) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = rojo, strokeWidth = 2.dp)
                    } else {
                        Text(c.ajustesEliminarConfirmarBoton, color = rojo, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !estado.eliminandoCuenta, onClick = { mostrarEliminar = false }) {
                    Text(c.cancelar)
                }
            }
        )
    }
}

@Composable
private fun AjustesCard(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            contenido()
        }
    }
}

@Composable
private fun SwitchRow(
    titulo: String,
    subtitulo: String,
    checked: Boolean,
    colorActivo: Color,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, fontSize = 15.sp)
            Text(subtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colorActivo)
        )
    }
}

@Composable
private fun AccionRow(
    icono: ImageVector,
    titulo: String,
    subtitulo: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icono, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, fontSize = 15.sp)
            Text(subtitulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CambiarPasswordDialog(
    cargando: Boolean,
    onCerrar: () -> Unit,
    onConfirmar: (actual: String, nueva: String) -> Unit
) {
    val c = LocalCadenas.current
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var errorLocal by remember { mutableStateOf<String?>(null) }

    val transform = if (visible) VisualTransformation.None else PasswordVisualTransformation()

    AlertDialog(
        onDismissRequest = { if (!cargando) onCerrar() },
        icon = { Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(c.ajustesCambiarPassword, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = actual, onValueChange = { actual = it; errorLocal = null },
                    label = { Text(c.ajustesPasswordActual) }, singleLine = true,
                    visualTransformation = transform,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nueva, onValueChange = { nueva = it; errorLocal = null },
                    label = { Text(c.ajustesPasswordNueva) }, singleLine = true,
                    visualTransformation = transform,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmar, onValueChange = { confirmar = it; errorLocal = null },
                    label = { Text(c.ajustesPasswordConfirmar) }, singleLine = true,
                    visualTransformation = transform,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (visible) c.ocultar else c.mostrar)
                        }
                    },
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                errorLocal?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !cargando,
                onClick = {
                    when {
                        nueva.length < 8 -> errorLocal = c.contrasenaError
                        nueva != confirmar -> errorLocal = c.registroContrasenasNoCoinciden
                        else -> onConfirmar(actual, nueva)
                    }
                }
            ) {
                if (cargando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(c.guardar, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(enabled = !cargando, onClick = onCerrar) { Text(c.cancelar) }
        }
    )
}
