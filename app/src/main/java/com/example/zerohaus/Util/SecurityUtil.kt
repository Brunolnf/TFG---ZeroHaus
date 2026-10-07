package com.example.zerohaus.Util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import java.io.File

/**
 * Comprobaciones de integridad del dispositivo y de la app: root, emulador,
 * depurador, tracer, Frida y Xposed. Se ofusca con R8 a propósito (sin
 * reglas -keep).
 *
 * Se ejecuta en el arranque (hilo principal), así que solo hace
 * comprobaciones rápidas de ficheros y propiedades: nada de lanzar procesos
 * (`su -c id` abría además el diálogo de superusuario en móviles con root) ni
 * de abrir sockets (en el hilo principal Android los prohíbe y la
 * comprobación de Frida por puerto nunca llegaba a funcionar). La protección
 * de verdad está en el servidor: App Check con Play Integrity.
 */
object SecurityUtil {

    // ─── Emulator detection ─────────────────────────────────────────────

    fun esEmulador(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BRAND.startsWith("generic") ||
                Build.DEVICE.startsWith("generic") ||
                Build.PRODUCT == "sdk_gphone64_arm64" ||
                Build.PRODUCT == "sdk_gphone_x86_64" ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu")
    }

    // ─── Root detection ─────────────────────────────────────────────────

    private val RUTAS_ROOT = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su",
        "/system/app/SuperSU.apk",
        "/system/app/KingRoot.apk",
        "/sbin/magisk"
    )

    fun esRooteado(): Boolean = RUTAS_ROOT.any { File(it).exists() }

    // ─── Debug/tampering detection ──────────────────────────────────────

    fun esDebugeable(ctx: Context): Boolean {
        return (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun debuggerConectado(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    fun tracerDetectado(): Boolean = try {
        File("/proc/self/status").useLines { lineas ->
            lineas.firstOrNull { it.startsWith("TracerPid:") }
                ?.substringAfter(":")?.trim()?.toIntOrNull()?.let { it != 0 } ?: false
        }
    } catch (_: Exception) {
        false
    }

    // ─── Frida detection ────────────────────────────────────────────────

    /** Frida inyectado en el propio proceso (sus librerías aparecen en el mapa de memoria). */
    fun fridaDetectada(): Boolean = try {
        File("/proc/self/maps").useLines { lineas -> lineas.any { it.contains("frida", ignoreCase = true) } }
    } catch (_: Exception) {
        false
    }

    // ─── Xposed detection ───────────────────────────────────────────────

    fun xposedDetectado(): Boolean {
        val clases = arrayOf("de.robv.android.xposed.XposedBridge", "de.robv.android.xposed.XC_MethodHook")
        if (clases.any { runCatching { Class.forName(it) }.isSuccess }) return true
        return Thread.currentThread().stackTrace.any { e ->
            e.className.contains("xposed", ignoreCase = true) || e.className.contains("LSPosed", ignoreCase = true)
        }
    }

    // ─── Comprehensive threat assessment ────────────────────────────────

    data class AmenazaDetectada(
        val tipo: String,
        val nivel: Int // 1=bajo, 2=medio, 3=critico
    )

    fun evaluarAmenazas(ctx: Context): List<AmenazaDetectada> {
        val amenazas = mutableListOf<AmenazaDetectada>()

        if (esRooteado()) amenazas.add(AmenazaDetectada("ROOT", 3))
        if (debuggerConectado()) amenazas.add(AmenazaDetectada("DEBUGGER", 3))
        if (tracerDetectado()) amenazas.add(AmenazaDetectada("TRACER", 3))
        if (fridaDetectada()) amenazas.add(AmenazaDetectada("FRIDA", 3))
        if (xposedDetectado()) amenazas.add(AmenazaDetectada("XPOSED", 3))
        if (esDebugeable(ctx)) amenazas.add(AmenazaDetectada("DEBUGGABLE", 2))
        if (esEmulador()) amenazas.add(AmenazaDetectada("EMULATOR", 1))

        return amenazas
    }
}
