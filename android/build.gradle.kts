plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Dependency locking (audit-100 Fase 4, 62-F2/63-F1/84-F6): the version catalog pins
// direct versions, but transitive versions used to float — two clean builds of the
// same commit could resolve different graphs. With locking on, every module commits
// a `gradle.lockfile` and the locked versions act as strict constraints, so the
// same commit always resolves the same graph. Two behaviours to know (both verified
// locally in this phase):
// - A version bump in the catalog WITHOUT regenerating locks is silently IGNORED
//   (the old locked version wins) — regenerate deliberately with
//   `./gradlew test lint :app:assembleDebug :app:assembleRelease --write-locks`
//   after an intended bump, and commit the updated lockfiles.
// - A NEW dependency that no lockfile covers FAILS the build ("... which is not
//   part of the dependency lock state") until locks are regenerated. CI never
//   writes locks; it only enforces them.
subprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

// Unit-test workers are forked JVMs that talk back to the Gradle daemon over
// loopback TCP. On hosts whose DNS answers with AAAA records that cannot be
// routed (GitHub Actions runners in some regions, and most Android-on-ARM dev
// boxes), a worker JVM that defaults to IPv6 never connects and the test run
// times out after 120s with "Unable to connect to the child process". The
// daemon itself is forced onto IPv4 by org.gradle.jvmargs / systemProp in
// gradle.properties; the workers need the same flags explicitly, because they
// do not inherit the daemon's JVM args.
subprojects {
    tasks.withType<Test>().configureEach {
        jvmArgs(
            "-Djava.net.preferIPv4Stack=true",
            "-Djava.net.preferIPv4Addresses=true",
        )
        systemProperty("java.net.preferIPv4Stack", "true")
        systemProperty("java.net.preferIPv4Addresses", "true")
    }
}
