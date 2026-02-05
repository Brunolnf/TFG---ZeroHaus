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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreestudioScreen(
    onVolver: () -> Unit = {},
    onGenerarInforme: () -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)

    var nombreVivienda by remember { mutableStateOf("Mi vivienda principal") }
    var superficie by remember { mutableStateOf("100") }
    var anio by remember { mutableStateOf("2000") }

    var ventanas by remember { mutableStateOf("Vidrio simple") }
    var aislamiento by remember { mutableStateOf("Aislamiento parcial") }

    var calefaccion by remember { mutableStateOf("Caldera de gas") }
    var acs by remember { mutableStateOf("Gas") }

    var direccion by remember { mutableStateOf("") }
    var orientacion by remember { mutableStateOf("Sur") }

    var expandVentanas by remember { mutableStateOf(false) }
    var expandAislamiento by remember { mutableStateOf(false) }
    var expandCalefaccion by remember { mutableStateOf(false) }
    var expandAcs by remember { mutableStateOf(false) }
    var expandOrientacion by remember { mutableStateOf(false) }

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
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { ps ->
        Column(
            modifier = Modifier
                .padding(ps)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        CabeceraSeccion(
                            iconoColorFondo = Color(0xFFD1FAE5),
                            iconoTint = Color(0xFF059669),
                            icono = Icons.Default.Info,
                            titulo = "Datos básicos"
                        )

                        Spacer(Modifier.height(10.dp))

                        EtiquetaCampo("Nombre de la vivienda")
                        CampoTexto(
                            valor = nombreVivienda,
                            onValor = { nombreVivienda = it },
                            placeholder = "Ej: Mi casa, Piso Madrid…",
                            borde = borde
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                EtiquetaCampo("Superficie (m²)")
                                CampoTexto(
                                    valor = superficie,
                                    onValor = { superficie = it },
                                    placeholder = "100",
                                    borde = borde
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                EtiquetaCampo("Año construcción")
                                CampoTexto(
                                    valor = anio,
                                    onValor = { anio = it },
                                    placeholder = "2000",
                                    borde = borde
                                )
                            }
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        CabeceraSeccion(
                            iconoColorFondo = Color(0xFFDBEAFE),
                            iconoTint = Color(0xFF2563EB),
                            icono = Icons.Default.Warning,
                            titulo = "Envolvente térmica"
                        )

                        Spacer(Modifier.height(10.dp))

                        SelectorCompacto(
                            etiqueta = "Tipo de ventanas",
                            valor = ventanas,
                            opciones = listOf("Vidrio simple", "Doble acristalamiento", "Triple"),
                            expandido = expandVentanas,
                            onExpandido = { expandVentanas = it },
                            onSeleccion = { ventanas = it },
                            borde = borde
                        )

                        Spacer(Modifier.height(10.dp))

                        SelectorCompacto(
                            etiqueta = "Aislamiento",
                            valor = aislamiento,
                            opciones = listOf("Sin aislamiento", "Aislamiento parcial", "Aislamiento completo"),
                            expandido = expandAislamiento,
                            onExpandido = { expandAislamiento = it },
                            onSeleccion = { aislamiento = it },
                            borde = borde
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        CabeceraSeccion(
                            iconoColorFondo = Color(0xFFEDE9FE),
                            iconoTint = Color(0xFF7C3AED),
                            icono = Icons.Default.Build,
                            titulo = "Sistemas energéticos"
                        )

                        Spacer(Modifier.height(10.dp))

                        SelectorCompacto(
                            etiqueta = "Calefacción",
                            valor = calefaccion,
                            opciones = listOf("Caldera de gas", "Eléctrica", "Aerotermia", "Biomasa"),
                            expandido = expandCalefaccion,
                            onExpandido = { expandCalefaccion = it },
                            onSeleccion = { calefaccion = it },
                            borde = borde
                        )

                        Spacer(Modifier.height(10.dp))

                        SelectorCompacto(
                            etiqueta = "ACS",
                            valor = acs,
                            opciones = listOf("Gas", "Eléctrico", "Solar térmica", "Aerotermia"),
                            expandido = expandAcs,
                            onExpandido = { expandAcs = it },
                            onSeleccion = { acs = it },
                            borde = borde
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        CabeceraSeccion(
                            iconoColorFondo = Color(0xFFFFEDD5),
                            iconoTint = Color(0xFFEA580C),
                            icono = Icons.Default.LocationOn,
                            titulo = "Ubicación"
                        )

                        Spacer(Modifier.height(10.dp))

                        EtiquetaCampo("Dirección")
                        CampoTexto(
                            valor = direccion,
                            onValor = { direccion = it },
                            placeholder = "Calle, número, ciudad",
                            borde = borde
                        )

                        Spacer(Modifier.height(10.dp))

                        SelectorCompacto(
                            etiqueta = "Orientación",
                            valor = orientacion,
                            opciones = listOf("Norte", "Sur", "Este", "Oeste"),
                            expandido = expandOrientacion,
                            onExpandido = { expandOrientacion = it },
                            onSeleccion = { orientacion = it },
                            borde = borde
                        )
                    }
                }

                Button(
                    onClick = onGenerarInforme,
                    colors = ButtonDefaults.buttonColors(containerColor = verde),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text("Generar informe energético", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun CabeceraSeccion(
    iconoColorFondo: Color,
    iconoTint: Color,
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    titulo: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(iconoColorFondo, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icono, contentDescription = null, tint = iconoTint, modifier = Modifier.size(18.dp))
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
private fun CampoTexto(
    valor: String,
    onValor: (String) -> Unit,
    placeholder: String,
    borde: Color
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValor,
        placeholder = { Text(placeholder, fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = borde,
            focusedBorderColor = borde,
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SelectorCompacto(
    etiqueta: String,
    valor: String,
    opciones: List<String>,
    expandido: Boolean,
    onExpandido: (Boolean) -> Unit,
    onSeleccion: (String) -> Unit,
    borde: Color
) {
    EtiquetaCampo(etiqueta)

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { onExpandido(true) }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF6B7280))
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = borde,
                focusedBorderColor = borde,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        DropdownMenu(
            expanded = expandido,
            onDismissRequest = { onExpandido(false) }
        ) {
            opciones.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onSeleccion(opt)
                        onExpandido(false)
                    }
                )
            }
        }
    }
}
@Preview
@Composable
fun PreestudioPreview(){

    PreestudioScreen()
}