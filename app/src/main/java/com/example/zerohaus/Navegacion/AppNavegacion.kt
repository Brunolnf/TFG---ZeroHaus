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
                    navController.popBackStack()
                }
            )
        }
        composable("dashboard") {
            PanelScreen(
                onNotificaciones = {

                },
                onAjustes = {

                },
                onCerrarSesion = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                },

            )
        }
    }
}
