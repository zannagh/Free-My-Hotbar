import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.tasks.bundling.Jar
import java.io.Serializable

fun Project.prop(key: String): String? = findProperty(key)?.toString()
val Project.stonecutterBuild: StonecutterBuildExtension
    get() = extensions.getByType(StonecutterBuildExtension::class.java)

/** The loader encoded in the Stonecutter project name, e.g. "fabric" from "fabric-1.21.11". */
val Project.loader: String get() = stonecutterBuild.current.project.substringBefore("-")

/** The Minecraft version from the Stonecutter project, e.g. "1.21.11" from "fabric-1.21.11". */
val Project.mcVersion: String get() = stonecutterBuild.current.version.replace("snapshot.", "snapshot-")

/** Whether this version uses deobfuscated (unmapped) Minecraft jars. */
val Project.isDeobf: Boolean get() = mcVersion.startsWith("26.")

/**
 * An unfortunately required hack to get configuration-cache-safe expansion of properties into files.
 */
class ExpandPropertiesAction(private val props: Map<String, Any>) : Action<FileCopyDetails>, Serializable {
    override fun execute(details: FileCopyDetails) {
        details.expand(props)
    }
}

/**
 * Injects a `refmap` field into a mixin config JSON as it is copied. Used on the classic-Forge
 * side only: MDG's Mixin annotation processor generates an SRG refmap named e.g.
 * `free-my-hotbar.refmap.json`, but Mixin only loads it when the config references it by name —
 * otherwise it falls back to the default `mixin.refmap.json`, which does not exist, and the
 * injections silently never apply in a reobfuscated production jar. The field is added at package
 * time so the shared source config stays clean for Fabric (Loom remaps mixins statically and needs
 * no refmap). The insertion is a no-op if a `refmap` field is already present.
 */
class InjectMixinRefmapAction(private val refmap: String) : Action<FileCopyDetails>, Serializable {
    override fun execute(details: FileCopyDetails) {
        var injected = false
        details.filter { line: String ->
            when {
                line.contains("\"refmap\"") -> {
                    injected = true
                    line
                }
                !injected && line.trimStart().startsWith("\"required\"") -> {
                    injected = true
                    val indent = line.takeWhile { it == ' ' }
                    "$indent\"refmap\": \"$refmap\",\n$line"
                }
                else -> line
            }
        }
    }
}

/** Configures the jar task to include LICENSE with a project-specific suffix. */
fun Jar.includeLicense(archivesName: String) {
    from("LICENSE") {
        rename("LICENSE", "LICENSE_$archivesName")
    }
}

// ── eunomia coordinates ──────────────────────────────────────────────────────────
// eunomia ships two compile-time artifacts. `eunomia-core` is MC-free and carries no
// version suffix, so one coordinate resolves everywhere. `eunomia-common` is the
// MC-facing half and IS version-specific, so its coordinate needs eunomia's own
// display_version appended plus the `dev` classifier (eunomia's default artifact is
// loom's remapped jar, which is intermediary-mapped on every MC 1.x variant).
//
// eunomia's display_version is deliberately a SEPARATE property from ours: the two
// disagree on MC 1.20.1 (eunomia says mc-1.20.0-1 on Fabric and mc-1.20.1-forge on
// Forge; FMH says mc-1.20.1 on both, and that string names our jars and keys the
// publish matrix). See the comments in stonecutter.properties.toml.

/** eunomia's release tag, e.g. "0.3.14". Global — one value covers every variant. */
val Project.eunomiaVersion: String? get() = prop("eunomia.version")

/** eunomia's OWN display_version for this variant, e.g. "mc-1.20.0-1". Never ours. */
val Project.eunomiaDisplayVersion: String? get() = prop("eunomia.display_version")

/** eunomia's Modrinth loader prefix, keyed by the loader in the Stonecutter project name. */
private val EUNOMIA_MODRINTH_LOADER_PREFIXES = mapOf(
    "fabric" to "fab",
    "forge" to "forge",
    "neoforge" to "neo"
)

/**
 * The Modrinth version id of the eunomia mod jar for this variant, DERIVED rather than
 * pinned: eunomia names one release per (loader, MC range) as
 * `<fab|forge|neo>-<eunomia.display_version>-<eunomia.version>`. A new variant therefore
 * needs only its `eunomia.display_version`. `eunomia.modrinth_id` overrides it if eunomia
 * ever breaks that naming convention for a single release.
 */
val Project.eunomiaModrinthVersionId: String?
    get() {
        prop("eunomia.modrinth_id")?.let { return it }
        val version = eunomiaVersion ?: return null
        val display = eunomiaDisplayVersion ?: return null
        val prefix = EUNOMIA_MODRINTH_LOADER_PREFIXES[loader] ?: return null
        return "$prefix-$display-$version"
    }

