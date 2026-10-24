plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.owari.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.owari.launcher"
        minSdk = 26
        // Se queda en 28 a propósito: desde Android 10, las apps que apuntan a 29+
        // ya no pueden apagar/prender el Wi-Fi ni pedir reconexión.
        targetSdk = 28
        versionCode = 4
        versionName = "1.3"
        // La TV es ARM de 32 bits; así el motor web no trae las versiones para otros procesadores.
        ndk { abiFilters += "armeabi-v7a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        // targetSdk 28 es intencional (ver arriba).
        disable += "ExpiredTargetSdkVersion"
        abortOnError = false
    }
}

dependencies {
    // Reproductor de video para TV IP (canales en vivo HLS).
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // Motor web de Firefox 140 ESR: el de la TV (Chromium 91) no entiende los sitios modernos.
    implementation("org.mozilla.geckoview:geckoview-armeabi-v7a:140.0.20250707120347")
}
