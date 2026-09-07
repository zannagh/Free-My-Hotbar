plugins {
    id("multiloader-loader")
}

val plugin = if (project.isDeobf) "loom-deobfuscated" else "loom-obfuscated"
plugins.apply(plugin)

val sc = project.stonecutterBuild
val fabricVersion = findProperty("fabric.minecraft_version")!!.toString()

stonecutter {
    constants["fabric"] = true
}

configure<net.fabricmc.loom.api.LoomGradleExtensionAPI> {
    splitEnvironmentSourceSets()

    mods {
        register("free-my-hotbar") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }

    // Shared run directory for all versions
    runConfigs.configureEach {
        val dir = project.layout.buildDirectory.dir("run")
        runDirectory.set(dir)
        generateRunConfig.set(true)
        if (project.isDeobf) {
            jvmArguments.add("-Dfabric.gameVersion=${fabricVersion}")
        }
    }
}

dependencies {
    if (!project.isDeobf) {
        add("modImplementation", "net.fabricmc:fabric-loader:${property("loader_version")}")
    }
    // Fabric API — required for networking (ClientPlayNetworking/ServerPlayNetworking),
    // keybinds (KeyBindingHelper) and lifecycle events (ClientTickEvents/connection events).
    add("modImplementation", "net.fabricmc.fabric-api:fabric-api:0.92.2+1.20.1")
}

val expandProps = mapOf(
    "version" to project.version,
    "java_version" to project.prop("java.version")!!,
    "fabric_minecraft_version" to project.prop("fabric.minecraft_version_range")!!
)

tasks.processResources {
    inputs.properties(expandProps)
    filesMatching(listOf("fabric.mod.json", "**/*.mixins.json"), ExpandPropertiesAction(expandProps))
}

tasks.named<ProcessResources>("processClientResources") {
    inputs.properties(expandProps)
    filesMatching("**/*.mixins.json", ExpandPropertiesAction(expandProps))
}
