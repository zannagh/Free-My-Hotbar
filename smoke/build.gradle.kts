plugins {
    java
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

repositories {
    mavenCentral()
}

// The smoke suite exercises the mod's loader-agnostic core (FreeMyHotbar + the slot model) WITHOUT
// booting Minecraft or resolving Loom: it compiles against the MC-free :core module directly and
// validates the shipped loader metadata as plain files. Mixin classes (which touch Minecraft) live
// in :common and are never on this classpath. This keeps the suite fast and dependency-light while
// still proving the core wiring is intact.
dependencies {
    // The MC-free core under test (FreeMyHotbar, SlotBlock, SlotSelection, SlotLockState).
    testImplementation(project(":core"))

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // FreeMyHotbar logs via SLF4J: the API compiles it, and a simple provider lets init() run
    // cleanly without the "no SLF4J providers were found" warning.
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")

    // Metadata invariants: parse the shipped fabric.mod.json / mixin configs (JSON) and
    // mods.toml (TOML) the same way the loaders would.
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
