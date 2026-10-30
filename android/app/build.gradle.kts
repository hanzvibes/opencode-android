plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.bidzz.opencode"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.bidzz.opencode"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-dev"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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

    // Declared directly: `implementation` in :core:engine does not expose
    // coroutines to consumers, and the activity uses CoroutineScope and Flow.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
