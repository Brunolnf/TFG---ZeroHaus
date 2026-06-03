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
import com.example.zerohaus.Util.AppPreferencias
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.Util.NotificacionesLocales
import com.example.zerohaus.Util.getCadenas
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* resultado gestionado por el sistema */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash Screen API: muestra el icono adaptativo sobre fondo blanco
        // mientras inicializamos. Backport a Android < 12 vía core-splashscreen.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        AppEstado.inicializar(AppPreferencias(this))
        NotificacionesLocales.crearCanales(this)
        pedirPermisoNotificaciones()
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            appCheckFactory()
        )
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

    /**
     * Selecciona la factory de App Check según el build type.
     * - Debug: DebugAppCheckProviderFactory (solo en classpath debug).
     *   Cargada por reflexión para que el compilador no la exija en release.
     * - Release: PlayIntegrityAppCheckProviderFactory.
     */
    private fun appCheckFactory(): AppCheckProviderFactory {
        if (BuildConfig.DEBUG) {
            runCatching {
                val clazz = Class.forName(
                    "com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory"
                )
                return clazz.getMethod("getInstance").invoke(null) as AppCheckProviderFactory
            }
            // Si la clase no existe (build raro), caemos al provider de prod.
        }
        return PlayIntegrityAppCheckProviderFactory.getInstance()
    }

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
