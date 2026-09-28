plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.astrofixxer"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.astrofixxer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // Release signing comes from the environment (CI secrets), never from the repo. Without it, release builds are unsigned.
    val keystore = System.getenv("ASTROFIXXER_KEYSTORE")
    val previewKeystore = System.getenv("ASTROFIXXER_PREVIEW_KEYSTORE")
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storePassword = System.getenv("ASTROFIXXER_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ASTROFIXXER_KEY_ALIAS")
            keyPassword = System.getenv("ASTROFIXXER_KEY_PASSWORD")
        }
        // Key for the downloadable preview build, also from the environment (a CI secret), so official downloads
        // update each other and nobody else can sign a look-alike update. Without it, preview builds use the debug key.
        if (previewKeystore != null) create("preview") {
            storeFile = file(previewKeystore)
            storePassword = System.getenv("ASTROFIXXER_PREVIEW_PASSWORD")
            keyAlias = "preview"
            keyPassword = System.getenv("ASTROFIXXER_PREVIEW_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
        }
        // Optimised like a release, installable by anyone from GitHub Releases (./gradlew assemblePreview).
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            signingConfig = signingConfigs.getByName(if (previewKeystore != null) "preview" else "debug")
            matchingFallbacks += listOf("release")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
