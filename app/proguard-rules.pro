# ════════════════════════════════════════════════════════════════════════
# ZeroHaus — reglas R8/ProGuard
# ════════════════════════════════════════════════════════════════════════
# isMinifyEnabled = true en buildType release. Sin estas reglas R8 destruye
# campos de los data classes que Firestore deserializa por reflexión y la
# app crashea al primer toObject() en producción.

# ── Preserva números de línea para stack traces legibles ────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Modelos de Firestore: reflexión vía getters/setters ─────────────────
# Firestore usa toObject<T>() / DocumentSnapshot.toObject(T::class.java) que
# llama a getters por nombre. R8 los renombraría → null en todos los campos.
-keep class com.example.zerohaus.Modelos.** { *; }
-keepclassmembers class com.example.zerohaus.Modelos.** { *; }

# Anotaciones de Firestore (PropertyName, Exclude, IgnoreExtraProperties)
-keepattributes *Annotation*
-keep class com.google.firebase.firestore.** { *; }
-dontwarn com.google.firebase.firestore.**

# ── Firebase Auth / FCM ──────────────────────────────────────────────────
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.messaging.** { *; }

# ── Firebase Functions: callable wrappers ────────────────────────────────
-keep class com.google.firebase.functions.** { *; }

# ── Google Play Billing (suscripciones de profesionales) ─────────────────
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# ── Coil: usa kotlin reflection en algunos módulos ───────────────────────
-dontwarn coil.**

# ── Google Maps Compose ──────────────────────────────────────────────────
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# ── Coroutines: evita warnings de DebugProbesKt ──────────────────────────
-dontwarn kotlinx.coroutines.debug.**

# ── Compose runtime / lifecycle: defaults seguros ────────────────────────
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ── Kotlin metadata: reflexión y data class copy()/toString() ────────────
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlinx.** { volatile <fields>; }

# ── Enums: serialización Firestore ───────────────────────────────────────
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Elimina todas las llamadas a Log en release ─────────────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# ── Ofuscación agresiva ──────────────────────────────────────────────────
# repackageclasses '' mueve TODAS las clases al paquete raíz (nombres a→a.a).
# allowaccessmodification permite a R8 romper private/protected para inlinear
# y ofuscar mejor. NO usamos -overloadaggressively: rompe la reflexión de
# Firestore en casos límite y el beneficio es marginal.
-repackageclasses ''
-allowaccessmodification
-optimizationpasses 5

# ── Elimina los checks de null de Kotlin en release ──────────────────────
# Reducen la información que un reverser ve en el bytecode (nombres de
# parámetros, mensajes de "parameter X must not be null").
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkParameterIsNotNull(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkFieldIsNotNull(...);
    public static void checkReturnedValueIsNotNull(...);
}

# NOTA: SecurityUtil se ofusca a propósito (NO lleva -keep). Así sus métodos
# esRooteado()/fridaDetectada()/… no aparecen con nombre legible en el APK
# decompilado y son mucho más difíciles de localizar y hookear.

# ── Activities/Receivers/Services del manifest ya los protege R8 ─────────
# (com.example.zerohaus.ServicioNotificaciones, MainActivity)
