plugins {
    java
    `java-library`
    // eunomia's maven sources. :core is outside the Stonecutter tree, so it does not inherit the
    // repositories multiloader-common installs and declares them itself.
    id("eunomia-repositories")
}

// Minecraft-free, loader-agnostic core: plain Java + java.util only. Shared by :common and both
// loaders (compiled against directly) and bundled into every shipped loader jar. One artifact,
// game-version-agnostic — NOT a stonecutter branch.

repositories {
    mavenCentral()
}

java {
    // 1.20.1 baseline — Java 17, the lowest toolchain across the matrix, so the one core artifact
    // loads everywhere the loaders (and :smoke) do.
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

dependencies {
    // Consumers already ship SLF4J (Minecraft on the loaders, slf4j-simple in :smoke). compileOnly
    // keeps :core a good citizen on whatever classpath it lands on. Versions match the ones already
    // used elsewhere in this repo (see smoke/build.gradle.kts and common/build.gradle.kts).
    compileOnly("org.slf4j:slf4j-api:2.0.16")
    compileOnly("org.jspecify:jspecify:1.0.0")

    // eunomia's MC-free half only: the packet types, the payload codec and the capability
    // handshake live in eunomia-core, so FmhPackets and the presence derivation can sit here in
    // the Minecraft-free module and be unit-tested from :smoke. eunomia-common (MC-facing) must
    // never reach this classpath. compileOnly — the eunomia mod supplies the runtime.
    addEunomiaCoreOnly()
}
