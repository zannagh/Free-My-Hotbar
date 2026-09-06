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

// SUBPROJECTS
// Sibling subprojects (not Stonecutter branches) are included here. Later template jobs
// append the smoke test suite and the Paper plugin below this marker, e.g.:
//   include(":smoke")
//   include(":paper")

// Plain-JVM JUnit smoke suite: fast invariants that catch a broken template. Not a Stonecutter
// branch. Run it with `./gradlew smokeTest`.
include(":smoke")

// PaperMC/Bukkit server-side plugin. A plain Gradle subproject (not a Stonecutter branch): it uses
// only stable Bukkit API, so one jar covers every supported game version. Remove it with
// `remove-paper.sh` / `remove-paper.ps1` if the template's mod has no server component.
include(":paper")
