plugins {
    id("dev.kikugie.stonecutter")
    id("net.neoforged.moddev.legacyforge") version "2.0.140" apply false
}

stonecutter active "fabric-1.20.1" /* [SC] DO NOT EDIT */

// ── Global token replacements ─────────────────────────────────────────────────
// A renamed-but-otherwise-identical API is a rename, not a behaviour change, so it belongs
// in one table here rather than in a per-file `//? if` gate. This alone removes about half
// of the gating the version matrix would otherwise need.
//
// Each rule is written canonically "newest name -> the name that version range has". Note
// that the sources on disk are always stored in the ACTIVE variant's dialect: switching the
// active variant rewrites them in place, in whichever direction that switch needs, and the
// generated sources of every non-active variant are rewritten the same way. So with a 1.20.1
// variant checked out the files below read `ClickType`, not `ContainerInput`.
//
// NOTE the overlap between `handleContainerInput` and `ContainerInput`. Stonecutter feeds
// the whole table to one Aho-Corasick pass (see stitcher's TrieSearcher) rather than
// applying the rules in sequence, so the longer token wins and `handleContainerInput` is
// never mangled into `handleClickType` - and a genuinely ambiguous pair is reported as a
// build error rather than silently mis-rewritten.
stonecutter parameters {
    // ── 26.1: the render/interaction API rename wave ──
    // ClickType -> ContainerInput, and the gameMode entry point that takes it.
    replacements.string(current.parsed < "26.1") { replace("handleContainerInput", "handleInventoryMouseClick") }
    replacements.string(current.parsed < "26.1") { replace("ContainerInput", "ClickType") }
    // GuiGraphics became the render-state extractor; the draw calls were renamed with it.
    replacements.string(current.parsed < "26.1") { replace("GuiGraphicsExtractor", "GuiGraphics") }
    replacements.string(current.parsed < "26.1") { replace("extractRenderState", "render") }
    replacements.string(current.parsed < "26.1") { replace("centeredText", "drawCenteredString") }
    replacements.string(current.parsed < "26.1") { replace("extractBackground", "renderBackground") }

    // ── 26.3: the key-input enum constant was renamed ──
    // Same constant, new name: InputConstants.Type.KEYSYM became KEYBOARD (and SCANCODE went
    // away with it, which the mod never used).
    replacements.string(current.parsed < "26.3") { replace("InputConstants.Type.KEYBOARD", "InputConstants.Type.KEYSYM") }

    // ── 26.1: fabric-api renamed its key-binding module to key-mapping ──
    // A pure rename on Fabric API's side (module, package and both helper methods), with the same
    // semantics either way, so it belongs here rather than in a gate around the Fabric glue's
    // import and its one call. No-op on Forge, which never names these.
    replacements.string(current.parsed < "26.1") { replace("registerKeyMapping", "registerKeyBinding") }
    replacements.string(current.parsed < "26.1") { replace("KeyMappingHelper", "KeyBindingHelper") }
    replacements.string(current.parsed < "26.1") { replace("keymapping", "keybinding") }

    // ── 1.21: the component/equipment rename wave ──
    // NBT tags became data components; the stack comparison was renamed with them.
    replacements.string(current.parsed < "1.21") { replace("isSameItemSameComponents", "isSameItemSameTags") }
    // EquipmentSlot.Type.ARMOR was split into HUMANOID_ARMOR / ANIMAL_ARMOR (and later SADDLE).
    // Pre-1.21 ARMOR is exactly the HUMANOID_ARMOR of today, so the rewrite is value-preserving -
    // which is why the sources must keep naming the precise constant and never relax to isArmor().
    replacements.string(current.parsed < "1.21") { replace("EquipmentSlot.Type.HUMANOID_ARMOR", "EquipmentSlot.Type.ARMOR") }
}

// Plain-JVM smoke suite entry point (delegates to :smoke:test). Run with `./gradlew smokeTest`.
tasks.register("smokeTest") {
    group = "verification"
    description = "Runs the plain-JVM smoke test suite (:smoke:test)."
    dependsOn(":smoke:test")
}

// In-game client game tests (delegates to the :smoke `clientGametest` suite, which forks
// `:fabric:<variant>:runClientGametest` once per FCGT-capable variant and boots a real client).
//
// DELIBERATELY NOT wired into `check`: `./gradlew build`/`check` must stay a compile+unit gate that
// never starts Minecraft. This is the CI-invoked entry point instead — see
// .github/workflows/client-gametest.yml, which runs one variant as a PR gate on a display-capable
// self-hosted runner and the full matrix nightly.
tasks.register("clientGametest") {
    group = "verification"
    description = "Runs the Fabric in-game client game tests (boots real Minecraft clients)."
    dependsOn(":smoke:clientGametest")
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
