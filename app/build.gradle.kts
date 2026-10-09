import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.example.zerohaus"
    compileSdk = 37

    defaultConfig {
        applicationId = "es.zerohaus.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "2.3"   // factura con IA, IRPF, plan por etapas y arreglos del diagnóstico

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = localProps.getProperty("MAPS_API_KEY", "")
    }

    signingConfigs {
        create("release") {
            storeFile = file("zerohaus-release.jks")
            storePassword = localProps.getProperty("KEYSTORE_PASSWORD")
            keyAlias = "zerohaus"
            keyPassword = localProps.getProperty("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Nota: NO añadir applicationIdSuffix aquí — google-services.json
            // solo tiene config para "es.zerohaus.app" y un suffix rompería
            // la inicialización de Firebase. Si en el futuro quieres debug y
            // release instaladas en paralelo, hay que registrar
            // "es.zerohaus.app.debug" como app aparte en Firebase Console.
            // No sube el mapping de R8 en debug. Los fallos de debug no se
            // envían a Crashlytics: lo desactiva Util/Diagnostico.kt
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        disable += "InvalidFragmentVersionForActivityResult"
    }

    // Desde AGP 9 Kotlin va integrado (sin el plugin kotlin-android) y el
    // compilador de Compose lo aporta kotlin.plugin.compose: sin composeOptions
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {

    // Firebase
    // BOM 34: los módulos -ktx desaparecen (sus APIs Kotlin están en los principales)
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-config")

    // Google Play Billing (suscripciones de profesionales). Play exige al
    // menos la v8 para publicar actualizaciones desde el 31/08/2026.
    implementation("com.android.billingclient:billing-ktx:9.1.0")

    // Google Maps
    implementation("com.google.maps.android:maps-compose:8.6.0")
    implementation("com.google.android.gms:play-services-maps:20.0.0")
    implementation("com.google.android.gms:play-services-location:21.4.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")

    // Activity
    implementation("androidx.activity:activity-compose:1.13.0")

    // Core
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.core:core-splashscreen:1.2.0")

    // Security — EncryptedSharedPreferences (AES-256)
    implementation("androidx.security:security-crypto:1.1.0")

    // Google Play In-App Review (pedir valoración sin salir de la app)
    implementation("com.google.android.play:review-ktx:2.0.2")

    // Coil
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Orientación EXIF de las fotos al comprimirlas (Util/Imagenes.kt). Ya
    // venía en el APK por otras librerías; se declara para poder usarla.
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}