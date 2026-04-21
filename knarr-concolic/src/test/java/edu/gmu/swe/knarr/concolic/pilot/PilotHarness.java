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

        // waitFor(long, TimeUnit) has been observed to miss its deadline
        // and park indefinitely when the child is 100% CPU-bound under
        // heavy Galette instrumentation (observed multiple times during
        // pilot batch: waitFor stayed in AQS.awaitNanos for 3600s+ past
        // a 1800s deadline, jstack'd in LockSupport.parkNanos). Work
        // around it with a belt-and-suspenders watchdog: an independent
        // daemon thread that sleeps for the full timeout and then SIGKILLs
        // the child PID via `kill -9` from the OS shell, NOT via
        // Process.destroyForcibly (which itself can fail to propagate if
        // the JVM is wedged). After the watchdog fires, the child dies,
        // waitFor observes the exit and returns. We give waitFor an extra
        // 60s grace past the nominal deadline to account for the time
        // between kill and SIGCHLD delivery.
        final long childPid = child.pid();
        final int hardDeadline = timeoutSeconds;
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(hardDeadline * 1000L);
            } catch (InterruptedException e) {
                return;
            }
            if (!child.isAlive()) return;
            // Try destroyForcibly first — if the JVM's SIGCHLD plumbing
            // is healthy this is the clean path.
            child.destroyForcibly();
            // Belt-and-suspenders: shell out to `kill -9` directly so the
            // child dies even if destroyForcibly is itself wedged. We
            // ignore the exit code — kill may report ESRCH if the process
            // already died from destroyForcibly a microsecond earlier.
            try {
                new ProcessBuilder("kill", "-9", String.valueOf(childPid))
                        .redirectErrorStream(true)
                        .start()
                        .waitFor(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }, "pilot-watchdog-" + childPid);
        watchdog.setDaemon(true);
        watchdog.start();

        boolean done;
        try {
            done = child.waitFor(timeoutSeconds + 60L, TimeUnit.SECONDS);
        } finally {
            watchdog.interrupt();
        }
        String out = new String(child.getInputStream().readAllBytes());
        if (!done) {
            // Grace period past the watchdog's SIGKILL elapsed and the
            // child STILL shows alive — at this point the kernel is the
            // problem, not us. Force-kill again and surface the error.
            child.destroyForcibly();
            throw new AssertionError("child timed out after " + timeoutSeconds
                    + "s (+60s grace): still alive past watchdog SIGKILL. Output:\n" + out);
        }
        return out;
    }

    /** Persist the child's output under target/ for CI inspection. */
    public static void snapshot(String name, String output) throws IOException {
        Files.writeString(Paths.get("target", name), output);
    }

    /** Ensure {@code target/} exists before writing anything into it. */
    public static void ensureTargetDir() throws IOException {
        Files.createDirectories(Paths.get("target"));
    }

    /** Summary of one pilot fork's output. */
    public static final class RunResult {
        public boolean started;
        public boolean finished;
        public int iters;
        public int uniqueOutcomes;
        public int uniqueBranches;
        @Override
        public String toString() {
            return "{iters=" + iters
                    + " unique_outcomes=" + uniqueOutcomes
                    + " unique_branches=" + uniqueBranches + "}";
        }
    }

    /** Parse the per-pilot output protocol into a {@link RunResult}. */
    public static RunResult parseRun(String linePrefix, String output) {
        RunResult r = new RunResult();
        r.started = output.contains(linePrefix + "_PILOT_STARTED");
        int itersSeen = 0;
        int totalBranches = 0;
        java.util.Set<String> outcomes = new java.util.LinkedHashSet<>();
        for (String line : output.split("\n")) {
            if (line.startsWith(linePrefix + "_BRANCHES_HIT")) {
                itersSeen++;
                // total=<N>
                int idx = line.indexOf("total=");
                if (idx >= 0) {
                    int end = line.indexOf(' ', idx);
                    if (end < 0) end = line.length();
                    try {
                        totalBranches = Math.max(totalBranches,
                                Integer.parseInt(line.substring(idx + 6, end)));
                    } catch (NumberFormatException ignored) {}
                }
            } else if (line.startsWith(linePrefix + "_OUTCOME ")) {
                outcomes.add(line.substring((linePrefix + "_OUTCOME ").length()).trim());
            } else if (line.startsWith(linePrefix + "_PILOT_FINISHED")) {
                r.finished = true;
                // Reconcile with the summary line (authoritative).
                int b = line.indexOf("branches=");
                if (b >= 0) {
                    int end = line.indexOf(' ', b);
                    if (end < 0) end = line.length();
                    try {
                        totalBranches = Integer.parseInt(line.substring(b + 9, end));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        r.iters = itersSeen;
        r.uniqueOutcomes = outcomes.size();
        r.uniqueBranches = totalBranches;
        return r;
    }

    /** Write a human-readable results table for the "three mutators" comparison. */
    public static void writeTable(String name, java.util.Map<String, RunResult> results) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-8s %6s %18s %18s%n",
                "mutator", "iters", "unique_outcomes", "unique_branches"));
        for (java.util.Map.Entry<String, RunResult> e : results.entrySet()) {
            RunResult r = e.getValue();
            sb.append(String.format("%-8s %6d %18d %18d%n",
                    e.getKey(), r.iters, r.uniqueOutcomes, r.uniqueBranches));
        }
        Files.writeString(Paths.get("target", name), sb.toString());
        System.out.println(sb);
    }
}
