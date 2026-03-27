package com.example.zerohaus.UserInterface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.PreestudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreestudioScreen(
    viewModel: PreestudioViewModel,
    onVolver: () -> Unit = {},
    onInformeGenerado: () -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)
    val estado = viewModel.estado

    var expandVentanas by remember { mutableStateOf(false) }
    var expandAislamiento by remember { mutableStateOf(false) }
    var expandCalefaccion by remember { mutableStateOf(false) }
    var expandAcs by remember { mutableStateOf(false) }
    var expandOrientacion by remember { mutableStateOf(false) }

    // Cuando se genera el informe, navegar automáticamente
    LaunchedEffect(estado.informeGenerado) {
        if (estado.informeGenerado != null) onInformeGenerado()
    }

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Preestudio energético", fontWeight = FontWeight.SemiBold)
                        Text("Completa los datos de tu vivienda", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { ps ->
        Column(Modifier.padding(ps).fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Datos básicos
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        CabeceraSeccion(Color(0xFFD1FAE5), Color(0xFF059669), Icons.Default.Info, "Datos básicos")
                        Spacer(Modifier.height(10.dp))
                        EtiquetaCampo("Nombre de la vivienda")
                        CampoTexto(estado.nombreVivienda, { viewModel.cambiarNombre(it) }, "Ej: Mi casa", borde)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                EtiquetaCampo("Superficie (m²)")
                                CampoTexto(estado.superficie, { viewModel.cambiarSuperficie(it) }, "100", borde)
                            }
                            Column(Modifier.weight(1f)) {
                                EtiquetaCampo("Año construcción")
                                CampoTexto(estado.anio, { viewModel.cambiarAnio(it) }, "2000", borde)
                            }
                        }
                    }
                }

                // Envolvente térmica
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        CabeceraSeccion(Color(0xFFDBEAFE), Color(0xFF2563EB), Icons.Default.Warning, "Envolvente térmica")
                        Spacer(Modifier.height(10.dp))
                        SelectorCompacto("Tipo de ventanas", estado.ventanas,
                            listOf("Vidrio simple", "Doble acristalamiento", "Triple"),
                            expandVentanas, { expandVentanas = it }, { viewModel.cambiarVentanas(it) }, borde)
                        Spacer(Modifier.height(10.dp))
                        SelectorCompacto("Aislamiento", estado.aislamiento,
                            listOf("Sin aislamiento", "Aislamiento parcial", "Aislamiento completo"),
                            expandAislamiento, { expandAislamiento = it }, { viewModel.cambiarAislamiento(it) }, borde)
                    }
                }

                // Sistemas energéticos
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        CabeceraSeccion(Color(0xFFEDE9FE), Color(0xFF7C3AED), Icons.Default.Build, "Sistemas energéticos")
                        Spacer(Modifier.height(10.dp))
                        SelectorCompacto("Calefacción", estado.calefaccion,
                            listOf("Caldera de gas", "Eléctrica", "Aerotermia", "Biomasa"),
                            expandCalefaccion, { expandCalefaccion = it }, { viewModel.cambiarCalefaccion(it) }, borde)
                        Spacer(Modifier.height(10.dp))
                        SelectorCompacto("ACS", estado.acs,
                            listOf("Gas", "Eléctrico", "Solar térmica", "Aerotermia"),
                            expandAcs, { expandAcs = it }, { viewModel.cambiarAcs(it) }, borde)
                    }
                }

                // Ubicación
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        CabeceraSeccion(Color(0xFFFFEDD5), Color(0xFFEA580C), Icons.Default.LocationOn, "Ubicación")
                        Spacer(Modifier.height(10.dp))
                        EtiquetaCampo("Dirección")
                        CampoTexto(estado.direccion, { viewModel.cambiarDireccion(it) }, "Calle, número, ciudad", borde)
                        Spacer(Modifier.height(10.dp))
                        SelectorCompacto("Orientación", estado.orientacion,
                            listOf("Norte", "Sur", "Este", "Oeste"),
                            expandOrientacion, { expandOrientacion = it }, { viewModel.cambiarOrientacion(it) }, borde)
                    }
                }

                // Error
                estado.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                // Botón generar informe
                Button(
                    onClick = { viewModel.generarInforme() },
                    enabled = !estado.cargando,
                    colors = ButtonDefaults.buttonColors(containerColor = verde),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    if (estado.cargando) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Generar informe energético", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun CabeceraSeccion(iconoColorFondo: Color, iconoTint: Color, icono: androidx.compose.ui.graphics.vector.ImageVector, titulo: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(30.dp).background(iconoColorFondo, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icono, null, tint = iconoTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun EtiquetaCampo(texto: String) {
    Text(texto, fontSize = 12.sp, color = Color(0xFF111827))
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun CampoTexto(valor: String, onValor: (String) -> Unit, placeholder: String, borde: Color) {
    OutlinedTextField(
        value = valor, onValueChange = onValor,
        placeholder = { Text(placeholder, fontSize = 13.sp) },
        singleLine = true, shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = borde, focusedBorderColor = borde,
            unfocusedContainerColor = Color.White, focusedContainerColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SelectorCompacto(
    etiqueta: String, valor: String, opciones: List<String>,
    expandido: Boolean, onExpandido: (Boolean) -> Unit,
    onSeleccion: (String) -> Unit, borde: Color
) {
    EtiquetaCampo(etiqueta)
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor, onValueChange = {}, readOnly = true, singleLine = true,
            trailingIcon = {
                IconButton(onClick = { onExpandido(true) }) {
                    Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF6B7280))
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = borde, focusedBorderColor = borde,
                unfocusedContainerColor = Color.White, focusedContainerColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expandido, onDismissRequest = { onExpandido(false) }) {
            opciones.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSeleccion(opt); onExpandido(false) })
            }
        }
    }
}
