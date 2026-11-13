plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.bidzz.opencode.core.api"
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.serialization.json)

    // OkHttp rather than Ktor: the engine's event stream is read line by line off a
    // raw socket, which OkHttp exposes directly through `response.body.source()`.
    // Ktor would add a full client framework on top of the same OkHttp engine and
    // still need the raw channel for SSE. One HTTP stack, not two.
    api(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
