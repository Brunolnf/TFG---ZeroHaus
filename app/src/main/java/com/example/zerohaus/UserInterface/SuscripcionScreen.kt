package com.example.zerohaus.UserInterface

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.Modelos.Planes
import com.example.zerohaus.Modelos.esDestacado
import com.example.zerohaus.Modelos.esVerificado
import com.example.zerohaus.Util.BillingManager
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.ViewModel.SuscripcionViewModel

/**
 * Planes de suscripción del profesional (Verificado / Destacado) con los
 * precios reales de Google Play, compra, restauración y enlace para
 * gestionar o cancelar la suscripción (obligatorio en Play).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuscripcionScreen(
    viewModel: SuscripcionViewModel,
    onVolver: () -> Unit = {}
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val dorado = Color(0xFFF59E0B)
    val estado = viewModel.estado
    val context = LocalContext.current
    val activity = context as? Activity
    val c = LocalCadenas.current

    LaunchedEffect(Unit) {
        viewModel.cargar()
        activity?.let { viewModel.iniciarBilling(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            ZeroToast(
                mensaje = estado.exitoMensaje ?: estado.error,
                tipo = if (estado.error != null) ToastTipo.ERROR else ToastTipo.EXITO,
                alOcultar = { viewModel.limpiarMensajes() }
            )
        },
        topBar = {
            TopAppBar(
                title = { Text(c.subTitulo, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, c.volver) }
                }
            )
        }
    ) { pv ->
        if (estado.cargando) {
            Box(Modifier.padding(pv).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = verde)
            }
            return@Scaffold
        }

        Column(
            Modifier
                .padding(pv)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val tec = estado.tecnico

            // Estado actual
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        tec?.esDestacado == true -> dorado.copy(0.12f)
                        tec?.esVerificado == true -> verde.copy(0.12f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
                    }
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        when {
                            tec?.esDestacado == true -> Icons.Default.Star
                            tec?.esVerificado == true -> Icons.Default.VerifiedUser
                            else -> Icons.Default.Person
                        },
                        null,
                        tint = when {
                            tec?.esDestacado == true -> dorado
                            tec?.esVerificado == true -> verde
                            else -> gris
                        },
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            tec?.esDestacado == true -> c.subPlanDestacado
                            tec?.esVerificado == true -> c.subPlanVerificado
                            else -> c.subSinPlan
                        },
                        fontWeight = FontWeight.Bold, fontSize = 20.sp
                    )
                    Text(
                        when {
                            tec?.esDestacado == true -> c.subDestacadoEstadoDesc
                            tec?.esVerificado == true -> c.subVerificadoEstadoDesc
                            else -> c.subSinPlanDesc
                        },
                        color = gris, fontSize = 13.sp, textAlign = TextAlign.Center
                    )
                }
            }

            if (estado.activando) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = verde)
            }

            Text(c.subEligePlan, fontWeight = FontWeight.Bold, fontSize = 18.sp)

            // Con un plan vigente, los demás se ofrecen como CAMBIO de plan
            // (Google Play sustituye la suscripción en vez de cobrar dos)
            val planVigente = estado.vigente?.planId
            val hayPlan = planVigente != null
            val puedeComprar = estado.suscripcionesCargadas && !estado.activando
            if (hayPlan) {
                Text(c.subCambioPlanInfo, color = gris, fontSize = 12.sp)
            }

            // Verificado Trimestral
            PlanCard(
                titulo = c.subVerificadoTrimestral,
                precio = estado.precios[Planes.VERIFICADO_TRIMESTRAL],
                cargandoPrecio = !estado.billingListo,
                detalle = c.subVerificadoTrimestralDet,
                color = verde,
                icono = Icons.Default.VerifiedUser,
                activo = planVigente == Planes.VERIFICADO_TRIMESTRAL,
                cambioDePlan = hayPlan,
                habilitado = puedeComprar,
                onSuscribir = { activity?.let { viewModel.suscribirse(it, Planes.VERIFICADO_TRIMESTRAL) } }
            )

            // Verificado Anual — mejor valor
            PlanCard(
                titulo = c.subVerificadoAnual,
                precio = estado.precios[Planes.VERIFICADO_ANUAL],
                cargandoPrecio = !estado.billingListo,
                detalle = c.subVerificadoAnualDet,
                color = verde,
                icono = Icons.Default.VerifiedUser,
                activo = planVigente == Planes.VERIFICADO_ANUAL,
                cambioDePlan = hayPlan,
                habilitado = puedeComprar,
                destacado = true,
                onSuscribir = { activity?.let { viewModel.suscribirse(it, Planes.VERIFICADO_ANUAL) } }
            )

            // Destacado
            PlanCard(
                titulo = c.subDestacado,
                precio = estado.precios[Planes.DESTACADO_MENSUAL],
                cargandoPrecio = !estado.billingListo,
                detalle = c.subDestacadoDet,
                color = dorado,
                icono = Icons.Default.Star,
                activo = planVigente == Planes.DESTACADO_MENSUAL,
                cambioDePlan = hayPlan,
                habilitado = puedeComprar,
                onSuscribir = { activity?.let { viewModel.suscribirse(it, Planes.DESTACADO_MENSUAL) } }
            )

            // Destacado + Anuncios: fuera de venta. Promete una campaña de Google
            // Ads gestionada que hoy no gestiona nada; solo se muestra a quien ya
            // lo tenga contratado (para ver su plan y poder cambiarlo).
            if (planVigente == Planes.DESTACADO_ANUNCIOS_MENSUAL) {
                PlanCard(
                    titulo = c.subDestacadoAnuncios,
                    precio = estado.precios[Planes.DESTACADO_ANUNCIOS_MENSUAL],
                    cargandoPrecio = !estado.billingListo,
                    detalle = c.subDestacadoAnunciosDet,
                    color = Color(0xFF7C3AED),
                    icono = Icons.Default.Campaign,
                    activo = true,
                    cambioDePlan = hayPlan,
                    habilitado = puedeComprar,
                    onSuscribir = {}
                )
            }

            // Beneficios
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.subVentajasTitulo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    BeneficioItem(c.subVentaja1)
                    BeneficioItem(c.subVentaja2)
                    BeneficioItem(c.subVentaja3)
                    BeneficioItem(c.subVentaja4)
                }
            }

            // Gestionar / Cancelar suscripción (obligatorio en Google Play).
            // Abre la ficha de suscripciones de la cuenta, donde se cancela.
            if (tec?.esVerificado == true) {
                OutlinedButton(
                    onClick = {
                        val url = BillingManager.urlGestionSuscripcion(
                            context.packageName, viewModel.planIdActivo()
                        )
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(c.subGestionar)
                }
            }

            // Restaurar compras (reinstalación / cambio de dispositivo).
            TextButton(
                onClick = { viewModel.restaurarCompras() },
                enabled = !estado.activando,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Restore, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(c.subRestaurar)
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PlanCard(
    titulo: String,
    precio: String?,          // null = Google Play no lo ofrece (aún no publicado)
    cargandoPrecio: Boolean,
    detalle: String,
    color: Color,
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    activo: Boolean,
    cambioDePlan: Boolean,
    habilitado: Boolean,
    destacado: Boolean = false,
    onSuscribir: () -> Unit
) {
    val c = LocalCadenas.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (destacado) 2.dp else 1.dp, if (destacado) color else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icono, null, tint = color, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(titulo, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (destacado) {
                            Surface(shape = RoundedCornerShape(4.dp), color = color.copy(0.15f)) {
                                Text(c.subMejorValor, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    when {
                        precio != null -> Text(precio, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        cargandoPrecio -> LinearProgressIndicator(Modifier.width(60.dp).padding(vertical = 8.dp), color = color)
                        else -> Text(c.subPlanNoDisponible, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(detalle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            if (activo) {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, color.copy(0.5f))
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = color, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(c.subPlanActual, color = color, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onSuscribir,
                    enabled = precio != null && habilitado,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = color)
                ) {
                    Text(if (cambioDePlan) c.subCambiarAPlan else c.subSuscribirme, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BeneficioItem(texto: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(texto, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
