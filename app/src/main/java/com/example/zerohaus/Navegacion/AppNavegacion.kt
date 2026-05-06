// RUTA: Navegacion/AppNavegacion.kt
package com.example.zerohaus.Navegacion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.zerohaus.ServicioNotificaciones
import com.example.zerohaus.UserInterface.*
import com.example.zerohaus.ViewModel.*

@Composable
fun AppNavegacion() {
    val nav = rememberNavController()

    // ViewModels
    val sesionVM: SesionViewModel = viewModel()
    val loginVM: LoginViewModel = viewModel()
    val registroVM: RegistroViewModel = viewModel()
    val panelVM: PanelViewModel = viewModel()
    val preestudioVM: PreestudioViewModel = viewModel()
    val informeVM: InformeViewModel = viewModel()
    val tecnicosVM: TecnicosViewModel = viewModel()
    val rankingsVM: RankingsViewModel = viewModel()
    val proyectosVM: ProyectosViewModel = viewModel()
    val certificadoVM: CertificadoViewModel = viewModel()
    val perfilVM: PerfilViewModel = viewModel()
    val perfilTecnicoVM: PerfilTecnicoViewModel = viewModel()
    val presupuestosVM: PresupuestosViewModel = viewModel()
    val historialVM: HistorialInformesViewModel = viewModel()
    val chatVM: ChatViewModel = viewModel()
    val viviendasVM: ViviendasViewModel = viewModel()
    val graficasVM: GraficasViewModel = viewModel()
    val ajustesVM: AjustesViewModel = viewModel()

    // Estados de flujo inicial
    var mostrarSplash by remember { mutableStateOf(true) }
    var mostrarOnboarding by remember { mutableStateOf(false) }

    // Splash
    if (mostrarSplash) {
        SplashScreen { mostrarSplash = false }
        return
    }

    // Comprobar sesión
    LaunchedEffect(Unit) { sesionVM.comprobarSesion() }
    val logueado = sesionVM.logueado.value
    if (logueado == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Registrar token de notificaciones si hay sesión activa
    LaunchedEffect(logueado) {
        if (logueado) ServicioNotificaciones.registrarToken()
    }

    // Onboarding (solo usuarios nuevos)
    if (mostrarOnboarding) {
        OnboardingScreen { mostrarOnboarding = false }
        return
    }

    val start = if (logueado) "main" else "login"

    NavHost(navController = nav, startDestination = start) {

        // Auth
        composable("login") {
            LoginScreen(
                viewModel = loginVM,
                onLoginExitoso = { nav.navigate("main") { popUpTo("login") { inclusive = true } } },
                onIrARegistro = { nav.navigate("registro") },
                onIrARecuperar = { nav.navigate("recuperar") }
            )
        }
        composable("registro") {
            RegistroScreen(
                viewModel = registroVM,
                onRegistroExitoso = {
                    mostrarOnboarding = true
                    nav.navigate("main") { popUpTo("login") { inclusive = true } }
                },
                onIniciarSesion = { nav.popBackStack() }
            )
        }
        composable("recuperar") {
            RecuperarPasswordScreen(
                viewModel = loginVM,
                onVolver = { nav.popBackStack() }
            )
        }

        // Main (Bottom Nav)
        composable("main") {
            MainScaffold(
                panelViewModel = panelVM,
                certificadoViewModel = certificadoVM,
                chatViewModel = chatVM,
                onCerrarSesion = {
                    sesionVM.logout()
                    nav.navigate("login") { popUpTo(0) { inclusive = true } }
                },
                onNuevoPreestudio      = { nav.navigate("preestudio") },
                onBuscarTecnicos       = { nav.navigate("tecnicos") },
                onMisProyectos         = { nav.navigate("proyectos") },
                onRankings             = { nav.navigate("rankings") },
                onVerUltimoInforme     = { nav.navigate("informe") },
                onPerfil               = { nav.navigate("perfil") },
                onPresupuestos         = { nav.navigate("presupuestos") },
                onHistorialInformes    = { nav.navigate("historial_informes") },
                onMisViviendas         = { nav.navigate("mis_viviendas") },
                onChats                = { id -> nav.navigate("chat/$id") },
                onGraficas             = { nav.navigate("graficas") },
                onMapaTecnicos         = { nav.navigate("mapa_tecnicos") },
                onSobreApp             = { nav.navigate("sobre_app") },
                onAjustes              = { nav.navigate("ajustes") }
            )
        }

        // Perfil usuario
        composable("perfil") {
            PerfilScreen(
                viewModel = perfilVM,
                onVolver = { nav.popBackStack() },
                onCerrarSesion = {
                    sesionVM.logout()
                    nav.navigate("login") { popUpTo(0) { inclusive = true } }
                }
            )
        }

        // Preestudio → Informe
        composable("preestudio") {
            PreestudioScreen(
                viewModel = preestudioVM,
                onVolver = { nav.popBackStack() },
                onInformeGenerado = {
                    preestudioVM.estado.informeGenerado?.let { informeVM.cargarInforme(it) }
                    preestudioVM.limpiarInforme()
                    nav.navigate("informe") { popUpTo("preestudio") { inclusive = true } }
                }
            )
        }

        // Informe
        composable("informe") {
            InformeScreen(
                viewModel = informeVM,
                onVolver = { nav.popBackStack() },
                onContactarTecnicos = { nav.navigate("tecnicos") }
            )
        }

        // Técnicos
        composable("tecnicos") {
            TecnicosScreen(
                viewModel = tecnicosVM,
                onVolver = { nav.popBackStack() },
                onVerPerfil = { id -> nav.navigate("perfil_tecnico/$id") }
            )
        }
        composable("perfil_tecnico/{tecnicoId}") { backEntry ->
            PerfilTecnicoScreen(
                viewModel = perfilTecnicoVM,
                tecnicoId = backEntry.arguments?.getString("tecnicoId") ?: "",
                onVolver = { nav.popBackStack() },
                onContactar = { tecnicoUid, tecnicoNombre ->
                    chatVM.iniciarChatConTecnico(tecnicoUid, tecnicoNombre) { chatId ->
                        nav.navigate("chat/$chatId")
                    }
                }
            )
        }

        // Resto de pantallas
        composable("rankings") {
            RankingsScreen(
                viewModel = rankingsVM,
                onVolver = { nav.popBackStack() },
                onVerPerfil = { id -> nav.navigate("perfil_tecnico/$id") }
            )
        }
        composable("proyectos") {
            MisProyectosScreen(viewModel = proyectosVM, onVolver = { nav.popBackStack() })
        }
        composable("presupuestos") {
            PresupuestosScreen(viewModel = presupuestosVM, onVolver = { nav.popBackStack() })
        }
        composable("historial_informes") {
            HistorialInformesScreen(
                viewModel = historialVM,
                onVolver = { nav.popBackStack() },
                onVerInforme = { inf ->
                    informeVM.cargarInforme(inf)
                    nav.navigate("informe")
                }
            )
        }
        composable("mis_viviendas") {
            MisViviendasScreen(
                viewModel = viviendasVM,
                onVolver = { nav.popBackStack() },
                onNuevoPreestudio = { nav.navigate("preestudio") }
            )
        }
        composable("chat/{chatId}") { backEntry ->
            ChatScreen(
                viewModel = chatVM,
                chatId = backEntry.arguments?.getString("chatId") ?: "",
                onVolver = { nav.popBackStack() },
                onVerPerfil = { tecnicoDocId -> nav.navigate("perfil_tecnico/$tecnicoDocId") }
            )
        }
        composable("graficas") {
            GraficasConsumoScreen(viewModel = graficasVM, onVolver = { nav.popBackStack() })
        }
        composable("mapa_tecnicos") {
            MapaTecnicosScreen(
                viewModel = tecnicosVM,
                onVolver = { nav.popBackStack() },
                onVerPerfil = { id -> nav.navigate("perfil_tecnico/$id") }
            )
        }
        composable("sobre_app") {
            SobreAppScreen(onVolver = { nav.popBackStack() })
        }
        composable("ajustes") {
            AjustesScreen(viewModel = ajustesVM, onVolver = { nav.popBackStack() })
        }
    }
}
