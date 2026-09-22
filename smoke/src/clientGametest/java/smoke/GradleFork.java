package smoke;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Forks {@code ./gradlew} as a child process, mirrors its combined output to this JVM's stdout and
 * returns the captured lines alongside an exit code.
 *
 * <p>FCGT calls {@code System.exit} once the filtered gametests return, so the child's own exit
 * code is meaningful and this helper only has to wait for it — with a hard wall-clock ceiling,
 * because a fabric-loader popup or a stalled boot leaves stdout open forever and a blocking
 * {@code readLine()} on the main thread would never reach {@code waitFor()}. Hence the reader
 * thread plus the polling loop.
 */
final class GradleFork {

    /** Repo root, forwarded by the Gradle test task (same property the plain-JVM suite uses). */
    private static final String REPO_ROOT_PROPERTY = "smoke.repo.root";
    private static final long POLL_INTERVAL_MS = 250;
    private static final long READER_JOIN_MS = 2_000;
    private static final long TREE_EXIT_WAIT_MS = 5_000;

    /** Exit code synthesised when the child is killed for exceeding its wall-clock ceiling. */
    static final int TIMEOUT_EXIT_CODE = 124;

    /**
     * Every forked {@code ./gradlew} still alive, so a shutdown hook can reap their whole process
     * trees if THIS JVM is interrupted mid-row. Otherwise the multi-GB Minecraft JVM orphans
     * alongside its Gradle parent.
     */
    private static final Set<Process> LIVE_FORKS = ConcurrentHashMap.newKeySet();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Process fork : LIVE_FORKS) {
                if (fork.isAlive()) {
                    destroyTree(fork);
                }
            }
        }, "gametest-fork-reaper"));
    }

    private GradleFork() {
    }

    /** Waits for the child to exit on its own, killing its process tree once {@code ceilingMs} elapsed. */
    static Result runToExit(List<String> command, long ceilingMs) throws IOException, InterruptedException {
        File workingDirectory = repoRoot().toFile();
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory)
                .redirectErrorStream(true)
                .start();
        LIVE_FORKS.add(process);
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        Thread reader = startReader(process, lines);

        long startedAt = System.currentTimeMillis();
        while (true) {
            if (!process.isAlive()) {
                LIVE_FORKS.remove(process);
                reader.join(READER_JOIN_MS);
                return new Result(process.exitValue(), lines);
            }
            if (System.currentTimeMillis() - startedAt > ceilingMs) {
                destroyTree(process);
                LIVE_FORKS.remove(process);
                reader.join(READER_JOIN_MS);
                return new Result(TIMEOUT_EXIT_CODE, lines);
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
    }

    private static Thread startReader(Process process, List<String> lines) {
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = in.readLine()) != null) {
                    lines.add(line);
                    System.out.println(line);
                }
            } catch (IOException ignored) {
                // Pipe closed — normally because the child was just destroyed above.
            }
        }, "gametest-stdout-reader");
        reader.setDaemon(true);
        reader.start();
        return reader;
    }

    /**
     * The Gradle wrapper for this platform, as an ABSOLUTE path. {@link ProcessBuilder} resolves a
     * relative program name against the JVM's own working directory, never against
     * {@link ProcessBuilder#directory(File)}, and the test worker runs from {@code smoke/}.
     */
    static String gradleScript() {
        String script = isWindows() ? "gradlew.bat" : "gradlew";
        return repoRoot().resolve(script).toAbsolutePath().toString();
    }

    /** Repository root, forwarded by the Gradle test task, or discovered by walking upwards. */
    static Path repoRoot() {
        String property = System.getProperty(REPO_ROOT_PROPERTY);
        if (property != null && !property.isBlank()) {
            return Paths.get(property);
        }
        Path here = Paths.get("").toAbsolutePath();
        for (Path candidate = here; candidate != null; candidate = candidate.getParent()) {
            if (candidate.resolve("gradlew").toFile().isFile()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not locate repo root: set -D" + REPO_ROOT_PROPERTY);
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * Forcibly destroys {@code process} AND every descendant. The Minecraft JVM is a descendant of
     * the forked {@code ./gradlew}, so destroying only the top process orphans it. Descendants are
     * snapshotted BEFORE the root dies, because its children are reparented to init the moment it
     * exits and drop off {@link Process#descendants()}.
     */
    private static void destroyTree(Process process) {
        List<ProcessHandle> tree = new ArrayList<>();
        process.descendants().forEach(tree::add);
        tree.add(process.toHandle());
        for (ProcessHandle handle : tree) {
            handle.destroyForcibly();
        }
        for (ProcessHandle handle : tree) {
            try {
                handle.onExit().get(TREE_EXIT_WAIT_MS, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                // Best-effort: the destroyForcibly signal was already delivered above.
            }
        }
    }

    /** Exit code plus every captured output line of one Gradle invocation. */
    record Result(int exitCode, List<String> lines) {
        String tail(int count) {
            int from = Math.max(0, lines.size() - count);
            return String.join("\n", lines.subList(from, lines.size()));
        }
    }
}
