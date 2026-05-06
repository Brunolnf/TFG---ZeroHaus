package com.example.zerohaus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import com.example.zerohaus.Navegacion.AppNavegacion
import com.example.zerohaus.ui.theme.ZeroHausTheme
import com.example.zerohaus.util.AppEstado
import com.example.zerohaus.util.AppPreferencias
import com.example.zerohaus.util.LocalCadenas
import com.example.zerohaus.util.NotificacionesLocales
import com.example.zerohaus.util.getCadenas

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* resultado gestionado por el sistema */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppEstado.inicializar(AppPreferencias(this))
        NotificacionesLocales.crearCanales(this)
        pedirPermisoNotificaciones()
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
