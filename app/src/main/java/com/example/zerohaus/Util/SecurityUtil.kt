package com.example.zerohaus.Util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.provider.Settings
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

/**
 * Comprobaciones de integridad del dispositivo y de la app: root, emulador,
 * depurador, tracer, Frida, Xposed, firma e instalador. Se ofusca con R8 a
 * propósito (sin reglas -keep).
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

    fun esRooteado(): Boolean {
        val paths = arrayOf(
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
            "/system/xbin/busybox",
            "/sbin/magisk"
        )
        for (p in paths) {
            if (File(p).exists()) return true
        }
        val dangerousProps = arrayOf(
            "ro.debuggable" to "1",
            "ro.secure" to "0"
        )
        try {
            val process = Runtime.getRuntime().exec("getprop")
            val reader = BufferedReader(process.inputStream.reader())
            val output = reader.readText()
            reader.close()
            for ((prop, value) in dangerousProps) {
                if (output.contains("[$prop]: [$value]")) return true
            }
        } catch (_: Exception) {}

        return try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            true
        } catch (_: Exception) {
            false
        }
    }

    // ─── Debug/tampering detection ──────────────────────────────────────

    fun esDebugeable(ctx: Context): Boolean {
        return (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun esAdbActivo(ctx: Context): Boolean {
        return try {
            Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    fun debuggerConectado(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    fun tracerDetectado(): Boolean {
        return try {
            val br = BufferedReader(FileReader("/proc/self/status"))
            var line: String?
            while (br.readLine().also { line = it } != null) {
                if (line!!.startsWith("TracerPid:")) {
                    val pid = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                    br.close()
                    return pid != 0
                }
            }
            br.close()
            false
        } catch (_: Exception) {
            false
        }
    }

    // ─── Frida detection ────────────────────────────────────────────────

    fun fridaDetectada(): Boolean {
        val fridaPorts = intArrayOf(27042, 27043)
        for (port in fridaPorts) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress("127.0.0.1", port), 100)
                socket.close()
                return true
            } catch (_: Exception) {}
        }

        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                val content = mapsFile.readText()
                if (content.contains("frida") || content.contains("gadget")) return true
            }
        } catch (_: Exception) {}

        return false
    }

    // ─── Xposed detection ───────────────────────────────────────────────

    fun xposedDetectado(): Boolean {
        try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            return true
        } catch (_: ClassNotFoundException) {}

        try {
            Class.forName("de.robv.android.xposed.XC_MethodHook")
            return true
        } catch (_: ClassNotFoundException) {}

        val stackTrace = Thread.currentThread().stackTrace
        for (element in stackTrace) {
            if (element.className.contains("xposed", ignoreCase = true)) return true
            if (element.className.contains("EdXposed", ignoreCase = true)) return true
            if (element.className.contains("LSPosed", ignoreCase = true)) return true
        }

        val suspiciousApps = arrayOf(
            "de.robv.android.xposed.installer",
            "org.meowcat.edxposed.manager",
            "org.lsposed.manager",
            "com.topjohnwu.magisk"
        )
        try {
            val pm = java.lang.Runtime.getRuntime().exec("pm list packages")
            val reader = BufferedReader(pm.inputStream.reader())
            val output = reader.readText()
            reader.close()
            for (app in suspiciousApps) {
                if (output.contains(app)) return true
            }
        } catch (_: Exception) {}

        return false
    }

    // ─── APK signature verification ─────────────────────────────────────

    fun verificarFirma(ctx: Context, sha256Esperado: String): Boolean {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ctx.packageManager.getPackageInfo(
                    ctx.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                ctx.packageManager.getPackageInfo(
                    ctx.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }

            if (signatures.isNullOrEmpty()) return false

            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(signatures[0].toByteArray())
            val hex = hash.joinToString("") { "%02X".format(it) }
            hex.equals(sha256Esperado, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    // ─── Installer verification ─────────────────────────────────────────

    fun instaladoDesdeTiendaOficial(ctx: Context): Boolean {
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            ctx.packageManager.getInstallerPackageName(ctx.packageName)
        }
        return installer in listOf(
            "com.android.vending",
            "com.google.android.feedback"
        )
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
        if (esAdbActivo(ctx)) amenazas.add(AmenazaDetectada("ADB", 1))

        return amenazas
    }
}
