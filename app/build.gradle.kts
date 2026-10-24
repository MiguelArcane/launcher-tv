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
        versionCode = 1
        versionName = "1.0"
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
