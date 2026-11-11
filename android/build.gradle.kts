plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
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
