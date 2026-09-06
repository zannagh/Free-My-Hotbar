plugins {
    java
}

// The Paper plugin talks to the server through stable Bukkit API only, so a single jar covers
// every supported game version - it is not a Stonecutter branch and needs no per-version variants.
// `semVer` is supplied by the release tooling; the fallback keeps a plain local build working.
val semVer = findProperty("semVer")?.toString()?.takeIf { it.isNotEmpty() } ?: "0.0.1-preview.0"
val displayVersion = "paper"

group = "io.github.zannagh.freemyhotbar"
version = "$semVer+$displayVersion"

base {
    archivesName.set("free-my-hotbar-paper")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper 1.21.x runs on a Java 21 baseline, matching this template's toolchain. `compileOnly`
    // because the server provides the API at runtime - it must never be bundled into the plugin jar.
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// Inject the resolved project version into plugin.yml at build time so the manifest and the
// artifact never drift apart.
tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version.toString())
    }
}
