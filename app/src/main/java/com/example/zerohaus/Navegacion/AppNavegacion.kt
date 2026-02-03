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
                onLogin = {
                    navController.navigate("panel") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onRegistro = {
                    navController.navigate("registro")
                },
                onRecuperar = {
                }
            )
        }

        composable("registro") {
            RegistroScreen(
                onCrearCuenta = {
                    // Registro correcto → Panel
                    navController.navigate("panel") {
                        popUpTo("registro") { inclusive = true }
                    }
                },
                onLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable("panel") {
            PanelScreen()
        }
    }
}
