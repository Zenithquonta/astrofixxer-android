// Runs the app's real Compose UI and astronomy code on the desktop JVM (Compose Multiplatform), so the screens can be
// driven by tests, measured and screenshotted without an Android device. Run: ./gradlew test (from this folder).
// ponytail: an older Compose (1.5) on purpose; it needs nothing from Google's Maven repository.
plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.compose") version "1.5.12"
}
repositories { mavenCentral() }

val app = file("../../app/src/main/java/org/astrofixxer")
sourceSets {
    main {
        kotlin.srcDir("$app/astro")
        kotlin.srcDir("$app/ui")
        kotlin.srcDir("$app/update") // pure update logic the updater screen shows (the Android host in $app/host is not included)
        java.srcDir("$app/astro/vsop87")
    }
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> { kotlinOptions.jvmTarget = "17" }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.json:json:20240303") // part of the platform on Android
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    workingDir = file("../../app") // tests read src/main/assets like the app does
    systemProperty("screens", file("build/screens").absolutePath)
    testLogging { events("passed", "failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL; showStandardStreams = true }
}
