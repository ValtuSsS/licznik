plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Numer kolejnego builda z GitHub Actions – dzięki temu nowy APK instaluje się jako aktualizacja.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "pl.licznik.km"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.licznik.km"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Stały klucz podpisu: każda nowa wersja instaluje się na starą BEZ utraty danych.
    signingConfigs {
        create("staly") {
            storeFile = file("licznik.p12")
            storeType = "pkcs12"
            storePassword = "licznikkm"
            keyAlias = "licznik"
            keyPassword = "licznikkm"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("staly")
        }
        debug {
            signingConfig = signingConfigs.getByName("staly")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
