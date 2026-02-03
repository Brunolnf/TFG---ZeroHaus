package com.example.zerohaus.Navegacion


import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.zerohaus.UserInterface.LoginScreen
import com.example.zerohaus.UserInterface.RegistroScreen
import com.example.zerohaus.UserInterface.PanelScreen
import com.example.zerohaus.ViewModel.RegistroViewModel
import com.zerohaus.app.ui.login.LoginViewModel

@Composable
fun AppNavegacion() {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "login"
    ) {

        composable("login") {
            val loginViewModel: LoginViewModel = viewModel()

            LoginScreen(
                onLoginCorrecto = {
                    navController.navigate("panel") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onRegistro = {
                    navController.navigate("registro")
                },
                onRecuperar = {},
                viewModel = loginViewModel
            )
        }

        composable("registro") {
            val registroViewModel: RegistroViewModel = viewModel()

            RegistroScreen(
                onRegistroCorrecto = {
                    navController.navigate("panel") {
                        popUpTo("registro") { inclusive = true }
                    }
                },
                onVolverLogin = {
                    navController.popBackStack()
                },
                viewModel = registroViewModel
            )
        }

        composable("panel") {
            PanelScreen()
        }
    }
}
