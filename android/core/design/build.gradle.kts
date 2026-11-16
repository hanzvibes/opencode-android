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
    debugImplementation(libs.compose.ui.tooling)

    // Audit-200 C13 (M-018): this module declares no direct activity /
    // lifecycle / coroutines dependency, so those versions arrived
    // transitively from the Compose BOM (2024.12.01) and the lockfile
    // froze them at 1.8.2 / 2.8.3 / 1.7.3 — while :app, which declares
    // them directly, resolved to the catalog's 1.9.3 / 2.8.7 / 1.9.0.
    // Constraints pin this graph to the catalog too; regen the lockfile
    // after touching them (`--write-locks`), or the bump is silently
    // ignored by locking. The `testImplementation` /
    // `androidTestImplementation` lines exist because an `implementation`
    // constraint does not flow into the test classpaths, which kept
    // resolving the BOM versions. (kotlin-stdlib-jdk7/8 stay at 1.8.20
    // here vs 1.9.10 in :app: that is not a catalog version but the max
    // of :app's graph, and the shipped app already unifies on 1.9.10 —
    // no constraint to add.)
    constraints {
        implementation(libs.activity.compose)
        implementation(libs.lifecycle.runtime.compose)
        implementation(libs.kotlinx.coroutines.android)
        testImplementation(libs.activity.compose)
        testImplementation(libs.lifecycle.runtime.compose)
        testImplementation(libs.kotlinx.coroutines.android)
        androidTestImplementation(libs.activity.compose)
        androidTestImplementation(libs.lifecycle.runtime.compose)
        androidTestImplementation(libs.kotlinx.coroutines.android)
    }
}
