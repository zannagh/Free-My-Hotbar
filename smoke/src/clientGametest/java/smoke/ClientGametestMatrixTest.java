package smoke;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Tier-2 in-game gate: forks {@code ./gradlew :fabric:<variant>:runClientGametest} once per
 * FCGT-capable variant and asserts the child exits 0.
 *
 * <p>One launch runs every {@code fabric-client-gametest} entrypoint listed in
 * {@code fabric/build.gradle.kts}, so a regression in any of them reds that variant's row. The
 * load-bearing JVM arguments ({@code fabric.client.gametest},
 * {@code fabric.client.gametest.modid}, {@code fabric.client.gametest.disableNetworkSynchronizer})
 * and the eunomia run-mods copy are configured on the run task itself — this class deliberately
 * adds none of them, so there is exactly one place where they are defined.
 *
 * <p>These rows boot a REAL Minecraft client, so they live in their own {@code clientGametest}
 * suite (never the default {@code test} suite) and {@code ./gradlew check} / {@code build} can
 * therefore never pull them in. Run them explicitly with {@code ./gradlew clientGametest}.
 *
 * <p>Filtering knobs, as system properties on the Gradle invocation:</p>
 * <ul>
 *   <li>{@code -Dgametest.only=<variant>[,<variant>]} — run only these variants.</li>
 *   <li>{@code -Dgametest.exclude=<variant>[,<variant>]} — skip these variants.</li>
 *   <li>{@code -Dgametest.ceiling.min=<n>} — per-row wall-clock ceiling in minutes (default 15).</li>
 * </ul>
 */
@DisplayName("Fabric client game tests")
class ClientGametestMatrixTest {

    /**
     * Variants that run in-game client tests — exactly those pinning {@code fabricapi.semver} in
     * {@code stonecutter.properties.toml}, which is what flips {@code Project.fcgtEnabled} and
     * creates the {@code runClientGametest} task. The floor is 1.21.8, NOT 1.21.4: see the FCGT
     * section of {@code buildSrc/src/main/kotlin/BuildExtensions.kt} for why 1.21.4 is excluded
     * despite having the module. Adding a variant here without pinning the property fails fast —
     * the task simply does not exist on that project.
     */
    static final List<String> FCGT_VARIANTS = List.of(
            "fabric-1.21.8",
            "fabric-1.21.10",
            "fabric-1.21.11",
            "fabric-26.1.2",
            "fabric-26.2",
            "fabric-26.3"
    );

    private static final long DEFAULT_CEILING_MINUTES = 15;

    static Stream<String> variants() {
        List<String> only = split(System.getProperty("gametest.only"));
        List<String> exclude = split(System.getProperty("gametest.exclude"));
        List<String> rows = new ArrayList<>();
        for (String variant : FCGT_VARIANTS) {
            if (!only.isEmpty() && !only.contains(variant)) {
                continue;
            }
            if (exclude.contains(variant)) {
                continue;
            }
            rows.add(variant);
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("No FCGT variants selected; check -Dgametest.only/-Dgametest.exclude");
        }
        return rows.stream();
    }

    private static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("variants")
    void clientGametest(String variant) throws Exception {
        List<String> command = List.of(
                GradleFork.gradleScript(),
                ":fabric:" + variant + ":runClientGametest",
                "--console=plain",
                "--no-daemon",
                "--build-cache"
        );
        GradleFork.Result result = GradleFork.runToExit(command, ceilingMillis());

        if (result.exitCode() != 0) {
            Assertions.fail(String.format(
                    "runClientGametest on %s exited with %d.%nCommand: %s%nLast 80 lines:%n%s",
                    variant, result.exitCode(), String.join(" ", command), result.tail(80)));
        }
    }

    private static long ceilingMillis() {
        String raw = System.getProperty("gametest.ceiling.min");
        long minutes = DEFAULT_CEILING_MINUTES;
        if (raw != null && !raw.isBlank()) {
            try {
                long parsed = Long.parseLong(raw.trim());
                if (parsed > 0) {
                    minutes = parsed;
                }
            } catch (NumberFormatException ignored) {
                // A malformed override falls back to the default rather than failing the row.
            }
        }
        return minutes * 60 * 1000;
    }
}
