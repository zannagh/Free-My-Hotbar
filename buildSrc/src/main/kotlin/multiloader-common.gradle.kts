plugins {
    id("java")
    id("java-library")
}

val sc = project.stonecutterBuild
val loader = sc.branch.id
sc.constants["fabric"] = sc.current.project.contains("fabric")
sc.constants["forge"] = sc.current.project.contains("forge")

// Register the MC version part as a property tag so version-shared sections in
// stonecutter.properties.toml (e.g. ["1.21.11"]) resolve for this variant. The default
// project tag (e.g. "fabric-1.21.11") already applies the per-project sections.
sc.properties.tags(sc.current.project.substringAfter('-'))

val javaVersion = findProperty("java.version")?.toString() ?: error("No Java version specified")
val displayVersion = findProperty("display_version")?.toString() ?: error("No display version specified")

val isPreRelease = findProperty("prerelease")?.toString()?.lowercase() != "false"
val semVer = findProperty("semVer")?.toString()?.takeIf { it.isNotEmpty() } ?: "0.0.1-preview.0"

version = "$semVer+$displayVersion"
group = property("maven_group").toString()

base {
    archivesName.set("${property("archives_base_name")}-$loader")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

tasks.jar {
    includeLicense(base.archivesName.get())
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    // only run tests once
    enabled = sc.current.isActive
}
