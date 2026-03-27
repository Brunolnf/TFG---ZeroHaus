
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.AjustesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(viewModel: AjustesViewModel, onVolver: () -> Unit = {}) {
    val verde = Color(0xFF16A34A); val gris = Color(0xFF6B7280); val estado = viewModel.estado
    var expI by remember { mutableStateOf(false) }; var expE by remember { mutableStateOf(false) }; var expM by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.cargarAjustes() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Column { Text("Ajustes", fontWeight = FontWeight.SemiBold); Text("Personaliza tu experiencia", color = gris, fontSize = 12.sp) } },
            navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } })
    }) { pv ->
        if (estado.cargando) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = verde) } }
        else {
            Column(Modifier.padding(pv).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Notificaciones", fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Push", fontSize = 15.sp); Text("Alertas en tu dispositivo", color = gris, fontSize = 12.sp) }; Switch(checked = estado.ajustes.notificacionesPush, onCheckedChange = { viewModel.cambiarPush(it) }, colors = SwitchDefaults.colors(checkedTrackColor = verde)) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Email", fontSize = 15.sp); Text("Resúmenes por correo", color = gris, fontSize = 12.sp) }; Switch(checked = estado.ajustes.notificacionesEmail, onCheckedChange = { viewModel.cambiarEmail(it) }, colors = SwitchDefaults.colors(checkedTrackColor = verde)) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.3f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Sonido", fontSize = 15.sp); Text("Sonido en notificaciones", color = gris, fontSize = 12.sp) }; Switch(checked = estado.ajustes.notificacionesSonido, onCheckedChange = { viewModel.cambiarSonido(it) }, colors = SwitchDefaults.colors(checkedTrackColor = verde)) }
                    }
                }
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Preferencias", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Idioma", fontSize = 13.sp, color = gris)
                        Box(Modifier.fillMaxWidth()) { OutlinedTextField(value = estado.ajustes.idioma, onValueChange = {}, readOnly = true, trailingIcon = { IconButton(onClick = { expI = true }) { Icon(Icons.Default.ArrowDropDown, null) } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), singleLine = true); DropdownMenu(expanded = expI, onDismissRequest = { expI = false }) { listOf("Español","Inglés","Catalán","Euskera","Gallego").forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { viewModel.cambiarIdioma(o); expI = false }) } } }
                        Text("Unidad de energía", fontSize = 13.sp, color = gris)
                        Box(Modifier.fillMaxWidth()) { OutlinedTextField(value = estado.ajustes.unidadEnergia, onValueChange = {}, readOnly = true, trailingIcon = { IconButton(onClick = { expE = true }) { Icon(Icons.Default.ArrowDropDown, null) } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), singleLine = true); DropdownMenu(expanded = expE, onDismissRequest = { expE = false }) { listOf("kWh","MJ","kcal").forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { viewModel.cambiarUnidadEnergia(o); expE = false }) } } }
                        Text("Moneda", fontSize = 13.sp, color = gris)
                        Box(Modifier.fillMaxWidth()) { OutlinedTextField(value = estado.ajustes.unidadMoneda, onValueChange = {}, readOnly = true, trailingIcon = { IconButton(onClick = { expM = true }) { Icon(Icons.Default.ArrowDropDown, null) } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), singleLine = true); DropdownMenu(expanded = expM, onDismissRequest = { expM = false }) { listOf("EUR","USD","GBP").forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { viewModel.cambiarUnidadMoneda(o); expM = false }) } } }
                    }
                }
                Button(onClick = { viewModel.guardar() }, enabled = !estado.guardando, colors = ButtonDefaults.buttonColors(containerColor = verde), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 14.dp)) {
                    if (estado.guardando) { CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text("Guardar ajustes", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                if (estado.exito) { Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5))) { Row(Modifier.padding(12.dp)) { Icon(Icons.Default.CheckCircle, null, tint = verde); Spacer(Modifier.width(8.dp)); Text("Ajustes guardados", color = Color(0xFF065F46)) } } }
                estado.error?.let { Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2))) { Row(Modifier.padding(12.dp)) { Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626)); Spacer(Modifier.width(8.dp)); Text(it, color = Color(0xFF991B1B)) } } }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
