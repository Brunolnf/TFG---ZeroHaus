package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Modelo simple para cada técnico
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
    // Volver atrás
    onVolver: () -> Unit = {},

    // Abrir perfil
    onVerPerfil: (String) -> Unit = {},

    // Iniciar solicitud de presupuesto (no implementado)
    onSolicitarPresupuesto: (String) -> Unit = {}
) {
    // Colores base (mismo estilo que el resto de pantallas)
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)

    // Estado del buscador
    var busqueda by remember { mutableStateOf("") }

    // Filtro seleccionado
    var filtro by remember { mutableStateOf<String?>(null) }

    // Controla el desplegable de filtros
    var mostrarFiltros by remember { mutableStateOf(false) }

    // Lista de especialidades disponibles para filtrar
    val especialidades = listOf(
        "Aislamiento", "Ventanas", "Calefacción", "Fotovoltaica", "Aerotermia",
        "Auditorías", "Rehabilitación", "Biomasa", "Certificación", "Consultoría"
    )

    // Lista de técnicos de ejemplo
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

    // Aplicación de filtros:
    // filtra por búsqueda segun el nombre o la especialidad
    // filtra por la especialidad elegida
    val filtrados = tecnicos
        .filter {
            val q = busqueda.trim().lowercase()
            q.isBlank() ||
                    it.nombre.lowercase().contains(q) ||
                    it.especialidades.any { e -> e.lowercase().contains(q) }
        }
        .filter { t -> filtro == null || t.especialidades.contains(filtro) }

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                // Título y contador de resultados
                title = {
                    Column {
                        Text("Técnicos certificados", fontWeight = FontWeight.SemiBold)
                        Text("${filtrados.size} profesionales disponibles", color = gris, fontSize = 12.sp)
                    }
                },
                // Botón volver
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { pv ->

        // Lista principal
        LazyColumn(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Bloque superior: buscador + botón filtros + chip si hay filtro activo
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    // Campo de búsqueda
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

                    // Botón que abre el menú de filtros
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

                    // Menú desplegable de filtros
                    DropdownMenu(
                        expanded = mostrarFiltros,
                        onDismissRequest = { mostrarFiltros = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sin filtro") },
                            onClick = {
                                filtro = null
                                mostrarFiltros = false
                            }
                        )
                        especialidades.forEach { esp ->
                            DropdownMenuItem(
                                text = { Text(esp) },
                                onClick = {
                                    filtro = esp
                                    mostrarFiltros = false
                                }
                            )
                        }
                    }
                }

                // Si hay un filtro seleccionado, se muestra un chip para quitarlo rápido al clickarlo
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

            // Tarjetas de técnicos filtrados
            items(filtrados) { t ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, borde),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {

                        // Nombre del técnico/empresa
                        Text(t.nombre, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))

                        // Rating con estrellas mas un texto informativo
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilaEstrellas(rating = t.rating)
                            Spacer(Modifier.width(8.dp))
                            Text("${t.rating} (${t.opiniones} opiniones)", color = gris, fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(8.dp))

                        // Datos extra
                        Text("${t.distanciaKm} km", color = gris, fontSize = 12.sp)
                        Text("Especialidades: ${t.especialidades.joinToString()}", color = gris, fontSize = 12.sp)
                        Text("${t.proyectos} proyectos completados", color = gris, fontSize = 12.sp)

                        Spacer(Modifier.height(12.dp))

                        // Acciones de ver perfil y solicitar presupuesto
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
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

/*
 * Fila de estrellas para representar el rating.
 *Las cuales ahora mismo no tienen funcionalidad
 * pero en un futuro como en la otra pantalla espero que tengan
 */
@Composable
private fun FilaEstrellas(rating: Double) {
    val amarillo = Color(0xFFFFC107)
    val gris = Color(0xFFBDBDBD)

    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            // Si el rating supera el número de estrella, la pintamos “activa”
            val llena = rating >= (i + 1) - 0.01

            Icon(
                // Estás usando el mismo icono para ambos casos; cambia el color para simular vacío/lleno
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = if (llena) amarillo else gris,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
@Preview
@Composable
fun TecnicosPreview() {
    TecnicosScreen(

    )
}
