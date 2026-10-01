plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// A bad value fails the build rather than quietly publishing a build the updater would think is old.
val buildVersionCode: Int = System.getenv("ASTROFIXXER_VERSION_CODE")?.takeIf { it.isNotBlank() }?.let {
    it.toIntOrNull()?.takeIf { n -> n > 0 } ?: throw GradleException("ASTROFIXXER_VERSION_CODE must be a positive whole number, not '$it'")
} ?: 1

android {
    namespace = "org.astrofixxer"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.astrofixxer"
        minSdk = 26
        targetSdk = 35
        // CI sets ASTROFIXXER_VERSION_CODE (the build number plus an offset) so every published preview build has a larger
        // versionCode than the one before; that is how the in-app updater knows a build is newer. Without it: 1.
        versionCode = buildVersionCode
        versionName = "0.1.0"
        // Off unless a build type turns it on. Google Play forbids apps that update themselves, so the updater screen and
        // the install permission (REQUEST_INSTALL_PACKAGES, in the preview and debug manifests only) exist just in those builds.
        buildConfigField("boolean", "UPDATER_ENABLED", "false")
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
        debug {
            buildConfigField("boolean", "UPDATER_ENABLED", "true")
        }
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
            buildConfigField("boolean", "UPDATER_ENABLED", "true")
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
        buildConfig = true
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
