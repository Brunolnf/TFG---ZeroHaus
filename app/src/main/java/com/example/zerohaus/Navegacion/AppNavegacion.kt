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
import com.example.zerohaus.Util.AdminConfig
import com.example.zerohaus.ViewModel.*
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AppNavegacion() {
    val sesionVM: SesionViewModel = viewModel()

    var mostrarSplash by remember { mutableStateOf(true) }

    // Splash
    if (mostrarSplash) {
        SplashScreen { mostrarSplash = false }
        return
    }

    // Comprobar sesión
    LaunchedEffect(Unit) { sesionVM.comprobarSesion() }
    val logueado = sesionVM.logueado.value

    // null = aún comprobando sesión → spinner breve solo al abrir la app
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

    val cerrarSesion: () -> Unit = { sesionVM.logout() }

    val emailActual = if (logueado) FirebaseAuth.getInstance().currentUser?.email else null
    val esAdmin = logueado && AdminConfig.esAdmin(emailActual)
    val start = when {
        esAdmin -> "admin"
        logueado -> "main"
        else -> "login"
    }

    // key(start) destruye y recrea el NavHost entero cuando cambia el estado
    // de sesión. Al cerrar sesión start pasa a "login" → se muestra LoginScreen
    // al instante sin spinners ni pantallas en blanco.
    key(start) {
    val nav = rememberNavController()
    val loginVM: LoginViewModel = viewModel()
    val chatVM: ChatViewModel = viewModel()
    val informeVM: InformeViewModel = viewModel()
    val tecnicosVM: TecnicosViewModel = viewModel()

    NavHost(navController = nav, startDestination = start) {

        // Auth
        composable("login") {
            LoginScreen(
                viewModel = loginVM,
                onLoginExitoso = {
                    // Recarga el usuario tras login para que MainScaffold sepa si es técnico o cliente.
                    sesionVM.comprobarSesion()
                    // Si es el admin, va a su panel; si no, al main normal.
                    val destino = if (AdminConfig.esAdmin(FirebaseAuth.getInstance().currentUser?.email))
                        "admin" else "main"
                    nav.navigate(destino) { popUpTo("login") { inclusive = true } }
                },
                onIrARegistro = { nav.navigate("registro") },
                onIrARecuperar = { nav.navigate("recuperar") }
            )
        }
        composable("registro") {
            val registroVM: RegistroViewModel = viewModel()
            RegistroScreen(
                viewModel = registroVM,
                onRegistroExitoso = {
                    nav.navigate("onboarding") { popUpTo("login") { inclusive = true } }
                },
                onIniciarSesion = { nav.popBackStack() }
            )
        }
        composable("onboarding") {
            OnboardingScreen {
                sesionVM.comprobarSesion()
                nav.navigate("main") { popUpTo("onboarding") { inclusive = true } }
            }
        }
        composable("recuperar") {
            RecuperarPasswordScreen(
                viewModel = loginVM,
                onVolver = { nav.popBackStack() }
            )
        }

        // Panel exclusivo del administrador (único admin: AdminConfig.ADMIN_EMAIL).
        composable("admin") {
            val adminVM: AdminViewModel = viewModel()
            AdminScreen(
                viewModel = adminVM,
                onCerrarSesion = cerrarSesion
            )
        }

        // Main (Bottom Nav) — bifurca según tipoUsuario
        composable("main") {
            val usuario = sesionVM.usuario.value
            val esTecnico = usuario?.tipoUsuario == "Técnico"

            // Sin usuario aún (login fresco cargando, o logout en tránsito).
            // No mostramos spinner — si es logout, key(start) destruirá este
            // composable y mostrará LoginScreen directamente.
            if (usuario == null) return@composable

            if (esTecnico) {
                val panelTecnicoVM: PanelTecnicoViewModel = viewModel()
                val panelVM: PanelViewModel = viewModel()
                val certificadoVM: CertificadoViewModel = viewModel()
                val presupuestosVM: PresupuestosViewModel = viewModel()
                MainScaffoldTecnico(
                    panelTecnicoVM = panelTecnicoVM,
                    panelVM = panelVM,
                    certificadoVM = certificadoVM,
                    presupuestosVM = presupuestosVM,
                    chatVM = chatVM,
                    onCerrarSesion = cerrarSesion,
                    onPerfil       = { nav.navigate("perfil") },
                    onAjustes      = { nav.navigate("ajustes") },
                    onSobreApp     = { nav.navigate("sobre_app") },
                    onChats        = { id -> nav.navigate("chat/$id") },
                    onProyectos    = { nav.navigate("proyectos_tecnico") },
                    onResenas      = { nav.navigate("resenas_tecnico") },
                    onEstadisticas = { nav.navigate("estadisticas_tecnico") },
                    onMisClientes  = { nav.navigate("mis_clientes_tecnico") }
                )
            } else {
                val panelVM: PanelViewModel = viewModel()
                val certificadoVM: CertificadoViewModel = viewModel()
                MainScaffold(
                    panelViewModel = panelVM,
                    certificadoViewModel = certificadoVM,
                    chatViewModel = chatVM,
                    onCerrarSesion = cerrarSesion,
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
        }

        // Perfil usuario
        composable("perfil") {
            val perfilVM: PerfilViewModel = viewModel()
            PerfilScreen(
                viewModel = perfilVM,
                onVolver = { nav.popBackStack() },
                onCerrarSesion = cerrarSesion
            )
        }

        // Preestudio → Informe
        composable("preestudio") {
            val preestudioVM: PreestudioViewModel = viewModel()
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
            val perfilTecnicoVM: PerfilTecnicoViewModel = viewModel()
            PerfilTecnicoScreen(
                viewModel = perfilTecnicoVM,
                tecnicoId = backEntry.arguments?.getString("tecnicoId") ?: "",
                onVolver = { nav.popBackStack() },
                onContactar = { tecnicoUid, tecnicoNombre ->
                    chatVM.iniciarChatConTecnico(tecnicoUid, tecnicoNombre) { chatId ->
                        // chatId vacío = error creando/encontrando el chat;
                        // simplemente no navegamos para que el usuario reintente.
                        if (chatId.isNotBlank()) nav.navigate("chat/$chatId")
                    }
                }
            )
        }

        // Resto de pantallas
        composable("rankings") {
            val rankingsVM: RankingsViewModel = viewModel()
            RankingsScreen(
                viewModel = rankingsVM,
                onVolver = { nav.popBackStack() },
                onVerPerfil = { id -> nav.navigate("perfil_tecnico/$id") }
            )
        }
        composable("proyectos") {
            val proyectosVM: ProyectosViewModel = viewModel()
            MisProyectosScreen(viewModel = proyectosVM, onVolver = { nav.popBackStack() })
        }
        composable("presupuestos") {
            val presupuestosVM: PresupuestosViewModel = viewModel()
            PresupuestosScreen(viewModel = presupuestosVM, onVolver = { nav.popBackStack() })
        }
        composable("historial_informes") {
            val historialVM: HistorialInformesViewModel = viewModel()
            HistorialInformesScreen(
                viewModel = historialVM,
                chatViewModel = chatVM,
                onVolver = { nav.popBackStack() },
                onVerInforme = { inf ->
                    informeVM.cargarInforme(inf)
                    nav.navigate("informe")
                }
            )
        }
        composable("mis_viviendas") {
            val viviendasVM: ViviendasViewModel = viewModel()
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
            val graficasVM: GraficasViewModel = viewModel()
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
            val ajustesVM: AjustesViewModel = viewModel()
            AjustesScreen(viewModel = ajustesVM, onVolver = { nav.popBackStack() })
        }

        // ── Pantallas exclusivas de técnicos ──
        composable("mis_clientes_tecnico") {
            val misClientesVM: MisClientesTecnicoViewModel = viewModel()
            MisClientesTecnicoScreen(
                viewModel = misClientesVM,
                onVolver = { nav.popBackStack() },
                onAbrirChat = { chatId -> nav.navigate("chat/$chatId") }
            )
        }
        composable("proyectos_tecnico") {
            val proyectosTecVM: ProyectosAsignadosViewModel = viewModel()
            ProyectosAsignadosScreen(viewModel = proyectosTecVM, onVolver = { nav.popBackStack() })
        }
        composable("resenas_tecnico") {
            val resenasTecVM: ResenasRecibidasViewModel = viewModel()
            ResenasRecibidasScreen(viewModel = resenasTecVM, onVolver = { nav.popBackStack() })
        }
        composable("estadisticas_tecnico") {
            val estadisticasTecVM: EstadisticasTecnicoViewModel = viewModel()
            EstadisticasTecnicoScreen(viewModel = estadisticasTecVM, onVolver = { nav.popBackStack() })
        }
    }
    } // key(start)
}
