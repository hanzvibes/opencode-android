import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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

/**
 * Debug signing uses the COMMITTED `debug.keystore` next to this file (audit-100 Fase 4,
 * 84-F2). Every debug APK — CI or local — is signed with the same key, so the rolling
 * `debug-latest` release can be installed as an in-place update instead of forcing an
 * uninstall (which wipes sessions and config). This is a DEBUG-ONLY key: same trust
 * level as Android's public default debug key (alias `androiddebugkey`, passwords
 * `android`), and it must never sign a release build — the release signing config
 * below stays separate and keystore-backed. Its SHA-256 certificate fingerprint is
 * 7D:1D:2E:34:A0:2B:14:BC:CA:33:3D:08:F5:94:92:DB:01:94:4F:F2:3F:7F:1E:39:C0:C3:6B:FE:CC:D6:4C:4D.
 */
val debugKeystoreFile = rootProject.file("debug.keystore")

/**
 * Runs `git ...` in the repo root (one level above this `android/` dir) and returns its
 * trimmed stdout, or null when there is no `.git` (tarball checkout), git is missing,
 * or the command fails. Plain `ProcessBuilder` — the Gradle `exec` APIs are deprecated
 * for configuration-time use and `providers.exec` needs a newer Gradle than 8.14's
 * stable surface for this shape.
 */
fun gitOutput(vararg args: String): String? {
    val repoDir = rootProject.projectDir.parentFile
    if (!File(repoDir, ".git").exists()) return null
    return runCatching {
        val proc = ProcessBuilder(listOf("git") + args.toList())
            .directory(repoDir)
            .redirectErrorStream(true)
            .start()
        val text = proc.inputStream.bufferedReader().readText().trim()
        if (proc.waitFor() != 0) null else text.takeIf { it.isNotEmpty() }
    }.getOrNull()
}

/**
 * `versionCode` (audit-100 Fase 4, 84-F3/62-F7): one scale, commit count on main, so every
 * distributed build is a higher version than the last and Obtainium / the package manager
 * actually detect the update. Precedence: `-PversionCode` > `VERSION_CODE` env > `git
 * rev-list --count HEAD` (repo root, when `.git` exists) > 2 (fallback for tarball
 * checkouts without git history — never distribute a build with the fallback code).
 * CI checks out with `fetch-depth: 0` so the count is the real one, not 1.
 */
fun resolveVersionCode(): Int {
    providers.gradleProperty("versionCode").orNull?.toIntOrNull()?.let { return it }
    System.getenv("VERSION_CODE")?.toIntOrNull()?.let { return it }
    gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull()?.let { return it }
    return 2
}

/**
 * `versionName`: the app's own `0.1.1` (NOT the engine's upstream tag, which happens to
 * share the number) plus a per-build suffix — `-PversionNameSuffix` > `VERSION_NAME_SUFFIX`
 * env > short git SHA > `local`. Each build is identifiable in Settings / Obtainium.
 */
fun resolveVersionNameSuffix(): String {
    providers.gradleProperty("versionNameSuffix").orNull?.takeIf { it.isNotBlank() }?.let { return it }
    System.getenv("VERSION_NAME_SUFFIX")?.takeIf { it.isNotBlank() }?.let { return it }
    gitOutput("rev-parse", "--short", "HEAD")?.let { return it }
    return "local"
}

android {
    namespace = "dev.bidzz.opencode"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.bidzz.opencode"
        minSdk = 29
        targetSdk = 35
        versionCode = resolveVersionCode()
        versionName = "0.1.1+${resolveVersionNameSuffix()}"

        // ABI selection lives in `splits` below: declaring `ndk.abiFilters` as well
        // is a hard configuration error in AGP 8.
    }

    signingConfigs {
        // AGP pre-creates a `debug` signing config (default debug keystore); re-point
        // it at the committed keystore instead of creating a second one.
        getByName("debug") {
            storeFile = debugKeystoreFile
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // Shrinks the Kotlin/Java side only; the ~217 MB engine is a native library
            // and is untouched, so expect a few MB off the APK, not tens.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    // Unit tests in this module exercise code that calls `android.util.Log`; without
    // this the stub throws "Method i not mocked" instead of returning.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:engine"))
    implementation(project(":core:design"))
    implementation(project(":core:common"))
    implementation(project(":core:api"))

    // Declared directly: `implementation` in :core:engine does not expose
    // coroutines to consumers, and the activity uses CoroutineScope and Flow.
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Compose host for the activity. Versions come from the BOM that :core:design
    // exports, so they stay aligned with the design system.
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    // App lock (Batch 7): BiometricPrompt needs a FragmentActivity, so the
    // activity extends it via fragment-ktx; biometric itself is the prompt API.
    implementation(libs.biometric)
    implementation(libs.fragment.ktx)
    // WindowSizeClass for the adaptive tablet layout (persistent sidebar +
    // list-detail at >=840dp). Version comes from the Compose BOM above.
    implementation(libs.material3.window.size)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    // The API DTOs are @Serializable; decoding them in a test needs the runtime on
    // the test classpath, which `implementation` in :core:api does not expose.
    testImplementation(libs.kotlinx.serialization.json)
    // `EngineConfigStore` writes through `org.json`, which is a stub in the mockable
    // android.jar. The mockable jar sits last on the test classpath, so the real
    // implementation here is what runs — otherwise every assertion about the file
    // the app wrote would be vacuous.
    testImplementation(libs.json)
}
