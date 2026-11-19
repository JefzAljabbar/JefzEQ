plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jefz.eq"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.jefz.eq"
        minSdk = 28
        targetSdk = 34
        versionCode = 8
        versionName = "0.8"
    }
    // Fixed signing key (app/jefz.keystore) so every new build installs OVER the old one without uninstalling.
    signingConfigs {
        getByName("debug") {
            storeFile = file("jefz.keystore")
            storePassword = "jefzeq2026"
            keyAlias = "jefz"
            keyPassword = "jefzeq2026"
        }
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
