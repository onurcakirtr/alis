plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.alis.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alis.app"
        minSdk = 29
        targetSdk = 35
        // GitHub'daki release iş akışı sürümü etiketten verir (v1.2.0 → 1.2.0); yerelde 1.0
        versionCode = (findProperty("alisVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (findProperty("alisVersionName") as String?) ?: "1.0"
    }

    // Release imzası: ALIS_KEYSTORE ortam değişkeni varsa (GitHub sırları) o anahtar, yoksa debug anahtarı
    val keystore = System.getenv("ALIS_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("ALIS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ALIS_KEY_ALIAS")
                keyPassword = System.getenv("ALIS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystore != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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
    // Android 13+ uygulama dili menüsü res/values-<dil> klasörlerinden kendiliğinden oluşturulur
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:discovery"))
    implementation(project(":core:upnp"))
    implementation(project(":core:akis"))
    implementation(project(":core:data"))
    implementation(project(":core:player"))
    implementation(project(":core:ui"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // QR okuma (yalnızca kameralı cihazlarda görünür)
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    // Google TV ana ekranı: "Devam et" satırı
    implementation("androidx.tvprovider:tvprovider:1.0.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
