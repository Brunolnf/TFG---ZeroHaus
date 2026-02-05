package com.example.zerohaus.UserInterface


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TecnicoUi(
    val id: String,
    val nombre: String,
    val rating: Double,
    val opiniones: Int,
    val distanciaKm: Double,
    val especialidades: List<String>,
    val proyectos: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TecnicosScreen(
    onVolver: () -> Unit = {},
    onVerPerfil: (String) -> Unit = {},
    onSolicitarPresupuesto: (String) -> Unit = {}
) {
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)

    var busqueda by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf<String?>(null) }
    var mostrarFiltros by remember { mutableStateOf(false) }

    val especialidades = listOf(
        "Aislamiento", "Ventanas", "Calefacción", "Fotovoltaica", "Aerotermia",
        "Auditorías", "Rehabilitación", "Biomasa", "Certificación", "Consultoría"
    )

    val tecnicos = remember {
        listOf(
            TecnicoUi(
                id = "1",
                nombre = "EcoReformas Madrid",
                rating = 4.8,
                opiniones = 127,
                distanciaKm = 2.3,
                especialidades = listOf("Aislamiento", "Ventanas", "Calefacción"),
                proyectos = 85
            ),
            TecnicoUi(
                id = "2",
                nombre = "Juan Pérez – Técnico Certificado",
                rating = 4.9,
                opiniones = 203,
                distanciaKm = 4.1,
                especialidades = listOf("Aerotermia", "Fotovoltaica", "Certificación"),
                proyectos = 142
            ),
            TecnicoUi(
                id = "3",
                nombre = "SolarPro Consulting",
                rating = 4.7,
                opiniones = 92,
                distanciaKm = 5.6,
                especialidades = listOf("Fotovoltaica", "Consultoría"),
                proyectos = 60
            )
        )
    }

    val filtrados = tecnicos
        .filter {
            val q = busqueda.trim().lowercase()
            q.isBlank() ||
                    it.nombre.lowercase().contains(q) ||
                    it.especialidades.any { e -> e.lowercase().contains(q) }
        }
        .filter { t -> filtro == null || t.especialidades.contains(filtro!!) }

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Técnicos certificados", fontWeight = FontWeight.SemiBold)
                        Text("${filtrados.size} profesionales disponibles", color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { pv ->
        LazyColumn(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = busqueda,
                        onValueChange = { busqueda = it },
                        placeholder = { Text("Buscar por nombre o especialidad…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = borde,
                            focusedBorderColor = borde,
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White
                        )
                    )

                    OutlinedButton(
                        onClick = { mostrarFiltros = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, borde),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF111827))
                        Spacer(Modifier.width(6.dp))
                        Text("Filtros", color = Color(0xFF111827))
                    }

                    DropdownMenu(
                        expanded = mostrarFiltros,
                        onDismissRequest = { mostrarFiltros = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sin filtro") },
                            onClick = { filtro = null; mostrarFiltros = false }
                        )
                        especialidades.forEach { esp ->
                            DropdownMenuItem(
                                text = { Text(esp) },
                                onClick = { filtro = esp; mostrarFiltros = false }
                            )
                        }
                    }
                }

                if (filtro != null) {
                    Spacer(Modifier.height(10.dp))
                    AssistChip(
                        onClick = { filtro = null },
                        label = { Text("Filtro: $filtro ✕") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = verde.copy(alpha = 0.12f),
                            labelColor = verde
                        )
                    )
                }

                Spacer(Modifier.height(2.dp))
            }

            items(filtrados) { t ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, borde),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(t.nombre, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilaEstrellas(rating = t.rating)
                            Spacer(Modifier.width(8.dp))
                            Text("${t.rating} (${t.opiniones} opiniones)", color = gris, fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("${t.distanciaKm} km", color = gris, fontSize = 12.sp)
                        Text("Especialidades: ${t.especialidades.joinToString()}", color = gris, fontSize = 12.sp)
                        Text("${t.proyectos} proyectos completados", color = gris, fontSize = 12.sp)

                        Spacer(Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { onVerPerfil(t.id) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, verde),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = verde)
                            ) {
                                Text("Ver perfil", fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = { onSolicitarPresupuesto(t.id) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = verde),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text("Solicitar presupuesto", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaEstrellas(rating: Double) {
    val amarillo = Color(0xFFFFC107)
    val gris = Color(0xFFBDBDBD)
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            val llena = rating >= (i + 1) - 0.01
            Icon(
                imageVector = if (llena) Icons.Default.Star else Icons.Default.Star,
                contentDescription = null,
                tint = if (llena) amarillo else gris,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
