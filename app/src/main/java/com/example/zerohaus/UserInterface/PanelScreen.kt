package com.example.zerohaus.UserInterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelScreen(
    onNotificaciones: () -> Unit = {},
    onAjustes: () -> Unit = {},
    onCerrarSesion: () -> Unit = {},
    onVerUltimoInforme: () -> Unit = {},
    onNuevoPreestudio: () -> Unit = {},
    onBuscarTecnicos: () -> Unit = {},
    onMisProyectos: () -> Unit = {},
    onRankings: () -> Unit = {},
) {
    val verde = Color(0xFF16A34A)
    val grisTexto = Color(0xFF6B7280)
    val bordeSuave = Color(0xFFE5E7EB)
    val fondo = Color(0xFFF6F7F9)

    Scaffold(
        containerColor = fondo,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White),
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {

                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(verde),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("ZeroHaus", color = verde, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        }
                        Text("Usuario Demo", color = grisTexto, fontSize = 12.sp)
                    }
                },
                actions = {

                    Box {
                        IconButton(onClick = onNotificaciones) {
                            Icon(Icons.Default.Place, contentDescription = "Notificaciones", tint = Color(0xFF111827))
                        }
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color(0xFFEF4444))
                                .align(Alignment.TopEnd)
                                .offset(x = (-10).dp, y = 10.dp)
                        )
                    }
                    IconButton(onClick = onAjustes) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = Color(0xFF111827))
                    }
                    IconButton(onClick = onCerrarSesion) {
                        Icon(Icons.Default.Done, contentDescription = "Salir", tint = Color(0xFF111827))
                    }
                    Spacer(Modifier.width(4.dp))
                }
            )
        }
    ) { pv ->
        Column(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
        ) {

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Bienvenido, Usuario", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF111827))
                    Text("Gestiona la eficiencia energética de tu hogar", color = grisTexto, fontSize = 12.sp)
                }


                Card(
                    colors = CardDefaults.cardColors(containerColor = verde),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Tu vivienda", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                        Spacer(Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("Mi vivienda principal", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)


                            Box(
                                modifier = Modifier
                                    .size(width = 46.dp, height = 42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("D", fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Consumo estimado", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                                Text("145 kWh/año", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Emisiones", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                                Text("32 kg CO₂/año", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }

                        Spacer(Modifier.height(14.dp))


                        Button(
                            onClick = onVerUltimoInforme,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.18f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ver último informe  →", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }


                Text("Acciones rápidas", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF111827))

                     AccionRapidaCard(
                    icono = Icons.Default.Lock,
                    colorFondoIcono = Color(0xFFD1FAE5),
                    colorIcono = Color(0xFF059669),
                    titulo = "Nuevo preestudio",
                    subtitulo = "Analiza tu vivienda",
                    borde = bordeSuave,
                    onClick = onNuevoPreestudio
                )

                AccionRapidaCard(
                    icono = Icons.Default.ThumbUp,
                    colorFondoIcono = Color(0xFFDBEAFE),
                    colorIcono = Color(0xFF2563EB),
                    titulo = "Buscar técnicos",
                    subtitulo = "Encuentra profesionales",
                    borde = bordeSuave,
                    onClick = onBuscarTecnicos
                )


                AccionRapidaCard(
                    icono = Icons.Default.Star,
                    colorFondoIcono = Color(0xFFEDE9FE),
                    colorIcono = Color(0xFF7C3AED),
                    titulo = "Mis proyectos",
                    subtitulo = "Gestiona reformas",
                    borde = bordeSuave,
                    onClick = onMisProyectos
                )

                AccionRapidaCard(
                    icono = Icons.Default.AddCircle,
                    colorFondoIcono = Color(0xFFFFEDD5),
                    colorIcono = Color(0xFFEA580C),
                    titulo = "Rankings",
                    subtitulo = "Mejores técnicos",
                    borde = bordeSuave,
                    onClick = onRankings
                )


                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ThumbUp, contentDescription = null, tint = Color(0xFF2563EB))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Consejo de eficiencia energética",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Color(0xFF1E3A8A)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Mejorar el aislamiento de ventanas puede reducir el\n" +
                                    "consumo energético hasta un 25%. Consulta con un\n" +
                                    "técnico certificado para evaluar tu vivienda.",
                            color = Color(0xFF1D4ED8),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun AccionRapidaCard(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    colorFondoIcono: Color,
    colorIcono: Color,
    titulo: String,
    subtitulo: String,
    borde: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, borde)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colorFondoIcono),
                contentAlignment = Alignment.Center
            ) {
                Icon(icono, contentDescription = null, tint = colorIcono)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF111827))
                Spacer(Modifier.height(2.dp))
                Text(subtitulo, color = Color(0xFF6B7280), fontSize = 12.sp)
            }
        }
    }
}
