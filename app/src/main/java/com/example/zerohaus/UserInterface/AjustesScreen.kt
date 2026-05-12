package com.example.zerohaus.UserInterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.AjustesViewModel
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.AppPreferencias
import com.example.zerohaus.Util.LocalCadenas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(viewModel: AjustesViewModel, onVolver: () -> Unit = {}) {
    val c = LocalCadenas.current
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val estado = viewModel.estado

    val context = LocalContext.current
    val prefs = remember { AppPreferencias(context) }

    var expT by remember { mutableStateOf(false) }
    var expI by remember { mutableStateOf(false) }
    var expE by remember { mutableStateOf(false) }
    var expM by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.cargarAjustes() }

    val temaDisplay = when (AppEstado.tema) {
        "Claro" -> c.ajustesTemaClaro
        "Oscuro" -> c.ajustesTemaDark
        else -> c.ajustesTemaSystem
    }

    val idiomasDisponibles = listOf("Español", "English", "Català", "Euskara", "Galego", "Português", "Français", "Deutsch", "Italiano")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                // Notificaciones
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(c.ajustesNotificaciones, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.ajustesPush, fontSize = 15.sp)
                                Text(c.ajustesPushSub, color = gris, fontSize = 12.sp)
                            }
                            Switch(checked = estado.ajustes.notificacionesPush, onCheckedChange = { viewModel.cambiarPush(it) }, colors = SwitchDefaults.colors(checkedTrackColor = verde))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Email", fontSize = 15.sp)
                                Text(c.ajustesEmailSub, color = gris, fontSize = 12.sp)
                            }
                            Switch(checked = estado.ajustes.notificacionesEmail, onCheckedChange = { viewModel.cambiarEmail(it) }, colors = SwitchDefaults.colors(checkedTrackColor = verde))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.ajustesSonido, fontSize = 15.sp)
                                Text(c.ajustesSonidoSub, color = gris, fontSize = 12.sp)
                            }
                            Switch(checked = estado.ajustes.notificacionesSonido, onCheckedChange = { viewModel.cambiarSonido(it) }, colors = SwitchDefaults.colors(checkedTrackColor = verde))
                        }
                    }
                }

                // Apariencia (tema + idioma)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(c.ajustesApariencia, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                        // Selector de tema
                        Text(c.ajustesTema, fontSize = 13.sp, color = gris)
                        ExposedDropdownMenuBox(expanded = expT, onExpandedChange = { expT = it }) {
                            OutlinedTextField(
                                value = temaDisplay,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expT) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                singleLine = true
                            )
                            ExposedDropdownMenu(expanded = expT, onDismissRequest = { expT = false }) {
                                listOf(
                                    "Claro" to c.ajustesTemaClaro,
                                    "Oscuro" to c.ajustesTemaDark,
                                    "Sistema" to c.ajustesTemaSystem
                                ).forEach { (clave, etiqueta) ->
                                    DropdownMenuItem(
                                        text = { Text(etiqueta) },
                                        onClick = {
                                            viewModel.cambiarTema(clave)
                                            AppEstado.tema = clave
                                            prefs.setTema(clave)
                                            expT = false
                                        }
                                    )
                                }
                            }
                        }

                        // Selector de idioma
                        Text(c.ajustesIdioma, fontSize = 13.sp, color = gris)
                        ExposedDropdownMenuBox(expanded = expI, onExpandedChange = { expI = it }) {
                            OutlinedTextField(
                                value = AppEstado.idioma,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expI) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                singleLine = true
                            )
                            ExposedDropdownMenu(expanded = expI, onDismissRequest = { expI = false }) {
                                idiomasDisponibles.forEach { idioma ->
                                    DropdownMenuItem(
                                        text = { Text(idioma) },
                                        onClick = {
                                            viewModel.cambiarIdioma(idioma)
                                            AppEstado.idioma = idioma
                                            prefs.setIdioma(idioma)
                                            expI = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Preferencias (unidades y moneda)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(c.ajustesPreferencias, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                        Text(c.ajustesUnidadEnergia, fontSize = 13.sp, color = gris)
                        ExposedDropdownMenuBox(expanded = expE, onExpandedChange = { expE = it }) {
                            OutlinedTextField(
                                value = estado.ajustes.unidadEnergia, onValueChange = {}, readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expE) },
                                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), singleLine = true
                            )
                            ExposedDropdownMenu(expanded = expE, onDismissRequest = { expE = false }) {
                                listOf("kWh", "MJ", "kcal").forEach { o ->
                                    DropdownMenuItem(text = { Text(o) }, onClick = { viewModel.cambiarUnidadEnergia(o); expE = false })
                                }
                            }
                        }

                        Text(c.ajustesMoneda, fontSize = 13.sp, color = gris)
                        ExposedDropdownMenuBox(expanded = expM, onExpandedChange = { expM = it }) {
                            OutlinedTextField(
                                value = estado.ajustes.unidadMoneda, onValueChange = {}, readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expM) },
                                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), singleLine = true
                            )
                            ExposedDropdownMenu(expanded = expM, onDismissRequest = { expM = false }) {
                                listOf("EUR", "USD", "GBP").forEach { o ->
                                    DropdownMenuItem(text = { Text(o) }, onClick = { viewModel.cambiarUnidadMoneda(o); expM = false })
                                }
                            }
                        }
                    }
                }

                // Guardar
                Button(
                    onClick = { viewModel.guardar() },
                    enabled = !estado.guardando,
                    colors = ButtonDefaults.buttonColors(containerColor = verde),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    if (estado.guardando) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(c.ajustesGuardar, color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                if (estado.exito) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5))
                    ) {
                        Row(Modifier.padding(12.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = verde)
                            Spacer(Modifier.width(8.dp))
                            Text(c.ajustesGuardados, color = Color(0xFF065F46))
                        }
                    }
                }

                estado.error?.let {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2))
                    ) {
                        Row(Modifier.padding(12.dp)) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626))
                            Spacer(Modifier.width(8.dp))
                            Text(it, color = Color(0xFF991B1B))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
