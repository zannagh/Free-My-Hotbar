plugins {
    id("multiloader-loader")
    id("net.neoforged.moddev.legacyforge")
}

val forgeVersion = findProperty("forge.version")?.toString()
    ?: error("No Forge version mapping for Minecraft ${project.mcVersion}")

// The eunomia MOD is a required runtime dependency (mods.toml, side="BOTH"), so both dev runs need
// its jar in the run mods dir or FML aborts mod resolution at boot. MDG's default gameDirectory is
// the variant's `run/` dir — NOT build/run like Loom's — so the mods dir differs per loader and is
// passed in explicitly. Null when no eunomia source could be resolved (see EunomiaRuntimeMod).
val copyEunomiaToMods = registerCopyEunomiaToMods(provider { layout.projectDirectory.dir("run/mods") })

val clientSourceSet = sourceSets.create("client") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}

// Classic Forge doesn't split environments — main has the full MC jar, so common client sources
// must also be in main for Forge-specific code that references common client classes.
val commonSourceSets = extra["commonSourceSets"] as SourceSetContainer
sourceSets.main {
    java { commonSourceSets["client"].java.srcDirs.forEach { srcDir(it) } }
    resources { commonSourceSets["client"].resources.srcDirs.forEach { srcDir(it) } }
}

stonecutter {
    constants["forge"] = true
}

legacyForge {
    version = forgeVersion

    runs {
        register("client") {
            client()
            copyEunomiaToMods?.let { taskBefore(it) }
            // Launch via the Gradle run task, which forks the game on the project's Java 17
            // toolchain. The daemon must stay on Java 21 for Stonecutter 0.9.1, so we cannot rely
            // on the IDE's Gradle JVM; MDG's generated IntelliJ run config pins no JRE and would
            // otherwise inherit the 21 daemon and crash MC 1.20.1 (LWJGL unsupported JNI version).
            disableIdeRun()
        }
        register("server") {
            server()
            copyEunomiaToMods?.let { taskBefore(it) }
            disableIdeRun()
        }
    }

    mods {
        register("free_my_hotbar") {
            sourceSet(sourceSets.main.get())
            sourceSet(clientSourceSet)
        }
    }
}

// Mixins for classic Forge. MDG's `config(...)` only registers the config for dev runs
// (as `--mixin.config` launch args) and does NOT emit anything into the shipped jar: it neither
// writes the `MixinConfigs` manifest attribute nor generates a refmap. Two things are therefore
// wired explicitly below so the reobfuscated production jar actually applies the mixins:
//   1. `add(sourceSet, refmapName)` runs the Mixin annotation processor for that source set,
//      generating the SRG refmap (build/mixin/...) and bundling it into the jar. The refmap maps
//      the mixin's official method references (getFreeSlot / getSlotWithRemainingSpace) to SRG
//      names at runtime; without it the @Inject targets never resolve in a reobf'd jar.
//   2. The `MixinConfigs` manifest attribute (set on tasks.jar below) — classic Forge registers
//      mixin configs from that manifest entry.
// Every mixin class ends up in the main source set (common main AND common client sources are
// srcDir'd into main above), so `add(sourceSets.main, ...)` runs the AP over all of them and the
// single main refmap covers both configs. The client config travels the same way: common's client
// RESOURCES are srcDir'd into main's resources too, so `free-my-hotbar.client.mixins.json` is
// stamped with the refmap name by `processResources` below. The `processClientResources` block is
// only for forge's own client source set, which ships no mixin configs of its own today.
mixin {
    config("free-my-hotbar.mixins.json")
    config("free-my-hotbar.client.mixins.json")
    add(sourceSets.main.get(), "free-my-hotbar.refmap.json")
}

// The Mixin annotation processor generates the SRG refmap that `mixin.add(...)` wires into the jar.
// MDG configures the compiler ARGS for it but does not put the processor on the classpath, so add
// it here. Forge 1.20.1 ships Mixin 0.8.5; the `:processor` classifier is the fat AP jar.
dependencies {
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
}

tasks.jar {
    from(clientSourceSet.output)
    // Common client sources are in both main and client (main needs them for compile visibility,
    // client gets them from multiloader-loader). Exclude duplicates in the jar.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Classic Forge loads mixin configs from this manifest attribute. MDG does not add it, so do it
    // here. The reobfuscation (RemapJar) step copies this manifest through to the shipped jar.
    manifest {
        attributes("MixinConfigs" to "free-my-hotbar.mixins.json,free-my-hotbar.client.mixins.json")
    }
}

val expandProps = mapOf(
    "version" to project.version,
    "minecraft_version" to project.prop("forge.minecraft_version_range")!!,
    "forge_version" to forgeVersion,
    "java_version" to project.prop("java.version")!!
)

// The refmap name here must match the one passed to mixin.add(...) above so the packaged Forge
// mixin configs point Mixin at the generated SRG refmap. Fabric is untouched (it keeps the clean
// shared source config and remaps mixins statically).
val mixinRefmap = "free-my-hotbar.refmap.json"

tasks.processResources {
    inputs.properties(expandProps)
    inputs.property("mixinRefmap", mixinRefmap)
    filesMatching(listOf("META-INF/mods.toml", "**/*.mixins.json"), ExpandPropertiesAction(expandProps))
    filesMatching("**/*.mixins.json", InjectMixinRefmapAction(mixinRefmap))
}

tasks.named<ProcessResources>("processClientResources") {
    inputs.properties(expandProps)
    inputs.property("mixinRefmap", mixinRefmap)
    filesMatching("**/*.mixins.json", ExpandPropertiesAction(expandProps))
    filesMatching("**/*.mixins.json", InjectMixinRefmapAction(mixinRefmap))
}
