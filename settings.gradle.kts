pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.1"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    create(rootProject, file("versions.json5"))
}

// MC-free, plain-Java loader-agnostic core shared by :common and both loaders (compiled against
// directly) and bundled into every shipped loader jar. Version-agnostic — one artifact covers
// every game version. Sibling subproject, NOT a stonecutter branch.
include(":core")

// Plain-JVM JUnit smoke suite: fast invariants that catch a broken template. Not a Stonecutter
// branch. Run it with `./gradlew smokeTest`.
include(":smoke")

