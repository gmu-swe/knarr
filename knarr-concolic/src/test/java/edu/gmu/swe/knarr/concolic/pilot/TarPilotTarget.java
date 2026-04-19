package edu.gmu.swe.knarr.concolic.pilot;

import edu.gmu.swe.knarr.runtime.PathConstraintListener;
import edu.gmu.swe.knarr.runtime.Symbolicator;
import edu.neu.ccs.prl.galette.internal.runtime.symbolic.SymbolicListener;
import java.io.ByteArrayInputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import net.jonbell.crochet.runtime.CheckpointRollbackAgent;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Pilot 1 target: feed a symbolic 512-byte buffer (one Tar header block)
 * into {@link TarArchiveInputStream#getNextEntry()} and report distinct
 * branch outcomes across a short concolic loop.
 *
 * <p>The target does NOT use {@link edu.gmu.swe.knarr.concolic.ConcolicDriver}'s
 * solver round-trip. Green's Z3 translator currently throws on byte-array
 * store ({@code BitVec 8 vs BitVec 32} sort mismatch) for the sort of
 * constraint shape tar-header parsing produces, so asking the solver for
 * a model would fail every iteration and drag the wall-clock out. What
 * this pilot proves is narrower but real:
 *
 * <ul>
 *   <li>Dual-instrumented JDK runs {@code commons-compress} without crash.</li>
 *   <li>{@link Symbolicator#symbolic(String, byte[])} tags propagate through
 *       the parser's header reads.</li>
 *   <li>{@link PathConstraintListener} fires during parse branches (we
 *       install it even though we don't dump the constraints — it exercises
 *       the listener SPI under real load).</li>
 *   <li>{@link CheckpointRollbackAgent#checkpointAll()} /
 *       {@link CheckpointRollbackAgent#rollbackAll(int)} survive multiple
 *       iterations over a non-trivial parser's heap / statics.</li>
 * </ul>
 *
 * <p>Mutation is structural: flip one random byte per iteration. Enough
 * to drive the parser into distinct branch outcomes without needing a
 * solver model; the assertion in the ITCase just checks that more than
 * one outcome was observed.
 */
public final class TarPilotTarget {

    static final int HEADER_SIZE = 512;
    static final int ITERATIONS = 10;
    static final Set<String> outcomes = new LinkedHashSet<>();

    public static void main(String[] args) {
        System.out.println("TAR_PILOT_STARTED");
        SymbolicListener.setListener(new PathConstraintListener());
        Symbolicator.reset();

        byte[] buf = new byte[HEADER_SIZE];
        byte[] magic = "ustar\0".getBytes();
        System.arraycopy(magic, 0, buf, 257, magic.length);

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
                // Target specific tar-header fields so each iteration
                // perturbs a decision point the parser actually branches
                // on. A uniform flip pattern misses the first 30 name-
                // field bytes every iter (the name is null-terminated,
                // so with byte 0 untouched, bucket is always zero). The
                // loop counter {@code i} is a local variable and so
                // survives CROCHET's rollback; {@link java.util.Random}
                // does not.
                int[] targets = new int[]{
                        i % 8,                 // name field byte 0-7
                        8 + (i * 7) % 20,      // name field byte 8-27
                        100 + (i % 8),         // mode
                        148 + (i % 8),         // checksum
                        156 + (i % 1),         // typeflag
                        257 + (i % 6),         // ustar magic
                        265 + (i % 2),         // ustar version
                };
                for (int j = 0; j < targets.length; j++) {
                    buf[targets[j]] = (byte) (((i + 1) * 97) ^ (j * 211));
                }
            }
        } finally {
            SymbolicListener.setListener(null);
        }

        for (String o : outcomes) {
            System.out.println("TAR_OUTCOME " + o);
        }
        System.out.println("TAR_PILOT_FINISHED outcomes=" + outcomes.size());
        System.out.flush();
        // Force exit: Galette / CROCHET / knarr runtime may leave a
        // non-daemon thread alive past main().
        System.exit(0);
    }

    static void body(byte[] input) {
        byte[] tagged = Symbolicator.symbolic("tar", input);
        try (TarArchiveInputStream tin = new TarArchiveInputStream(
                new ByteArrayInputStream(tagged))) {
            Object entry = tin.getNextEntry();
            if (entry == null) {
                outcomes.add("NULL_ENTRY");
            } else {
                outcomes.add("ENTRY_READ bucket=" + signalLen(entry));
            }
        } catch (Throwable t) {
            outcomes.add("EX " + t.getClass().getSimpleName());
        }
    }

    private static int signalLen(Object entry) {
        try {
            String name = entry.getClass()
                    .getMethod("getName").invoke(entry).toString();
            int len = name.length();
            if (len == 0) return 0;
            if (len < 8) return 1;
            if (len < 64) return 2;
            return 3;
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }
}
