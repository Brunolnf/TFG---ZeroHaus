package com.example.zerohaus.Navegacion

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.zerohaus.UserInterface.*

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
                onOlvideContrasena = {
                    // Demo: sin acción
                }
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
                    navController.popBackStack() // vuelve a login
                }
            )
        }

        composable("dashboard") {
            PanelScreen(
                onNotificaciones = {
                    // Si aún no tienes esa pantalla, déjalo vacío o navega cuando la crees
                    // navController.navigate("notificaciones")
                },
                onAjustes = {
                    // navController.navigate("ajustes")
                },
                onCerrarSesion = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                },
                onVerUltimoInforme = {
                    // navController.navigate("informe")
                },
                onNuevoPreestudio = {
                    // navController.navigate("preestudio")
                },
                onBuscarTecnicos = {
                    // navController.navigate("tecnicos")
                },
                onMisProyectos = {
                    // navController.navigate("mis_proyectos")
                },
                onRankings = {
                    // navController.navigate("rankings")
                }
            )
        }
    }
}
