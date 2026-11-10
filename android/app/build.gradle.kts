// PlayAgain32: prepares imported ARM32 games locally on an ARM64 phone.
// Build native files with tools/build-android.ps1 before packaging the app.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.playagain32.app"
    compileSdk = 37

    defaultConfig {
        // Stable install identity preserves earlier alpha imports and game-signing keys.
        applicationId = "dev.legacybridge.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.3-alpha"
        ndk.abiFilters += "arm64-v8a"
    }

    signingConfigs {
        System.getenv("PLAYAGAIN32_KEYSTORE")?.takeIf { it.isNotBlank() }?.let { keystore ->
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("PLAYAGAIN32_KEY_PASSWORD")
                keyAlias = System.getenv("PLAYAGAIN32_KEY_ALIAS") ?: "playagain32"
                keyPassword = System.getenv("PLAYAGAIN32_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures { compose = true }

    // The converter runs as a program and the bridge files are copied into games, so they
    // must exist as real files in the app's library folder.
    packaging.jniLibs.useLegacyPackaging = true
}

// Direct Android Studio builds must not produce an APK with a missing runtime.
tasks.configureEach {
    if (name == "packageDebug" || name == "packageRelease") {
        doFirst {
            if (name == "packageRelease") {
                check(!System.getenv("PLAYAGAIN32_KEYSTORE").isNullOrBlank() &&
                    !System.getenv("PLAYAGAIN32_KEY_PASSWORD").isNullOrBlank()) {
                    "Release signing requires PLAYAGAIN32_KEYSTORE and PLAYAGAIN32_KEY_PASSWORD."
                }
            }
            listOf("libarm32_bridge.so", "libapkconvert.so", "libstandin.so", "libgamebridge_pacer.so").forEach {
                check(file("src/main/jniLibs/arm64-v8a/$it").isFile) { "Missing $it. Run tools/build-android.ps1 first." }
            }
            check(!file("src/main/assets/debug.keystore").exists()) { "Do not embed a private signing key in the app." }
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    // Signs converted games with the same key as tools/convert.sh.
    implementation("com.android.tools.build:apksig:9.4.1")
}
