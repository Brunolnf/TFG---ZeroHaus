package com.example.zerohaus.Navegacion

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.zerohaus.UserInterface.*
import com.zerohaus.ui.pantallas.home.PanelScreen

@Composable
fun AppNavegacion() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "login"
    ) {

        // Pantalla login
        composable("login") {
            LoginScreen(
                onIniciarSesion = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onRegistrarse = { navController.navigate("registro") },
                onOlvideContrasena = {}
            )
        }

        // Pantalla registro
        composable("registro") {
            RegistroScreen(
                onCrearCuenta = {
                    navController.navigate("dashboard") {
                        popUpTo("registro") { inclusive = true }
                    }
                },
                onIniciarSesion = { navController.popBackStack() }
            )
        }

        // Panel principal
        composable("dashboard") {
            PanelScreen(
                onCerrarSesion = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                },
                onNuevoPreestudio = { navController.navigate("preestudio") },
                onBuscarTecnicos = { navController.navigate("tecnicos") },
                onMisProyectos = { navController.navigate("proyectos") },
                onRankings = { navController.navigate("rankings") },
                onVerUltimoInforme = { navController.navigate("informe") }
            )
        }

        // Preestudio
        composable("preestudio") {
            PreestudioScreen(
                onVolver = { navController.popBackStack() }
            )
        }

        // Técnicos
        composable("tecnicos") {
            TecnicosScreen(
                onVolver = { navController.popBackStack() }
            )
        }

        // Proyectos
        composable("proyectos") {
            MisProyectosScreen(
                onVolver = { navController.popBackStack() }
            )
        }

        // Rankings
        composable("rankings") {
            RankingsScreen(
                onVolver = { navController.popBackStack() }
            )
        }

        // Informe
        composable("informe") {
            InformeScreen(
                onVolver = { navController.popBackStack() },

                // de momento estos dos no navegan, solo ejecutan lo que tú pongas luego
                onDescargar = { },
                onCompartir = { },

                // este botón sí manda a técnicos
                onContactarTecnicos = { navController.navigate("tecnicos") }
            )
        }
    }
}
