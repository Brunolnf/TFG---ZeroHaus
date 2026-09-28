// RUTA: Navegacion/AppNavegacion.kt
package com.example.zerohaus.Navegacion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.zerohaus.ServicioNotificaciones
import com.example.zerohaus.UserInterface.*
import com.example.zerohaus.Util.AdminConfig
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.ViewModel.*
import com.google.firebase.auth.FirebaseAuth

private const val RUTA_VERIFICAR = "verificar_email"
// Patrón completo (con el argumento opcional): es el que identifica el destino,
// así que es el que debe usarse como startDestination.
private const val RUTA_VERIFICAR_PATRON = "$RUTA_VERIFICAR?siguiente={siguiente}"

/**
 * Grafo de navegación de toda la app. Decide la pantalla inicial (login,
 * verificación del email, panel de admin o app principal) y reacciona al
 * cierre de sesión. Los perfiles cliente y profesional comparten el grafo y
 * bifurcan en la ruta `main` según el tipo de usuario.
 */
@Composable
fun AppNavegacion() {
    val sesionVM: SesionViewModel = viewModel()

    var mostrarSplash by rememberSaveable { mutableStateOf(true) }

    if (mostrarSplash) {
        SplashScreen { mostrarSplash = false }
        return
    }

    // logueado es Boolean (nunca null): se inicializa sincrónico en SesionViewModel.init
    val logueado = sesionVM.logueado.value

    LaunchedEffect(logueado) {
        if (logueado) ServicioNotificaciones.registrarToken()
    }

    val esAdmin = logueado && AdminConfig.esAdmin()

    val nav = rememberNavController()
    val loginVM: LoginViewModel = viewModel()
    val chatVM: ChatViewModel = viewModel()
    val informeVM: InformeViewModel = viewModel()
    val tecnicosVM: TecnicosViewModel = viewModel()

    // startDestination se fija una sola vez cuando el NavHost se crea.
    // Los cambios posteriores de auth los gestiona el LaunchedEffect de abajo.
    val startDestination = remember {
        val emailVerificado = FirebaseAuth.getInstance().currentUser?.isEmailVerified == true
        when {
            // Sesión abierta pero sin verificar el email: no se entra hasta
            // introducir el código (también si era una sesión antigua).
            logueado && !emailVerificado -> RUTA_VERIFICAR_PATRON
            esAdmin  -> "admin"
            logueado -> "main"
            else     -> "login"
        }
    }

    // Destino tras un login/verificación: panel de admin o app normal según
    // el claim del token (pedido fresco, ya incluye email_verified).
    val entrarEnLaApp: () -> Unit = {
        sesionVM.postLogin()
        FirebaseAuth.getInstance().currentUser
            ?.getIdToken(true)
            ?.addOnSuccessListener { result ->
                val destino = if (result.claims["admin"] == true) "admin" else "main"
                nav.navigate(destino) { popUpTo(nav.graph.id) { inclusive = true } }
            }
            ?.addOnFailureListener {
                nav.navigate("main") { popUpTo(nav.graph.id) { inclusive = true } }
            }
    }

    // Al cerrar sesión navega explícitamente a login vaciando el back stack.
    // Más fiable que key(start): no destruye el NavHost ni sufre problemas
    // de subcomposiciones del NavHost interno.
    //
    // IMPORTANTE: se vacía con popUpTo(nav.graph.id), NO con popUpTo(0): el id 0
    // no corresponde a ningún destino real, así que no popea nada y deja viva la
    // pantalla "main" anterior. Al cerrar sesión esa "main" huérfana (con
    // usuario == null) se queda girando el spinner verde para siempre.
    LaunchedEffect(logueado) {
        if (!logueado) {
            loginVM.resetear()   // limpia el loginCorrecto viejo ANTES de mostrar el login
            nav.navigate("login") {
                popUpTo(nav.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val cerrarSesion: () -> Unit = { sesionVM.logout() }

    NavHost(navController = nav, startDestination = startDestination) {

        // Auth
        composable("login") {
            LoginScreen(
                viewModel = loginVM,
                onLoginExitoso = {
                    // GUARDA anti-rebote: el LoginViewModel es app-scoped y conserva
                    // loginCorrecto=true tras un login. Al volver al login después de un
                    // logout, ese flag stale vuelve a disparar este callback. Si no hay
                    // sesión REAL, ignorarlo evita rebotar a "main" sin usuario (causa
                    // del spinner verde infinito).
                    val user = FirebaseAuth.getInstance().currentUser
                    when {
                        user == null -> Unit
                        // Sin email verificado no se entra: primero el código
                        !user.isEmailVerified ->
                            nav.navigate(RUTA_VERIFICAR) { popUpTo("login") { inclusive = true } }
                        else -> entrarEnLaApp()
                    }
                },
                onIrARegistro  = { nav.navigate("registro") },
                onIrARecuperar = { nav.navigate("recuperar") }
            )
        }
        composable("registro") {
            val registroVM: RegistroViewModel = viewModel()
            RegistroScreen(
                viewModel = registroVM,
                onRegistroExitoso = {
                    // Cuenta recién creada: verificar el email y después el onboarding
                    nav.navigate("$RUTA_VERIFICAR?siguiente=onboarding") { popUpTo("login") { inclusive = true } }
                },
                onIniciarSesion = { nav.popBackStack() }
            )
        }
        composable(
            RUTA_VERIFICAR_PATRON,
            arguments = listOf(navArgument("siguiente") { nullable = true; defaultValue = null })
        ) { entry ->
            val siguiente = entry.arguments?.getString("siguiente")
            val verificarVM: VerificarEmailViewModel = viewModel()
            VerificarEmailScreen(
                viewModel = verificarVM,
                onVerificado = {
                    if (siguiente == "onboarding") {
                        nav.navigate("onboarding") { popUpTo(nav.graph.id) { inclusive = true } }
                    } else {
                        entrarEnLaApp()
                    }
                },
                onUsarOtraCuenta = cerrarSesion
            )
        }
        composable("onboarding") {
            OnboardingScreen {
                sesionVM.postLogin()
                nav.navigate("main") { popUpTo("onboarding") { inclusive = true } }
            }
        }
        composable("recuperar") {
            RecuperarPasswordScreen(
                viewModel = loginVM,
                onVolver = { nav.popBackStack() }
            )
        }

        // Panel exclusivo del administrador (custom claim `admin` del ID token).
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

            // Logout en curso: LaunchedEffect ya está navegando a login.
            if (!sesionVM.logueado.value) return@composable

            // Tipo: primero del usuario cargado, si no del caché local
            val tipoUsuario = usuario?.tipoUsuario
                ?: AppEstado.tipoUsuarioCache.ifBlank { null }

            if (tipoUsuario == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (sesionVM.cargaFallida.value) {
                        // La carga del usuario falló tras varios intentos: en vez de
                        // un spinner eterno, ofrecemos reintentar.
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                "No se pudieron cargar tus datos. Comprueba tu conexión.",
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { sesionVM.cargarUsuario() }) {
                                Text("Reintentar")
                            }
                        }
                    } else {
                        CircularProgressIndicator()
                    }
                }
                return@composable
            }

            // Técnicos certificadores y empresas de reformas comparten la UI profesional
            if (tipoUsuario == "Técnico" || tipoUsuario == "Empresa") {
                val panelTecnicoVM: PanelTecnicoViewModel = viewModel()
                val panelVM: PanelViewModel = viewModel()
                val misClientesVM: MisClientesTecnicoViewModel = viewModel()
                MainScaffoldTecnico(
                    panelTecnicoVM = panelTecnicoVM,
                    panelVM = panelVM,
                    misClientesVM = misClientesVM,
                    chatVM = chatVM,
                    onCerrarSesion = cerrarSesion,
                    onPerfil       = { nav.navigate("perfil") },
                    onAjustes      = { nav.navigate("ajustes") },
                    onSobreApp     = { nav.navigate("sobre_app") },
                    onChats        = { id -> nav.navigate("chat/$id") },
                    onSuscripcion  = { nav.navigate("suscripcion") },
                    onResenas      = { nav.navigate("resenas_tecnico") },
                    onEstadisticas = { nav.navigate("estadisticas_tecnico") },
                    onVerPerfilPublico = {
                        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                            nav.navigate("perfil_tecnico/$uid")
                        }
                    }
                )
            } else {
                val panelVM: PanelViewModel = viewModel()
                MainScaffold(
                    panelViewModel = panelVM,
                    chatViewModel = chatVM,
                    onCerrarSesion = cerrarSesion,
                    onNuevoPreestudio   = { nav.navigate("preestudio") },
                    onBuscarTecnicos    = { nav.navigate("tecnicos") },
                    onRankings          = { nav.navigate("rankings") },
                    onVerUltimoInforme  = { nav.navigate("informe") },
                    onPerfil            = { nav.navigate("perfil") },
                    onHistorialInformes = { nav.navigate("historial_informes") },
                    onMisViviendas      = { nav.navigate("mis_viviendas") },
                    onChats             = { id -> nav.navigate("chat/$id") },
                    onGraficas          = { nav.navigate("graficas") },
                    onMapaTecnicos      = { nav.navigate("mapa_tecnicos") },
                    onSobreApp          = { nav.navigate("sobre_app") },
                    onAjustes           = { nav.navigate("ajustes") }
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
                onContactarTecnicos = { especialidad ->
                    // El directorio comparte ViewModel: se abre ya filtrado (o sin filtro)
                    tecnicosVM.cambiarFiltro(especialidad)
                    nav.navigate("tecnicos")
                }
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
                        if (chatId.isNotBlank()) nav.navigate("chat/$chatId")
                    }
                }
            )
        }

        composable("rankings") {
            val rankingsVM: RankingsViewModel = viewModel()
            RankingsScreen(
                viewModel = rankingsVM,
                onVolver = { nav.popBackStack() },
                onVerPerfil = { id -> nav.navigate("perfil_tecnico/$id") }
            )
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
            AjustesScreen(
                viewModel = ajustesVM,
                onVolver = { nav.popBackStack() },
                onCerrarSesion = cerrarSesion
            )
        }

        composable("resenas_tecnico") {
            val resenasTecVM: ResenasRecibidasViewModel = viewModel()
            ResenasRecibidasScreen(viewModel = resenasTecVM, onVolver = { nav.popBackStack() })
        }
        composable("estadisticas_tecnico") {
            val estadisticasTecVM: EstadisticasTecnicoViewModel = viewModel()
            EstadisticasTecnicoScreen(
                viewModel = estadisticasTecVM,
                onVolver = { nav.popBackStack() },
                onSuscripcion = { nav.navigate("suscripcion") },
                onEditarPerfil = { nav.navigate("perfil") }
            )
        }
        // Suscripción del profesional (verificado / destacado)
        composable("suscripcion") {
            val suscripcionVM: SuscripcionViewModel = viewModel()
            SuscripcionScreen(
                viewModel = suscripcionVM,
                onVolver = { nav.popBackStack() }
            )
        }
    }
}
