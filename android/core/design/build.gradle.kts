plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.bidzz.opencode.core.design"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }

    kotlinOptions {
        jvmTarget = libs.versions.java.get()
    }

    // Unit tests call android.* APIs (Log, Base64, ...) that return default
    // values on the JVM instead of throwing (62-F8): app and core:api had
    // this on, the other modules did not, so the same call behaved
    // differently depending on which module's test ran it.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // `api` rather than `implementation`: consumers compose with these types
    // directly, so they need the Compose runtime on their compile classpath too.
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.compose.material.icons.core)
    api(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
