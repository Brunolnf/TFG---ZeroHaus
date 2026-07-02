package com.example.zerohaus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import com.example.zerohaus.Navegacion.AppNavegacion
import com.example.zerohaus.ui.theme.ZeroHausTheme
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.Util.getCadenas

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* resultado gestionado por el sistema */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash Screen API: muestra el icono adaptativo sobre fondo blanco
        // mientras inicializamos. Backport a Android < 12 vía core-splashscreen.
        // La inicialización pesada (App Check, Firestore, prefs) ya se hizo en
        // ZeroHausApp.onCreate, antes incluso de crear esta Activity.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        pedirPermisoNotificaciones()
        // Reintento de registro del token FCM en cada arranque. AppNavegacion ya
        // lo hace cuando logueado=true, pero solo en la TRANSICIÓN false→true:
        // si la app se relanza con sesión ya activa el LaunchedEffect no
        // re-dispara y el token puede no haberse guardado nunca (red caída en el
        // primer login, fallo silencioso de Firestore…). Aquí lo cubrimos.
        ServicioNotificaciones.registrarToken()
        setContent {
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (AppEstado.tema) {
                "Claro" -> false
                "Oscuro" -> true
                else -> systemDark
            }
            val cadenas = remember(AppEstado.idioma) { getCadenas(AppEstado.idioma) }
            CompositionLocalProvider(LocalCadenas provides cadenas) {
                ZeroHausTheme(darkTheme = darkTheme) {
                    AppNavegacion()
                }
            }
        }
    }

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
