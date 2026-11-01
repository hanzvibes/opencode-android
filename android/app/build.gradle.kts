import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Release signing is local-only. `keystore.properties` is gitignored and points at a keystore kept
 * outside the repository, so wiping the working tree cannot destroy the upload key. A missing file
 * leaves the release build unsigned rather than failing, which keeps CI able to assemble artifacts.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "dev.bidzz.opencode"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.bidzz.opencode"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-dev"

        // ABI selection lives in `splits` below: declaring `ndk.abiFilters` as well
        // is a hard configuration error in AGP 8.
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
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

    /**
     * One APK per ABI, because each carries its own ~217 MB engine.
     *
     * A universal APK would be ~340 MB. Play Store limits are irrelevant here (see
     * the distribution decision in `handoff.md` §3), but an APK that size is
     * painful to download and update over Obtainium. Splitting also keeps the
     * emulator-only x86_64 build out of arm64 users' downloads.
     *
     * `isUniversalApk = false` matters: without it AGP also emits a combined APK,
     * which defeats the point.
     */
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    // The engine binary is shipped as a native library so Android extracts it to
    // nativeLibraryDir, the only exec-allowed location since API 29.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation(project(":core:engine"))
    implementation(project(":core:design"))
    implementation(project(":core:common"))
    implementation(project(":core:api"))

    // Declared directly: `implementation` in :core:engine does not expose
    // coroutines to consumers, and the activity uses CoroutineScope and Flow.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Compose host for the activity. Versions come from the BOM that :core:design
    // exports, so they stay aligned with the design system.
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")

    testImplementation("junit:junit:4.13.2")
    // The API DTOs are @Serializable; decoding them in a test needs the runtime on
    // the test classpath, which `implementation` in :core:api does not expose.
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
