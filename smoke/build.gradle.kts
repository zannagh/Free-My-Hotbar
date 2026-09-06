plugins {
    java
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
}

// The smoke suite exercises the mod's loader-agnostic core (ExampleMod) WITHOUT booting
// Minecraft or resolving Loom: it compiles just that class straight from :common's main sources
// and validates the shipped loader metadata as plain files. Mixin classes under :common DO touch
// Minecraft, so they are excluded from this lightweight compile. This keeps the suite fast and
// dependency-light while still proving the core wiring is intact.
sourceSets {
    named("test") {
        java {
            srcDir(rootProject.file("common/src/main/java"))
            exclude("**/mixin/**")
        }
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // ExampleMod logs via SLF4J: the API compiles it, and a simple provider lets init() run
    // cleanly without the "no SLF4J providers were found" warning.
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")

    // Metadata invariants: parse the shipped fabric.mod.json / mixin configs (JSON) and
    // neoforge.mods.toml (TOML) the same way the loaders would.
    testImplementation("com.google.code.gson:gson:2.13.1")
    testImplementation("org.tomlj:tomlj:1.1.1")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // Locate the shipped loader metadata relative to the repo root, independent of the CWD.
    systemProperty("smoke.repo.root", rootProject.projectDir.absolutePath)
    testLogging {
        events("passed", "skipped", "failed")
    }
}
