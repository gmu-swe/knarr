package edu.gmu.swe.knarr.concolic.pilot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;

/**
 * Shared launcher for concolic pilot ITCases. Extracts the knarr-server
 * bootstrap + dual-JDK fork boilerplate so individual pilots only have to
 * name their target class and assert on the forked child's stdout.
 *
 * <p>All paths / JDK homes come from system properties with the same
 * defaults used by {@link edu.gmu.swe.knarr.concolic.ConcolicLoopITCase}
 * — running the pilots under the same {@code -Dcrochet.dualJdk=...}
 * invocation as the loop test Just Works.
 */
public final class PilotHarness {

    private PilotHarness() {}

    /** Holds the started knarr-server subprocess; null if the pilot was skipped. */
    public static final class Server implements AutoCloseable {
        private final Process process;
        Server(Process p) { this.process = p; }

        @Override
        public void close() {
            if (process != null && process.isAlive()) {
                process.destroy();
                try {
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** Start knarr-server on a plain JDK. Skips the caller test via Assumptions if preconditions aren't met. */
    public static Server startServer(Path targetDir) throws Exception {
        Path serverJar = Paths.get(System.getProperty("e2e.serverJar",
                "knarr-server/target/Knarr-Server-0.0.2-SNAPSHOT.jar"));
        if (!Files.isRegularFile(serverJar)) {
            serverJar = Paths.get("..").resolve(serverJar).toAbsolutePath().normalize();
        }
        Assumptions.assumeTrue(Files.isRegularFile(serverJar),
                "knarr-server jar missing: set -De2e.serverJar — tried " + serverJar);
        String z3LibDir = System.getProperty("e2e.z3LibDir",
                Paths.get("..").resolve("z3-4.8.9-x64-ubuntu-16.04/bin")
                        .toAbsolutePath().normalize().toString());
        String plainJavaHome = System.getProperty("e2e.plainJavaHome",
                "/usr/lib/jvm/java-17-openjdk-amd64");
        Files.createDirectories(targetDir);
        ProcessBuilder pb = new ProcessBuilder(
                plainJavaHome + "/bin/java",
                // Deep constraint trees from real-parser pilots overflow
                // Green's recursive readExternal on the server side too.
                // Match the client's stack size.
                "-Xss16m",
                "-jar", serverJar.toString());
        pb.environment().put("LD_LIBRARY_PATH", z3LibDir);
        pb.redirectOutput(targetDir.resolve("knarr-server.out").toFile());
        pb.redirectError(targetDir.resolve("knarr-server.err").toFile());
        Process process = pb.start();
        long deadline = System.currentTimeMillis() + 30_000;
        File outLog = targetDir.resolve("knarr-server.out").toFile();
        while (System.currentTimeMillis() < deadline) {
            if (outLog.exists() && outLog.length() > 0) {
                Thread.sleep(200);
                return new Server(process);
            }
            Thread.sleep(100);
        }
        process.destroyForcibly();
        throw new IllegalStateException("knarr-server did not start within 30s");
    }

    /** Fork the named target class under the dual JDK and return its combined stdout+stderr. */
    public static String runTargetUnderDualJdk(
            String targetClassName, int timeoutSeconds, List<String> extraArgs) throws Exception {
        String dualJdk = System.getProperty("crochet.dualJdk");
        Assumptions.assumeTrue(dualJdk != null && !dualJdk.isEmpty(),
                "Set -Dcrochet.dualJdk to a built dual JDK image to run this pilot");
        Path javaBin = Paths.get(dualJdk, "bin", "java");
        Assumptions.assumeTrue(Files.isExecutable(javaBin),
                "dualJdk java not executable: " + javaBin);

        Path m2 = Paths.get(System.getProperty("user.home"), ".m2", "repository");
        Path galetteAgent = m2.resolve(
                "edu/neu/ccs/prl/galette/galette-agent/1.0.0-SNAPSHOT/galette-agent-1.0.0-SNAPSHOT.jar");
        Path crochetAgent = m2.resolve(
                "net/jonbell/crochet/crochet-agent/1.0.0-SNAPSHOT/crochet-agent-1.0.0-SNAPSHOT.jar");
        Path bridgeAgent = m2.resolve(
                "edu/gmu/swe/knarr/Crochet-Galette-Bridge/0.0.3-SNAPSHOT/Crochet-Galette-Bridge-0.0.3-SNAPSHOT.jar");
        Assumptions.assumeTrue(Files.isRegularFile(galetteAgent), "galette agent missing: " + galetteAgent);
        Assumptions.assumeTrue(Files.isRegularFile(crochetAgent), "crochet agent missing: " + crochetAgent);
        Assumptions.assumeTrue(Files.isRegularFile(bridgeAgent), "bridge agent missing: " + bridgeAgent);

        String childCp = System.getProperty("java.class.path");

        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(javaBin.toString());
        // Real parsers (tar, XML) generate thousands of branches per
        // iteration, producing deep BinaryOperation trees that Green's
        // recursive Externalizable writer can't serialise at the default
        // 512kb stack. 16m is the empirical threshold where the Tar
        // pilot completes without StackOverflowError; bump further only
        // if a bigger target runs out again.
        cmd.add("-Xss16m");
        // Cap the constraint tree so a branch-heavy pilot doesn't blow up
        // the recursive Green serializer. 2000 is empirically enough for
        // tar headers (~512 bytes → hundreds of branches) and well below
        // what a 16m stack can serialise in BinaryOperation.writeExternal.
        cmd.add("-DMAX_CONSTRAINTS=2000");
        cmd.add("--add-reads"); cmd.add("java.base=jdk.unsupported");
        cmd.add("--add-exports"); cmd.add("java.base/jdk.internal.misc=ALL-UNNAMED");
        cmd.add("--add-opens"); cmd.add("java.base/java.lang=ALL-UNNAMED");
        cmd.add("--add-opens"); cmd.add("java.base/edu.neu.ccs.prl.galette.internal.transform=ALL-UNNAMED");
        cmd.add("-Xbootclasspath/a:" + galetteAgent + ":" + crochetAgent);
        cmd.add("-javaagent:" + bridgeAgent);
        cmd.add("-cp"); cmd.add(childCp);
        cmd.add(targetClassName);
        if (extraArgs != null) cmd.addAll(extraArgs);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process child = pb.start();
        boolean done = child.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        String out = new String(child.getInputStream().readAllBytes());
        if (!done) {
            child.destroyForcibly();
            throw new AssertionError("child timed out after " + timeoutSeconds + "s:\n" + out);
        }
        return out;
    }

    /** Persist the child's output under target/ for CI inspection. */
    public static void snapshot(String name, String output) throws IOException {
        Files.writeString(Paths.get("target", name), output);
    }
}
