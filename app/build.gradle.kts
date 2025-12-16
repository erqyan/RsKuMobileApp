plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("kotlin-parcelize")
}


android {
    // ... rest of your file

    namespace = "com.example.myrs"
    compileSdk = 34 // Sesuaikan dengan SDK stabil terbaru (33/34), 36 mungkin masih preview

    defaultConfig {
        applicationId = "com.example.myrs"
        minSdk = 26 // Minimal SDK yang wajar untuk maps/firebase modern
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // --- Android Core & UI ---
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // --- Lifecycle ---
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // --- Maps & Location ---
    implementation("com.google.android.gms:play-services-location:21.1.0")
    implementation("com.mapbox.maps:android:11.2.0")

    // --- FIREBASE (Clean Setup) ---
    // Gunakan BOM untuk mengatur versi
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))

    // Library Firebase (Tanpa versi, diatur oleh BOM)
    implementation("com.google.firebase:firebase-database-ktx") // Realtime Database
    implementation("com.google.firebase:firebase-auth-ktx")     // Auth
    implementation("com.google.firebase:firebase-analytics-ktx") // Analytics
}
