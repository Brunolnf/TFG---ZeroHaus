package com.example.zerohaus.Util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Si el móvil tiene una red con acceso a Internet, observado en tiempo real.
 *
 * Sin red Firestore sigue mostrando lo que tiene en caché y encola las
 * escrituras, pero las Cloud Functions (código de verificación, consejos de
 * IA, suscripciones) fallan: la app lo usa para avisar al usuario.
 *
 * Solo se mira la capacidad INTERNET, no VALIDATED: en redes que bloquean la
 * comprobación de Google la app funcionaría y el aviso sería un falso positivo.
 */
@Composable
fun rememberHayConexion(): State<Boolean> {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    val hayConexion = remember { mutableStateOf(tieneInternet(cm, cm.activeNetwork)) }
    DisposableEffect(cm) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                hayConexion.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            // Se pierde la red por defecto: si hay otra, llegará su onCapabilitiesChanged
            override fun onLost(network: Network) {
                hayConexion.value = false
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return hayConexion
}

private fun tieneInternet(cm: ConnectivityManager, red: Network?): Boolean =
    red != null && cm.getNetworkCapabilities(red)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