/**
 * Adds eunomia's compile-time API to [configuration]. compileOnly everywhere: the eunomia
 * MOD supplies the implementation at game runtime (declared as a required dependency in
 * fabric.mod.json / mods.toml), so nothing of eunomia's is ever bundled into our jar.
 * Plain library dependencies, NOT remapped mod jars — they never go through loom.
 * No-op when the properties are absent, so an unpinned variant still configures.
 */
fun Project.addEunomiaCompileOnly(configuration: String = "compileOnly") {
    val version = eunomiaVersion ?: return
    dependencies.add(configuration, "de.zannagh.eunomia:eunomia-core:$version")
    val display = eunomiaDisplayVersion ?: return
    dependencies.add(configuration, "de.zannagh.eunomia:eunomia-common:$version+$display:dev")
}

/**
 * eunomia's release tag for projects OUTSIDE the Stonecutter tree — `:core` and `:smoke` are plain
 * Java modules, so Stonecutter never injects `eunomia.version` into them. Reads the single global
 * declaration out of `stonecutter.properties.toml` (the lines above the first `[section]` header)
 * so the version still lives in exactly one place. Falls back to the injected property when it IS
 * present, which keeps this usable from anywhere.
 */
val Project.eunomiaVersionGlobal: String?
    get() {
        eunomiaVersion?.let { return it }
        val toml = rootProject.file("stonecutter.properties.toml")
        if (!toml.isFile) {
            return null
        }
        val global = toml.readLines().takeWhile { !it.trimStart().startsWith("[") }
        return global
            .firstNotNullOfOrNull { Regex("""^\s*eunomia\.version\s*=\s*"([^"]+)"""").find(it) }
            ?.groupValues?.get(1)
    }

/**
 * Adds only the MC-free `eunomia-core` to [configuration]. For `:core` and `:smoke`, which are
 * Minecraft-free and therefore must never see `eunomia-common`. Same compileOnly rationale as
 * [addEunomiaCompileOnly]: the eunomia MOD supplies the implementation at game runtime.
 */
fun Project.addEunomiaCoreOnly(configuration: String = "compileOnly") {
    val version = eunomiaVersionGlobal ?: return
    dependencies.add(configuration, "de.zannagh.eunomia:eunomia-core:$version")
}

// ── Fabric Client Game Tests (FCGT) ──────────────────────────────────────────────
// One switch, `fabricapi.semver`, decides everything FCGT on a variant: the `fcgt` Stonecutter
// constant that compiles the test classes in, the compile dependency below, the entrypoint list
// injected into fabric.mod.json, and the `runClientGametest` task. Pinned only on Fabric variants
// from MC 1.21.4 up — older fabric-api lines have no fabric-client-gametest-api-v1 at all.

/** Whether this variant runs in-game client tests (a Fabric variant that pins `fabricapi.semver`). */
val Project.fcgtEnabled: Boolean
    get() = prop("fabricapi.semver") != null && stonecutterBuild.current.project.contains("fabric")

/**
 * Adds `fabric-client-gametest-api-v1` to the CLIENT compile classpath of an FCGT-capable variant.
 *
 * <p>Needed on `:common`, which compiles the test classes but has no Fabric API on its classpath
 * (only `fabric-loader`). The loader projects already pull the module in transitively: they depend
 * on the `fabric-api` umbrella, whose POM lists every module including this one.
 *
 * The version is resolved through Loom's `fabricApi.module(...)` rather than hardcoded — the module
 * has its own semver (5.x/6.x) that does not track the umbrella's.
 */
fun Project.addFcgtClientCompileOnly() {
    val semver = prop("fabricapi.semver") ?: return
    if (!fcgtEnabled) {
        return
    }
    val fabricApi = extensions.getByType(net.fabricmc.loom.api.fabricapi.FabricApiExtension::class.java)
    val configuration = if (isDeobf) "clientCompileOnly" else "modClientCompileOnly"
    dependencies.add(configuration, fabricApi.module("fabric-client-gametest-api-v1", semver))
}

/**
 * Fills fabric.mod.json's `fabric-client-gametest` entrypoint list as the resource is copied.
 *
 * <p>A `${...}` placeholder cannot be used here: the shipped file has to stay valid JSON on its
 * own (the metadata smoke suite parses it straight off disk), and a bare placeholder in array
 * position is not. So the file ships the honest non-FCGT value — an empty list — and this rewrites
 * that one line on the variants that do run client game tests. A variant with no entries is left
 * exactly as shipped.
 */
class InjectFcgtEntrypointsAction(private val entries: List<String>) : Action<FileCopyDetails>, Serializable {
    override fun execute(details: FileCopyDetails) {
        if (entries.isEmpty()) {
            return
        }
        details.filter { line: String ->
            if (!line.contains("\"fabric-client-gametest\"")) {
                line
            } else {
                val indent = line.takeWhile { it == ' ' }
                val comma = if (line.trimEnd().endsWith(",")) "," else ""
                entries.joinToString(
                    separator = ",\n$indent  ",
                    prefix = "$indent\"fabric-client-gametest\": [\n$indent  ",
                    postfix = "\n$indent]$comma"
                ) { "\"$it\"" }
            }
        }
    }
}
