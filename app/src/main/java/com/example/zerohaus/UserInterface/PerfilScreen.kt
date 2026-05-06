package com.example.zerohaus.UserInterface

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.zerohaus.ViewModel.PerfilViewModel
import com.example.zerohaus.util.LocalCadenas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilScreen(
    viewModel: PerfilViewModel,
    onVolver: () -> Unit = {},
    onCerrarSesion: () -> Unit = {}
) {
    val c = LocalCadenas.current
    val verde = Color(0xFF16A34A)
    val gris = Color(0xFF6B7280)
    val fondo = Color(0xFFF6F7F9)
    val borde = Color(0xFFE5E7EB)
    val estado = viewModel.estado

    val fotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.subirFotoPerfil(it) }
    }

    LaunchedEffect(Unit) { viewModel.cargarPerfil() }

    Scaffold(
        containerColor = fondo,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(c.perfilTitulo, fontWeight = FontWeight.SemiBold)
                        Text(c.perfilSubtitulo, color = gris, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, c.volver)
                    }
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
                modifier = Modifier
                    .padding(pv)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE5E7EB))
                        .clickable { fotoPicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (estado.fotoPerfil.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(estado.fotoPerfil).crossfade(true).build(),
                            contentDescription = "Foto de perfil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(50.dp), tint = Color(0xFF9CA3AF))
                    }
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).size(34.dp).clip(CircleShape).background(verde),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                if (estado.subiendoFoto) {
                    LinearProgressIndicator(modifier = Modifier.width(110.dp), color = verde)
                }
                Text(c.perfilFotoTexto, color = gris, fontSize = 12.sp)

                // Datos personales
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(c.perfilDatosPersonales, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                        Text(c.registroNombre, fontSize = 12.sp, color = Color(0xFF111827))
                        OutlinedTextField(
                            value = estado.nombre,
                            onValueChange = { viewModel.cambiarNombre(it) },
                            placeholder = { Text(c.perfilNombrePlaceholder) },
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = gris) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, focusedBorderColor = verde, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(c.perfilEmailLabel, fontSize = 12.sp, color = Color(0xFF111827))
                        OutlinedTextField(
                            value = estado.email, onValueChange = {}, readOnly = true,
                            leadingIcon = { Icon(Icons.Default.MailOutline, null, tint = gris) },
                            singleLine = true, shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, unfocusedContainerColor = Color(0xFFF9FAFB), focusedContainerColor = Color(0xFFF9FAFB)),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(c.perfilTipoUsuarioLabel, fontSize = 12.sp, color = Color(0xFF111827))
                        val tipoDisplay = when (estado.tipoUsuario) {
                            "Técnico" -> c.tipoTecnico
                            else -> c.tipoPropietario
                        }
                        OutlinedTextField(
                            value = tipoDisplay, onValueChange = {}, readOnly = true,
                            leadingIcon = { Icon(if (estado.tipoUsuario == "Técnico") Icons.Default.Build else Icons.Default.Home, null, tint = gris) },
                            singleLine = true, shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, unfocusedContainerColor = Color(0xFFF9FAFB), focusedContainerColor = Color(0xFFF9FAFB)),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Perfil técnico
                if (estado.tipoUsuario == "Técnico") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(c.perfilTecnicoTitulo, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                            Text(c.perfilEspecialidades, fontSize = 12.sp, color = Color(0xFF111827))
                            OutlinedTextField(
                                value = estado.especialidades,
                                onValueChange = { viewModel.cambiarEspecialidades(it) },
                                placeholder = { Text(c.perfilEspecialidadesPlaceholder) },
                                singleLine = true, shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, focusedBorderColor = verde, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(c.perfilDescripcionProf, fontSize = 12.sp, color = Color(0xFF111827))
                            OutlinedTextField(
                                value = estado.descripcion,
                                onValueChange = { viewModel.cambiarDescripcion(it) },
                                placeholder = { Text(c.perfilDescripcionPlaceholder) },
                                minLines = 3, shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, focusedBorderColor = verde, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Teléfono", fontSize = 12.sp, color = Color(0xFF111827))
                            OutlinedTextField(
                                value = estado.telefono,
                                onValueChange = { viewModel.cambiarTelefono(it) },
                                placeholder = { Text(c.perfilTelefonoPlaceholder) },
                                leadingIcon = { Icon(Icons.Default.Phone, null, tint = gris) },
                                singleLine = true, shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, focusedBorderColor = verde, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(c.perfilEmailContacto, fontSize = 12.sp, color = Color(0xFF111827))
                            OutlinedTextField(
                                value = estado.emailContacto,
                                onValueChange = { viewModel.cambiarEmailContacto(it) },
                                placeholder = { Text(c.perfilEmailContactoPlaceholder) },
                                leadingIcon = { Icon(Icons.Default.MailOutline, null, tint = gris) },
                                singleLine = true, shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = borde, focusedBorderColor = verde, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Guardar
                Button(
                    onClick = { viewModel.guardarPerfil() },
                    enabled = !estado.guardando && estado.nombre.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = verde),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    if (estado.guardando) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(c.perfilGuardar, color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                if (estado.exito) {
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5)), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = verde)
                            Spacer(Modifier.width(8.dp))
                            Text(c.perfilGuardado, color = Color(0xFF065F46))
                        }
                    }
                }

                estado.error?.let {
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626))
                            Spacer(Modifier.width(8.dp))
                            Text(it, color = Color(0xFF991B1B))
                        }
                    }
                }

                // Cuenta
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(c.perfilCuenta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        OutlinedButton(
                            onClick = onCerrarSesion,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFDC2626)))
                        ) {
                            Icon(Icons.Default.ExitToApp, null, tint = Color(0xFFDC2626))
                            Spacer(Modifier.width(8.dp))
                            Text(c.cerrarSesion, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
