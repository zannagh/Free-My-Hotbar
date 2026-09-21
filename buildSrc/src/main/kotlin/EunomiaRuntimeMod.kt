import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import java.io.File

private const val EUNOMIA_RUNTIME_MOD_CONFIGURATION = "eunomiaRuntimeMod"
private const val COPY_EUNOMIA_TASK = "copyEunomiaToMods"
private const val CLEAN_EUNOMIA_TASK = "cleanEunomiaFromMods"

/**
 * Registers `copyEunomiaToMods`, which drops the eunomia MOD jar into this variant's dev-run
 * mods dir. FMH compiles against eunomia-core/eunomia-common only; the eunomia mod supplies the
 * live transports at game runtime and is declared a REQUIRED dependency in fabric.mod.json /
 * mods.toml, so a dev client that boots without it aborts mod resolution.
 *
 * [modsDir] differs per loader and must be passed in: Loom points its run dir at
 * `<variant>/build/run`, MDG-legacyforge at `<variant>/run`.
 *
 * Two sources, in order:
 *  1. `-Peunomia.<loader>.jar=<path>` — a locally built eunomia. The path that always works, so
 *     it is checked first and is the escape hatch when a remote lookup fails.
 *  2. Modrinth, at an id DERIVED from `eunomia.display_version` + `eunomia.version` (see
 *     [eunomiaModrinthVersionId]) rather than a hand-maintained pin.
 *
 * Deliberately lenient: a variant that can resolve neither gets no task rather than failing
 * CONFIGURATION, which would break every task on it (`build`, the unit tests) and not just the
 * client runs that actually need the jar. Adding a variant therefore never breaks the build.
 *
 * Returns null when nothing could be resolved.
 */
fun Project.registerCopyEunomiaToMods(modsDir: Provider<Directory>): TaskProvider<Copy>? {
    // Registered and wired FIRST, and unconditionally: the stale-jar cleanup must not be able to
    // disappear along with the copy. See [registerCleanTask].
    val cleanTask = registerCleanTask(modsDir)
    tasks.matching { it.name == "runClient" || it.name == "runServer" }.configureEach {
        dependsOn(cleanTask)
    }

    val overrideJar = prop("eunomia.$loader.jar")
    if (overrideJar != null) {
        // Checked eagerly, and deliberately so. A Copy whose source does not exist is skipped as
        // NO-SOURCE, which takes any doFirst guard with it - the run would then boot with an empty
        // mods dir and fail on the missing dependency instead of on the typo. The check only ever
        // runs when someone explicitly passed the property, so it cannot fail an ordinary build.
        if (!file(overrideJar).exists()) {
            throw GradleException(
                "eunomia $loader mod jar (-Peunomia.$loader.jar) not found at:\n  $overrideJar"
            )
        }
        return registerCopyTask(
            modsDir,
            "Drop the local eunomia $loader mod jar into the dev run mods dir."
        ) { file(overrideJar) }
    }

    val modrinthId = eunomiaModrinthVersionId
    if (modrinthId == null) {
        logger.warn(
            "[free-my-hotbar] no eunomia mod jar source for ${stonecutterBuild.current.project}: the " +
                "Modrinth id could not be derived (needs `eunomia.version` + `eunomia.display_version`). " +
                "A dev run on this variant will fail its required eunomia dependency at boot. Set " +
                "`eunomia.display_version` for it, or pass -Peunomia.$loader.jar=<path>."
        )
        return null
    }

    val runtimeMod = configurations.create(EUNOMIA_RUNTIME_MOD_CONFIGURATION) {
        isCanBeResolved = true
        isCanBeConsumed = false
        isVisible = false
        isTransitive = false
    }
    dependencies.add(EUNOMIA_RUNTIME_MOD_CONFIGURATION, "maven.modrinth:eunomia:$modrinthId")
    return registerCopyTask(
        modsDir,
        "Drop the eunomia $loader mod jar (Modrinth $modrinthId) into the dev run mods dir."
    ) { resolveLeniently(runtimeMod, modrinthId) }
}

/**
 * Resolves the Modrinth jar, logging and skipping instead of failing the build. A remote lookup
 * is the one part of this that can break for reasons outside the repo (Modrinth down, a renamed
 * release); a hard failure there would take out every dev run, and the usable answer is always
 * `-Peunomia.$loader.jar`.
 */
private fun Project.resolveLeniently(configuration: Configuration, modrinthId: String): Collection<File> {
    return try {
        configuration.resolve()
    } catch (e: Exception) {
        logger.warn(
            "[free-my-hotbar] could not resolve the eunomia mod jar from Modrinth ($modrinthId): " +
                "${e.message}. Nothing was copied, so a dev run will fail its required eunomia " +
                "dependency at boot. Pass -Peunomia.$loader.jar=<path> to use a local build instead."
        )
        emptyList()
    }
}

/**
 * Registers the stale-jar cleanup as a task of its OWN, rather than as a `doFirst` on the copy.
 *
 * The dir must hold exactly one eunomia jar: a version bump, or a switch between the local override
 * and the Modrinth jar, changes the file name, and TWO eunomia mods load both copies of the codec
 * mixins, which double-applies them and breaks the handshake.
 *
 * A `doFirst` cannot carry that guarantee. When the Modrinth lookup fails, [resolveLeniently]
 * hands the copy an empty source, Gradle marks it NO-SOURCE and skips ALL of its actions — the
 * `doFirst` delete included. The old jar then survives and the dev run boots stale eunomia instead
 * of failing on the missing dependency the way the warning promises. A separate task has no source
 * to be skipped with; it always runs, whether or not anything is copied afterwards.
 */
private fun Project.registerCleanTask(modsDir: Provider<Directory>): TaskProvider<Task> {
    return tasks.register(CLEAN_EUNOMIA_TASK) {
        group = "verification"
        description = "Remove any eunomia mod jar from the dev run mods dir before a run."
        // A doLast on a task that declares no outputs, rather than a Delete: Loom's run dir lives
        // inside `build/`, so nothing about this is safe to call up-to-date.
        outputs.upToDateWhen { false }
        doLast {
            delete(fileTree(modsDir) { include("eunomia*.jar") })
        }
    }
}

/**
 * The shared Copy shape. Never up-to-date: Loom's run dir lives inside `build/`, so a `clean`
 * silently empties it while the task's outputs still look current. The cleanup it depends on is a
 * task of its own so a NO-SOURCE copy cannot skip it — see [registerCleanTask].
 */
private fun Project.registerCopyTask(
    modsDir: Provider<Directory>,
    taskDescription: String,
    source: () -> Any
): TaskProvider<Copy> {
    val cleanTask = tasks.named(CLEAN_EUNOMIA_TASK)
    return tasks.register(COPY_EUNOMIA_TASK, Copy::class.java) {
        group = "verification"
        description = taskDescription
        dependsOn(cleanTask)
        from(provider(source))
        into(modsDir)
        outputs.upToDateWhen { false }
    }
}

/** Wires the dev run tasks that exist on this loader to [copyTask]. */
fun Project.wireRunsToEunomiaCopy(copyTask: TaskProvider<Copy>?) {
    if (copyTask == null) {
        return
    }
    tasks.matching { it.name == "runClient" || it.name == "runServer" }.configureEach {
        dependsOn(copyTask)
    }
}
