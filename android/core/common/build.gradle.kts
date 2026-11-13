plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.bidzz.opencode.core.common"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
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
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
