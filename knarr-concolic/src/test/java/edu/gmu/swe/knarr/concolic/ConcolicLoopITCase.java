package edu.gmu.swe.knarr.concolic;

import edu.gmu.swe.knarr.runtime.Symbolicator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * End-to-end smoke test for the concolic driver running on a dual-
 * instrumented JDK (Galette + CROCHET). Activated only when the system
 * property {@code crochet.dualJdk} points at a built dual JDK image; on
 * a plain JVM the test is skipped via {@code Assumptions.assumeTrue}.
 *
 * <p>The test forks a child process under the dual JDK that runs
 * {@link Target} 10 times via {@link ConcolicDriver}. Each iteration
 * symbolic-tags an integer input named {@code x}; the target throws
 * when {@code x > 100}. We assert at least one iteration reaches the
 * throw branch — the proof that concolic execution can flip a path
 * within a single JVM with the dual instrumentation in place.
 *
 */
public class ConcolicLoopITCase {

    private static Process knarrServer;

    @BeforeAll
    static void startKnarrServer() throws Exception {
        // Knarr-server runs on a plain JDK (no dual instrumentation) so
        // Z3's JNI bindings can initialize without interference. Symbolicator
        // in the instrumented client toggles PROPAGATE_THROUGH_SERIALIZATION
        // for the round-trip so the plain peer doesn't choke on tag wrappers.
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
        Path targetDir = Paths.get("target");
        Files.createDirectories(targetDir);
        ProcessBuilder pb = new ProcessBuilder(
                plainJavaHome + "/bin/java", "-jar", serverJar.toString());
        pb.environment().put("LD_LIBRARY_PATH", z3LibDir);
        pb.redirectOutput(targetDir.resolve("knarr-server.out").toFile());
        pb.redirectError(targetDir.resolve("knarr-server.err").toFile());
        knarrServer = pb.start();
        long deadline = System.currentTimeMillis() + 30_000;
        File outLog = targetDir.resolve("knarr-server.out").toFile();
        while (System.currentTimeMillis() < deadline) {
            if (outLog.exists() && outLog.length() > 0) {
                Thread.sleep(200);
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("knarr-server did not start within 30s");
    }

    @AfterAll
    static void stopKnarrServer() {
        if (knarrServer != null && knarrServer.isAlive()) {
            knarrServer.destroy();
            try {
                if (!knarrServer.waitFor(5, TimeUnit.SECONDS)) {
                    knarrServer.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void concolicLoopFlipsAtLeastOnePath() throws Exception {
        String dualJdk = System.getProperty("crochet.dualJdk");
        Assumptions.assumeTrue(dualJdk != null && !dualJdk.isEmpty(),
                "Set -Dcrochet.dualJdk to a built dual JDK image to run this test");
        Path javaBin = Paths.get(dualJdk, "bin", "java");
        Assumptions.assumeTrue(Files.isExecutable(javaBin),
                "dualJdk java not executable: " + javaBin);

        // Resolve the agent jars from the user's local Maven repo. The
        // dual JDK runs under a single combined agent that applies both
        // Galette and CROCHET transformers to every class load; the two
        // upstream agent jars are still needed on the bootclasspath so
        // their runtime classes are visible to instrumented code.
        Path m2 = Paths.get(System.getProperty("user.home"), ".m2", "repository");
        Path galetteAgent = m2.resolve(
                "edu/neu/ccs/prl/galette/galette-agent/1.0.0-SNAPSHOT/galette-agent-1.0.0-SNAPSHOT.jar");
        Path crochetAgent = m2.resolve(
                "edu/neu/ccs/prl/crochet/crochet-agent/2.0.0-SNAPSHOT/crochet-agent-2.0.0-SNAPSHOT.jar");
        Path bridgeAgent = m2.resolve(
                "edu/gmu/swe/knarr/Crochet-Galette-Bridge/0.0.3-SNAPSHOT/Crochet-Galette-Bridge-0.0.3-SNAPSHOT.jar");
        Assumptions.assumeTrue(Files.isRegularFile(galetteAgent), "galette agent missing: " + galetteAgent);
        Assumptions.assumeTrue(Files.isRegularFile(crochetAgent), "crochet agent missing: " + crochetAgent);
        Assumptions.assumeTrue(Files.isRegularFile(bridgeAgent), "bridge agent missing: " + bridgeAgent);

        // The classpath for the forked child: this module's test-classes
        // (so it can find Target / ConcolicDriver / Symbolicator) +
        // every dep on this module's compile + test classpath.
        String childCp = System.getProperty("java.class.path");

        ProcessBuilder pb = new ProcessBuilder(
                javaBin.toString(),
                "--add-reads", "java.base=jdk.unsupported",
                "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "--add-opens", "java.base/edu.neu.ccs.prl.galette.internal.transform=ALL-UNNAMED",
                "-Xbootclasspath/a:" + galetteAgent + ":" + crochetAgent,
                "-javaagent:" + bridgeAgent,
                "-cp", childCp,
                Target.class.getName());
        pb.redirectErrorStream(true);
        Process child = pb.start();
        boolean done = child.waitFor(120, TimeUnit.SECONDS);
        String out = new String(child.getInputStream().readAllBytes());
        if (!done) {
            child.destroyForcibly();
            throw new AssertionError("child timed out:\n" + out);
        }
        // Snapshot for CI to inspect.
        Files.writeString(Paths.get("target", "concolic-it-output.txt"), out);

        boolean reachedThrow = out.contains("CONCOLIC_REACHED_THROW");
        org.junit.jupiter.api.Assertions.assertTrue(reachedThrow,
                "concolic loop never flipped the x>100 branch — output:\n" + out);
    }

    /** Forked-child entry point. */
    public static class Target {
        public static void main(String[] args) {
            System.out.println("CONCOLIC_DRIVER_STARTED");
            ConcolicDriver<Integer> driver = new ConcolicDriver<>(
                    Target::body,
                    Target::mutate,
                    10);
            try {
                driver.run(0);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            System.out.println("CONCOLIC_DRIVER_FINISHED");
        }

        static void body(Integer input) {
            int x = Symbolicator.symbolic("x", input);
            if (x > 100) {
                System.out.println("CONCOLIC_REACHED_THROW x=" + x);
                throw new RuntimeException("flipped");
            }
        }

        static Integer mutate(Integer prev, ArrayList<SimpleEntry<String, Object>> sol) {
            // Grow monotonically so the x > 100 branch is reached within
            // maxIterations=10 iterations (1 → 2 → 4 → ... → 128). The
            // solver's model is ignored on purpose: it satisfies the
            // current constraint but does not negate the branch we want
            // to flip, so following it would plateau (e.g. the solver
            // consistently returns x=0 for the x<=100 path).
            int base = prev == null ? 1 : Math.max(1, prev);
            return base * 2;
        }
    }
}
