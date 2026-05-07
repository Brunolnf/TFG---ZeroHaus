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
import com.example.zerohaus.BuildConfig
import com.example.zerohaus.util.LocalCadenas
import com.example.zerohaus.util.SembradorDatos

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SobreAppScreen(onVolver: () -> Unit = {}) {
    val c = LocalCadenas.current
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val context = LocalContext.current

    var reseteando by remember { mutableStateOf(false) }
    var credenciales by remember { mutableStateOf<List<SembradorDatos.CredencialTecnico>>(emptyList()) }
    var borrandoSinValoracion by remember { mutableStateOf(false) }
    var resultadoBorrado by remember { mutableStateOf<SembradorDatos.ResultadoBorrado?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(c.sobreTitulo, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, c.volver) } }
            )
        }
    ) { pv ->
        Column(
            Modifier
                .padding(pv)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            ZeroHausLogo(size = 64.dp)
            Text("ZeroHaus", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = verde)
            Text(c.sobreVersion, color = gris, fontSize = 13.sp)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(c.sobreQueEs, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(c.sobreDescripcion, color = gris, fontSize = 14.sp, lineHeight = 22.sp)
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(c.sobreFuncionalidades, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    listOf(
                        c.sobreFuncPreestudios,
                        c.sobreFuncViviendas,
                        c.sobreFuncBusqueda,
                        c.sobreFuncChat,
                        c.sobreFuncPresupuestos,
                        c.sobreFuncGraficas,
                        c.sobreFuncValoraciones
                    ).forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = verde, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(t, color = Color(0xFF374151), fontSize = 14.sp)
                        }
                    }
                }
            }

            Text(c.sobreCopyright, color = gris, fontSize = 12.sp)

            if (BuildConfig.DEBUG) {
            // ── Reset completo de técnicos demo ──
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Resetear técnicos demo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF92400E))
                    }
                    Text(
                        "Borra todos los técnicos demo antiguos (incluye duplicados y versiones legacy con emails de empresa) y crea desde cero los 7 mejor valorados con cuentas Firebase Auth funcionales. Cada email se corresponde con el nombre del técnico.",
                        fontSize = 12.sp, color = Color(0xFF78350F)
                    )
                    Button(
                        onClick = {
                            reseteando = true
                            credenciales = emptyList()
                            SembradorDatos.resetearTecnicosCompleto(context) { creds ->
                                reseteando = false
                                credenciales = creds
                            }
                        },
                        enabled = !reseteando,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (reseteando) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (reseteando) "Reseteando…" else "Resetear y crear cuentas de los 7 técnicos",
                            fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── Borrar técnicos sin valoración ──
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Limpiar técnicos sin valoración", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF991B1B))
                    }
                    Text(
                        "Borra todos los técnicos cuyas opiniones estén a 0. Útil para eliminar técnicos huérfanos o registrados a mano que aún no tienen reseñas.",
                        fontSize = 12.sp, color = Color(0xFF7F1D1D)
                    )
                    resultadoBorrado?.let { r ->
                        if (r.total == 0) {
                            Text("No había técnicos sin valoración.", fontSize = 12.sp, color = Color(0xFF065F46))
                        } else {
                            Text(
                                "Borrados ${r.borrados}/${r.total}.",
                                fontSize = 12.sp, color = Color(0xFF065F46), fontWeight = FontWeight.Medium
                            )
                            if (r.fallidos.isNotEmpty()) {
                                Text(
                                    "Sin permisos para borrar (hazlo desde Firebase Console): ${r.fallidos.joinToString(", ")}",
                                    fontSize = 11.sp, color = Color(0xFFDC2626)
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            borrandoSinValoracion = true
                            resultadoBorrado = null
                            SembradorDatos.borrarTecnicosSinValoracion { res ->
                                borrandoSinValoracion = false
                                resultadoBorrado = res
                            }
                        },
                        enabled = !borrandoSinValoracion && !reseteando,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (borrandoSinValoracion) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (borrandoSinValoracion) "Borrando…" else "Borrar técnicos sin valoración",
                            fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Lista de credenciales generadas
            if (credenciales.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Credenciales (${credenciales.count { it.ok }}/${credenciales.size} OK)",
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                        )
                        Text("Contraseña común: ${SembradorDatos.PASSWORD_TECNICOS}", fontSize = 12.sp, color = gris)
                        HorizontalDivider()
                        credenciales.forEach { cred ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (cred.ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    null,
                                    tint = if (cred.ok) verde else Color(0xFFDC2626),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(cred.nombre, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(cred.email, fontSize = 11.sp, color = gris)
                                    if (!cred.ok && cred.motivo.isNotBlank()) {
                                        Text(cred.motivo, fontSize = 10.sp, color = Color(0xFFDC2626))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            } // end BuildConfig.DEBUG

            Spacer(Modifier.height(20.dp))
        }
    }
}
