import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Signier-Zugangsdaten aus einer gitignorierten Datei laden (falls vorhanden).
// Ohne diese Datei baut der Release-Typ unsigniert weiter, z. B. für Forks.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasKeystore = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasKeystore) FileInputStream(keystorePropertiesFile).use { load(it) }
}

fun gitValue(vararg args: String): String = runCatching {
    providers.exec {
        commandLine(listOf("git") + args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrDefault("")

val gitSha: String = gitValue("rev-parse", "--short", "HEAD").ifBlank { "unknown" }

android {
    namespace = "de.kewl.cashdy"
    compileSdk = 35

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "de.kewl.cashdy"
        minSdk = 26
        targetSdk = 35
        versionCode = 5                         // manuell, steigt je Release
        versionName = "0.0.5"                   // manuell
        resValue("string", "app_name", "Cashdy")
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        // Die Texterkennung (ML Kit) bringt je Prozessorart eine 11-MB-Bibliothek mit.
        // x86 gibt es praktisch nur im Emulator, deshalb nur ARM.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Native Bibliotheken komprimiert ablegen: kleinerer Download, entpackt wird bei der Installation.
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Nur für Android Studio: eigenes Paket, ersetzt die echte App nicht.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Cashdy Debug")
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            vcsInfo {
                include = false
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // DEV ist ein richtiger Release-Build (gleicher Keystore, R8, gleiches Paket),
        // nur die Versionsnummer trägt "-devN". Installiert sich über die normale App.
        // Bauen: ./gradlew assembleDev -PdevBuild=<n>
        create("dev") {
            initWith(getByName("release"))
            versionNameSuffix = "-dev" + (project.findProperty("devBuild") ?: "")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    // Texterkennung für Kassenbons, Modell liegt in der APK (läuft offline, ohne Play-Dienste-Download).
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
}
