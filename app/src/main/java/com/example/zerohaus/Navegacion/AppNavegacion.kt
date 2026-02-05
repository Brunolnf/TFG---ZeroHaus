package com.example.zerohaus.Navegacion

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.zerohaus.UserInterface.LoginScreen
import com.example.zerohaus.UserInterface.RegistroScreen
import com.example.zerohaus.UserInterface.PreestudioScreen
import com.example.zerohaus.UserInterface.TecnicosScreen
import com.example.zerohaus.UserInterface.*
import com.example.zerohaus.UserInterface.RankingsScreen
import com.example.zerohaus.UserInterface.*
import com.zerohaus.ui.pantallas.home.PanelScreen

@Composable
fun AppNavegacion() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "login"
    ) {

        composable("login") {
            LoginScreen(
                onIniciarSesion = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onRegistrarse = {
                    navController.navigate("registro")
                },
                onOlvideContrasena = {}
            )
        }

        composable("registro") {
            RegistroScreen(
                onCrearCuenta = {
                    navController.navigate("dashboard") {
                        popUpTo("registro") { inclusive = true }
                    }
                },
                onIniciarSesion = {
                    navController.popBackStack()
                }
            )
        }

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

        composable("preestudio") {
            PreestudioScreen(
                onVolver = { navController.popBackStack() }
            )
        }

        composable("tecnicos") {
            TecnicosScreen(
                onVolver = { navController.popBackStack() }
            )
        }

        composable("proyectos") {
            MisProyectosScreen(
                onVolver = { navController.popBackStack() }
            )
        }

        composable("rankings") {
            RankingsScreen(
                onVolver = { navController.popBackStack() }
            )
        }

        composable("informe") {
            InformeScreen(
                onVolver = { navController.popBackStack() }
            )
        }
    }
}
