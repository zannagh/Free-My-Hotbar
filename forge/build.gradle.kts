plugins {
    id("multiloader-loader")
    id("net.neoforged.moddev.legacyforge")
}

val forgeVersion = findProperty("forge.version")?.toString()
    ?: error("No Forge version mapping for Minecraft ${project.mcVersion}")

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
            // Launch via the Gradle run task, which forks the game on the project's Java 17
            // toolchain. The daemon must stay on Java 21 for Stonecutter 0.9.1, so we cannot rely
            // on the IDE's Gradle JVM; MDG's generated IntelliJ run config pins no JRE and would
            // otherwise inherit the 21 daemon and crash MC 1.20.1 (LWJGL unsupported JNI version).
            disableIdeRun()
        }
        register("server") {
            server()
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

// Mixins are wired via the top-level mixin block (not mods.toml) for classic Forge; MDG adds
// the MixinConfigs manifest attribute and refmap from the configs listed here.
mixin {
    config("free-my-hotbar.mixins.json")
    config("free-my-hotbar.client.mixins.json")
}

tasks.jar {
    from(clientSourceSet.output)
    // Common client sources are in both main and client (main needs them for compile visibility,
    // client gets them from multiloader-loader). Exclude duplicates in the jar.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val expandProps = mapOf(
    "version" to project.version,
    "minecraft_version" to project.prop("forge.minecraft_version_range")!!,
    "forge_version" to forgeVersion,
    "java_version" to project.prop("java.version")!!
)

tasks.processResources {
    inputs.properties(expandProps)
    filesMatching(listOf("META-INF/mods.toml", "**/*.mixins.json"), ExpandPropertiesAction(expandProps))
}

tasks.named<ProcessResources>("processClientResources") {
    inputs.properties(expandProps)
    filesMatching("**/*.mixins.json", ExpandPropertiesAction(expandProps))
}
