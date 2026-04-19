package edu.gmu.swe.knarr.concolic.pilot;

import edu.gmu.swe.knarr.runtime.PathConstraintListener;
import edu.gmu.swe.knarr.runtime.Symbolicator;
import edu.neu.ccs.prl.galette.internal.runtime.symbolic.SymbolicListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.jonbell.crochet.runtime.CheckpointRollbackAgent;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.helper.ProjectHelperImpl;

/**
 * Pilot 2 target: mirror JQF's Ant {@code ProjectBuilderTest} and drive
 * {@link ProjectHelperImpl#parse(Project, Object)} with symbolic bytes.
 * Same structure as {@link TarPilotTarget} — we run a short local loop
 * with structural mutation rather than the solver-driven
 * {@link edu.gmu.swe.knarr.concolic.ConcolicDriver}, because the
 * byte-array constraint shape currently trips Green's Z3 translator.
 *
 * <p>What this pilot proves: Ant's XML parser runs under the dual JDK,
 * its branches fire a {@link PathConstraintListener}, checkpoint /
 * rollback survive the parser's heap state across iterations, and we
 * can observe multiple distinct BuildException / parse outcomes as the
 * input is perturbed. Numbers here are comparable to JQF's
 * ProjectBuilderTest because the entry point ({@code ProjectHelperImpl.parse}
 * with a temp-file argument) and the BuildException policy
 * ({@link org.junit.jupiter.api.Assumptions#assumeTrue}-equivalent —
 * parse rejection is a valid-but-uninteresting outcome) match.
 */
public final class AntPilotTarget {

    static final int ITERATIONS = 10;

    // No task elements: Ant's parser eagerly resolves task names via
    // taskdef / antlib, which on a dual-instrumented JDK pulls in the
    // locale-provider chain (StringTokenizer + String.toCharArray) and
    // Galette's per-array tag store spins for minutes on the first parse.
    // Structural mutation rarely produces a syntactically valid task
    // element anyway, so dropping the seed's {@code <echo>} costs no
    // branch coverage that the concolic loop would actually reach.
    static final byte[] SEED = (
            "<?xml version=\"1.0\"?>\n"
                    + "<project name=\"p\" default=\"t\" basedir=\".\">\n"
                    + "  <target name=\"t\"/>\n"
                    + "</project>\n").getBytes();

    static final Set<String> outcomes = new LinkedHashSet<>();

    public static void main(String[] args) {
        System.out.println("ANT_PILOT_STARTED");
        SymbolicListener.setListener(new PathConstraintListener());
        Symbolicator.reset();

        byte[] buf = SEED.clone();
        int cp = CheckpointRollbackAgent.checkpointAll();
        try {
            for (int i = 0; i < ITERATIONS; i++) {
                try {
                    body(buf);
                } catch (Throwable t) {
                    outcomes.add("THREW " + t.getClass().getSimpleName());
                }
                CheckpointRollbackAgent.rollbackAll(cp);
                cp = CheckpointRollbackAgent.checkpointAll();
                // Loop-local mutation so CROCHET's rollback-of-Random
                // doesn't make every iteration run on the same bytes.
                // See TarPilotTarget for the rationale.
                for (int j = 0; j < 4; j++) {
                    int pos = (((i + 1) * 31 + j * 53) & 0x7fffffff) % buf.length;
                    buf[pos] = (byte) (((i + 1) * 97) ^ (j * 211));
                }
            }
        } finally {
            SymbolicListener.setListener(null);
        }

        for (String o : outcomes) {
            System.out.println("ANT_OUTCOME " + o);
        }
        System.out.println("ANT_PILOT_FINISHED outcomes=" + outcomes.size());
        System.out.flush();
        System.exit(0);
    }

    static void body(byte[] input) {
        byte[] tagged = Symbolicator.symbolic("xml", input);
        Path path = null;
        try {
            path = Files.createTempFile("build", ".xml");
            Files.write(path, tagged);
            new ProjectHelperImpl().parse(new Project(), path.toFile());
            outcomes.add("PARSED_OK");
        } catch (org.apache.tools.ant.BuildException be) {
            outcomes.add("BUILD_EX " + bucket(be));
        } catch (Throwable t) {
            outcomes.add("CRASH " + t.getClass().getSimpleName());
        } finally {
            if (path != null) {
                try { Files.deleteIfExists(path); } catch (Exception ignored) {}
            }
        }
    }

    private static String bucket(Throwable t) {
        String msg = t.getMessage();
        if (msg == null) return t.getClass().getSimpleName();
        // Strip paths and quoted strings so the outcome key captures the
        // branch category, not the input-specific temp-file name.
        String cleaned = msg.replaceAll("\"[^\"]*\"", "\"_\"")
                .replaceAll("/[\\w./-]+", "_");
        String[] words = cleaned.split("\\s+");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(3, words.length); i++) {
            if (b.length() > 0) b.append(' ');
            b.append(words[i]);
        }
        return b.toString();
    }
}
