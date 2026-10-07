package com.example.zerohaus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.zerohaus.Navegacion.AppNavegacion
import com.example.zerohaus.UserInterface.ConAvisoSinConexion
import com.example.zerohaus.ui.theme.ZeroHausTheme
import com.example.zerohaus.Util.AppEstado
import com.example.zerohaus.Util.LocalCadenas
import com.example.zerohaus.Util.SecurityUtil
import com.example.zerohaus.Util.getCadenas
import com.example.zerohaus.Util.Diagnostico
import com.example.zerohaus.Util.NotificacionesLocales
import com.google.firebase.auth.FirebaseAuth

/**
 * Única Activity de la app (arquitectura single-activity con Navigation Compose).
 * Instala la splash, pide el permiso de notificaciones y, en release, bloquea
 * las capturas de pantalla (FLAG_SECURE) y termina la app si detecta un
 * depurador o un tracer.
 *
 * La sesión se mantiene como en cualquier app: antes se cerraba tras 5 minutos
 * en segundo plano y había que volver a poner la contraseña cada vez.
 */
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* resultado gestionado por el sistema */ }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (!BuildConfig.DEBUG) {
                if (SecurityUtil.debuggerConectado() || SecurityUtil.tracerDetectado()) {
                    FirebaseAuth.getInstance().signOut()
                    finishAffinity()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // En depuración se permiten capturas (pruebas, memoria, ficha de Play)
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        window.decorView.filterTouchesWhenObscured = true

        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        pedirPermisoNotificaciones()
        ServicioNotificaciones.registrarToken()
        leerChatDelAviso(intent)

        setContent {
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (AppEstado.tema) {
                "Claro" -> false
                "Oscuro" -> true
                else -> systemDark
            }
            val cadenas = remember(AppEstado.idioma) { getCadenas(AppEstado.idioma) }
            // Contexto de los informes de Crashlytics (idioma y tipo de usuario)
            LaunchedEffect(AppEstado.idioma, AppEstado.tipoUsuarioCache) { Diagnostico.actualizarContexto() }
            // Árabe se dibuja de derecha a izquierda; el resto de izquierda a derecha.
            val direccion = if (AppEstado.idioma == "العربية") LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(
                LocalCadenas provides cadenas,
                LocalLayoutDirection provides direccion
            ) {
                ZeroHausTheme(darkTheme = darkTheme) {
                    ConAvisoSinConexion { AppNavegacion() }
                }
            }
        }
    }

    // Con la app ya abierta (launchMode singleTop) el aviso llega por aquí
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        leerChatDelAviso(intent)
    }

    /**
     * Si la app se abre tocando un aviso de mensaje, recuerda su chat para
     * abrirlo directamente. Vale para los avisos que pinta Android con la app
     * en segundo plano (el `data` del push llega como extras) y para los de
     * NotificacionesLocales.
     */
    private fun leerChatDelAviso(intent: Intent?) {
        intent?.getStringExtra(NotificacionesLocales.EXTRA_CHAT_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { AppEstado.chatPendiente = it }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        super.onDestroy()
    }

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
