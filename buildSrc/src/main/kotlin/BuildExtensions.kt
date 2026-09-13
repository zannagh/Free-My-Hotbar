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
