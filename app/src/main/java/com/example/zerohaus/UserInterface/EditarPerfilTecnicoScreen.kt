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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerohaus.ViewModel.EditarPerfilTecnicoViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditarPerfilTecnicoScreen(
    viewModel: EditarPerfilTecnicoViewModel,
    onVolver: () -> Unit = {}
) {
    val verde = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val estado = viewModel.estado
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.cargar() }
    LaunchedEffect(estado.mensaje, estado.error) {
        estado.mensaje?.let { snackbar.showSnackbar(it); viewModel.limpiarMensaje() }
        estado.error?.let { snackbar.showSnackbar(it); viewModel.limpiarMensaje() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Editar mi perfil", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                }
            )
        }
    ) { pv ->
        if (estado.cargando) {
            Box(Modifier.padding(pv).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .padding(pv)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Información profesional", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Esta información será visible para tus clientes en tu perfil público.",
                color = gris, fontSize = 13.sp
            )

            OutlinedTextField(
                value = estado.nombre,
                onValueChange = viewModel::cambiarNombre,
                label = { Text("Nombre o nombre de empresa") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = estado.ciudad,
                onValueChange = viewModel::cambiarCiudad,
                label = { Text("Ciudad") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = estado.descripcion,
                onValueChange = viewModel::cambiarDescripcion,
                label = { Text("Descripción profesional") },
                placeholder = { Text("Cuenta a tus clientes qué haces, tus años de experiencia, certificaciones…") },
                shape = RoundedCornerShape(12.dp),
                minLines = 4,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = estado.telefono,
                onValueChange = viewModel::cambiarTelefono,
                label = { Text("Teléfono de contacto") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Phone, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = estado.emailContacto,
                onValueChange = viewModel::cambiarEmail,
                label = { Text("Email de contacto") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Email, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Especialidades
            Spacer(Modifier.height(4.dp))
            Text("Especialidades", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("Etiquetas que ayudan a los clientes a encontrarte.", color = gris, fontSize = 12.sp)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                estado.especialidades.forEach { esp ->
                    InputChip(
                        selected = false,
                        onClick = { viewModel.quitarEspecialidad(esp) },
                        label = { Text(esp) },
                        trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = estado.nuevaEspecialidad,
                    onValueChange = viewModel::cambiarNuevaEspecialidad,
                    label = { Text("Añadir especialidad") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { viewModel.anadirEspecialidad() },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = verde)
                ) {
                    Icon(Icons.Default.Add, "Añadir", tint = Color.White)
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { viewModel.guardar() },
                enabled = !estado.guardando,
                colors = ButtonDefaults.buttonColors(containerColor = verde),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                if (estado.guardando) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    if (estado.guardando) "Guardando…" else "Guardar cambios",
                    color = Color.White, fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
