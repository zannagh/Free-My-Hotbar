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

// Loom only creates the remapping `mod*` configurations while obfuscation is on. The 26.x
// variants run Loom with `fabric.loom.disableObfuscation` (see loom-deobfuscated), where mod
// dependencies are plain `implementation` coordinates instead.
val modConfiguration = if (project.isDeobf) "implementation" else "modImplementation"

dependencies {
    if (!project.isDeobf) {
        add(modConfiguration, "net.fabricmc:fabric-loader:${property("loader_version")}")
    }
    // Fabric API — required for networking (ClientPlayNetworking/ServerPlayNetworking),
    // keybinds (KeyBindingHelper) and lifecycle events (ClientTickEvents/connection events).
    // Per-variant, from `fabricapi.version` in stonecutter.properties.toml: the coordinate
    // carries the MC version, so one hardcoded value cannot serve the matrix. A variant that
    // pins no version is one whose sources do not compile yet — it configures, it just has no
    // Fabric API on its classpath until its coordinate is filled in.
    project.prop("fabricapi.version")?.let { add(modConfiguration, "net.fabricmc.fabric-api:fabric-api:$it") }
}

// The eunomia MOD is a required runtime dependency (fabric.mod.json), so every dev run needs its
// jar in the run mods dir or fabric-loader aborts mod resolution at boot. Loom points the run dir
// at build/run, so `clean` empties it — hence the task is never up-to-date (see EunomiaRuntimeMod).
// Registered at Fabric-branch level, NOT inside the FCGT block below: the copy (and the stale-jar
// cleanup it depends on) has to exist on every Fabric variant, or the ones without client game
// tests boot with an empty run/mods and fail mod resolution on their required eunomia dependency.
val copyEunomiaToMods = registerCopyEunomiaToMods(layout.buildDirectory.dir("run/mods"))
wireRunsToEunomiaCopy(copyEunomiaToMods)

// ── Fabric Client Game Tests ─────────────────────────────────────────────────────
// `runClientGametest` boots a real client, hands the main loop to FCGT's runner and runs the
// entrypoints below. Only on variants that pin `fabricapi.semver` (MC >= 1.21.8).
if (project.fcgtEnabled) {
    configure<net.fabricmc.loom.api.LoomGradleExtensionAPI> {
        runConfigs.create("clientGametest") {
            client()
            displayName.set("Client GameTest")
            // FCGT needs BOTH properties. `fabric.client.gametest` makes its mixin config plugin
            // apply the lifecycle/threading mixins that hand the main loop to the runner;
            // `fabric.client.gametest.modid` is what the runner filters `fabric-client-gametest`
            // entrypoints by. Without the modid the mixins still fire but no test is dispatched,
            // so Minecraft idles at the title screen and the task exits GREEN having run nothing.
            jvmArguments.add("-Dfabric.client.gametest=true")
            jvmArguments.add("-Dfabric.client.gametest.modid=free-my-hotbar")
            // eunomia injects its payload codecs into the vanilla packet codec from a netty
            // thread. FCGT's NetworkSynchronizer reads that as "interfacing with packets at a
            // lower level" and turns it into a hard AssertionError the moment the client
            // connects. The codec injection is load-bearing, so the synchronizer goes instead —
            // FCGT names this very property in the error it throws.
            jvmArguments.add("-Dfabric.client.gametest.disableNetworkSynchronizer=true")
            // Keep the test window from stealing focus on macOS (SDL reads these before creating
            // the window; harmless on every other backend and platform).
            environmentVars.put("SDL_WINDOW_ACTIVATE_WHEN_SHOWN", "0")
            environmentVars.put("SDL_WINDOW_ACTIVATE_WHEN_RAISED", "0")
        }
    }
    // eunomia is a REQUIRED dependency, so a gametest launch without it aborts mod resolution
    // before a single test runs. `wireRunsToEunomiaCopy` only knows runClient/runServer.
    copyEunomiaToMods?.let { copy -> tasks.named("runClientGametest") { dependsOn(copy) } }
}

val expandProps = mapOf(
    "version" to project.version,
    "java_version" to project.prop("java.version")!!,
    "fabric_minecraft_version" to project.prop("fabric.minecraft_version_range")!!
)

// The `fabric-client-gametest` entrypoints, in run order. One launch of `runClientGametest` runs
// every entry, so a class listed here reds the whole run when it fails — which is the point.
// Empty on every variant that does not pin `fabricapi.semver`, and the shipped fabric.mod.json
// already says `[]`, so those variants are left byte-identical to the source.
val fcgtEntrypoints = if (project.fcgtEnabled) {
    listOf(
        "io.github.zannagh.freemyhotbar.smoke.LockedSlotEvictionSmokeTest",
        "io.github.zannagh.freemyhotbar.smoke.QuickMoveEvictionSmokeTest",
        "io.github.zannagh.freemyhotbar.smoke.GuiClickBlockingSmokeTest",
        "io.github.zannagh.freemyhotbar.smoke.SlotLockScreenSmokeTest"
    )
} else {
    emptyList()
}

tasks.processResources {
    inputs.properties(expandProps)
    inputs.property("fcgtEntrypoints", fcgtEntrypoints)
    filesMatching(listOf("fabric.mod.json", "**/*.mixins.json"), ExpandPropertiesAction(expandProps))
    filesMatching("fabric.mod.json", InjectFcgtEntrypointsAction(fcgtEntrypoints))
}

tasks.named<ProcessResources>("processClientResources") {
    inputs.properties(expandProps)
    filesMatching("**/*.mixins.json", ExpandPropertiesAction(expandProps))
}
