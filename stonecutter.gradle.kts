plugins {
    id("dev.kikugie.stonecutter")
    id("net.neoforged.moddev.legacyforge") version "2.0.140" apply false
}

stonecutter active "fabric-1.20.1" /* [SC] DO NOT EDIT */

// Plain-JVM smoke suite entry point (delegates to :smoke:test). Run with `./gradlew smokeTest`.
tasks.register("smokeTest") {
    group = "verification"
    description = "Runs the plain-JVM smoke test suite (:smoke:test)."
    dependsOn(":smoke:test")
}

// Builds every loader variant, collects the shippable jars into staging/, and writes a
// generated staging/versions.json (loader -> display_version -> [game_versions]) that the
// publish workflow reads to build its upload matrix. Adding an MC version/loader is a
// stonecutter change (versions.json5 + a toml section), not a workflow edit.
tasks.register("stageArtifacts") {
    group = "build"
    description = "Builds all loader variants and copies unique artifacts to staging/"

    // Loader variants live under :fabric:<variant> / :forge:<variant>; the container and
    // version-agnostic subprojects (:core/:common/:smoke) don't produce shippable jars.
    val loaderProjects = allprojects.filter {
        it.path.startsWith(":fabric:") || it.path.startsWith(":forge:")
    }
    loaderProjects.forEach { dependsOn("${it.path}:build") }

    val staging = rootProject.file("staging")

    doLast {
        staging.deleteRecursively()
        staging.mkdirs()

        val versionMap = mutableMapOf<String, MutableMap<String, List<String>>>()
        for (proj in loaderProjects) {
            val displayVersion = proj.findProperty("display_version")?.toString()
                ?: error("Missing display_version for ${proj.name}")
            val gameVersions = proj.findProperty("game_versions")?.toString()
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: error("Missing game_versions for ${proj.name}")

            // "fabric-1.20.1" -> "fabric" (variant names are "<loader>-<mc>").
            val loader = proj.name.substringBefore("-")
            val existing = versionMap.getOrPut(loader) { mutableMapOf() }
                .putIfAbsent(displayVersion, gameVersions)
            if (existing != null && existing.sorted() != gameVersions.sorted()) {
                error("Conflicting game_versions for $loader/$displayVersion: $existing vs $gameVersions")
            }

            proj.layout.buildDirectory.dir("libs").get().asFile.let { libsDir ->
                libsDir.listFiles()
                    ?.filter { it.extension == "jar" && !it.name.endsWith("-sources.jar") }
                    ?.forEach { jar ->
                        val target = staging.resolve(jar.name)
                        if (!target.exists()) jar.copyTo(target)
                    }
            }
        }

        // Emit staging/versions.json: { "<loader>": { "<display_version>": ["<game_versions>"] } }.
        val versionMapJson = versionMap.entries.sortedBy { it.key }
            .joinToString(",\n  ", "{\n  ", "\n}") { (loader, groups) ->
                val groupsJson = groups.entries.sortedBy { it.key }
                    .joinToString(",\n    ", "{\n    ", "\n  }") { (display, versions) ->
                        val versionsJson = versions.sorted().joinToString("\", \"", "[\"", "\"]")
                        "\"$display\": $versionsJson"
                    }
                "\"$loader\": $groupsJson"
            }
        staging.resolve("versions.json").writeText(versionMapJson)

        val files = staging.listFiles()?.filter { it.extension == "jar" }?.sortedBy { it.name } ?: emptyList()
        println("Staged ${files.size} artifacts:")
        files.forEach { println("  ${it.name} (${it.length() / 1024} KB)") }
    }
}
