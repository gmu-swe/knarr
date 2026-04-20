package edu.gmu.swe.knarr.concolic.pilot;

import edu.gmu.swe.knarr.runtime.PathConstraintListener;
import edu.gmu.swe.knarr.runtime.PathUtils;
import edu.gmu.swe.knarr.runtime.Symbolicator;
import edu.neu.ccs.prl.galette.internal.runtime.symbolic.SymbolicListener;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import net.jonbell.crochet.runtime.CheckpointRollbackAgent;
import za.ac.sun.cs.green.expr.BinaryOperation;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation.Operator;

/**
 * Shared pilot iteration driver. Both the Tar and Ant pilots run the same
 * checkpoint/rollback loop with one of three mutation strategies:
 *
 * <ul>
 *   <li>{@code struct} — deterministic loop-counter-derived mutation.
 *       This is the baseline — it does not use the solver at all.</li>
 *   <li>{@code random} — {@link System#nanoTime()}-seeded random byte
 *       flips. {@code nanoTime} is host-clock derived (not heap state),
 *       so CROCHET's rollback doesn't reset it. Gives a structural-
 *       exploration baseline that doesn't benefit from the solver.</li>
 *   <li>{@code solver} — round-trip the path condition through
 *       {@link Symbolicator#dumpConstraints(String)} and pull the next
 *       byte values from the returned model's per-byte BVVariable entries
 *       ({@code <label>_b<i>} → {@code Integer}). Falls back to
 *       {@code struct} mutation when the server returns an empty / no-
 *       novelty solution.</li>
 * </ul>
 *
 * <p>Each iteration prints a {@code BRANCHES_HIT} line with the per-
 * iteration count, the cumulative distinct-branch set size, and the
 * outcome bucket. "Branches" here is the set of distinct constraint
 * signatures (leaf Operations in the PC tree) — a close proxy for
 * distinct taint-observing branch sites in the parser bytecode.
 */
public final class PilotRunner {

    /** One iteration: run the parser over {@code tagged} and return an outcome bucket. */
    public interface IterationBody {
        String run(byte[] tagged);
    }

    public enum Mutator { STRUCT, RANDOM, SOLVER, CONCOLIC }

    private final String tagLabel;
    private final String linePrefix; // "TAR" or "ANT"
    private final IterationBody body;
    private final int iterations;
    private final Mutator mutator;
    private final byte[] seed;

    /** Indices of branches already flipped in this session (avoid repeats). */
    private final java.util.LinkedHashSet<Integer> flippedBranches = new java.util.LinkedHashSet<>();
    /** Next branch index the concolic driver will attempt. Round-robin. */
    private int nextConcolicTarget = 0;

    public PilotRunner(String linePrefix, String tagLabel, byte[] seed,
                       IterationBody body, int iterations, Mutator mutator) {
        this.linePrefix = linePrefix;
        this.tagLabel = tagLabel;
        this.seed = seed;
        this.body = body;
        this.iterations = iterations;
        this.mutator = mutator;
    }

    public static Mutator parseMutator(String[] args) {
        for (String a : args) {
            if (a.startsWith("--mutator=")) {
                String v = a.substring("--mutator=".length()).toUpperCase();
                return Mutator.valueOf(v);
            }
        }
        return Mutator.STRUCT;
    }

    public void run() {
        System.out.println(linePrefix + "_PILOT_STARTED mutator=" + mutator);
        SymbolicListener.setListener(new PathConstraintListener());
        Symbolicator.reset();

        byte[] buf = seed.clone();
        Set<String> allOutcomes = new LinkedHashSet<>();
        Set<String> cumulativeBranches = new HashSet<>();

        int cp = CheckpointRollbackAgent.checkpointAll();
        try {
            for (int i = 0; i < iterations; i++) {
                String outcome;
                byte[] tagged;
                try {
                    // Clone buf so Symbolicator-tagged bytes don't leak into
                    // the next iteration's starting array after rollback.
                    tagged = Symbolicator.symbolic(tagLabel, buf.clone());
                } catch (Throwable t) {
                    outcome = "TAG_FAIL " + t.getClass().getSimpleName();
                    allOutcomes.add(outcome);
                    System.out.println(linePrefix + "_OUTCOME_ITER iter=" + i + " " + outcome);
                    CheckpointRollbackAgent.rollbackAll(cp);
                    cp = CheckpointRollbackAgent.checkpointAll();
                    continue;
                }

                try {
                    outcome = body.run(tagged);
                    if (outcome == null) outcome = "NULL";
                } catch (Throwable t) {
                    outcome = "THREW " + t.getClass().getSimpleName();
                }
                allOutcomes.add(outcome);

                int iterBranches = harvestBranches(cumulativeBranches);

                // Determine the next input based on the mutator.
                byte[] next;
                if (mutator == Mutator.SOLVER) {
                    next = solverMutate(buf, i);
                    if (next == null || sameBytes(next, buf)) {
                        next = structMutate(buf, i);
                    }
                } else if (mutator == Mutator.CONCOLIC) {
                    next = concolicMutate(buf, i);
                    if (next == null || sameBytes(next, buf)) {
                        next = structMutate(buf, i);
                    }
                } else if (mutator == Mutator.RANDOM) {
                    next = randomMutate(buf, i);
                } else {
                    next = structMutate(buf, i);
                }

                System.out.println(linePrefix + "_BRANCHES_HIT iter=" + i
                        + " count=" + iterBranches
                        + " total=" + cumulativeBranches.size()
                        + " outcome=" + outcome);

                // Rollback before next iter so parser heap is clean.
                Symbolicator.reset();
                CheckpointRollbackAgent.rollbackAll(cp);
                cp = CheckpointRollbackAgent.checkpointAll();

                buf = next;
            }
        } finally {
            SymbolicListener.setListener(null);
        }

        for (String o : allOutcomes) {
            System.out.println(linePrefix + "_OUTCOME " + o);
        }
        System.out.println(linePrefix + "_PILOT_FINISHED"
                + " mutator=" + mutator
                + " iters=" + iterations
                + " outcomes=" + allOutcomes.size()
                + " branches=" + cumulativeBranches.size());
        System.out.flush();
        System.exit(0);
    }

    /** Walk the current PC's AND-spine, add each leaf's signature to {@code cumulative}, and return this iteration's leaf count. */
    private static int harvestBranches(Set<String> cumulative) {
        Expression root = PathUtils.getCurPC().constraints;
        if (root == null) return 0;
        int count = 0;
        java.util.ArrayDeque<Expression> stack = new java.util.ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Expression e = stack.pop();
            if (e instanceof BinaryOperation) {
                BinaryOperation b = (BinaryOperation) e;
                if (b.getOperator() == Operator.AND) {
                    java.util.Iterator<Expression> it = b.getOperands().iterator();
                    while (it.hasNext()) stack.push(it.next());
                    continue;
                }
            }
            count++;
            String sig;
            try {
                sig = e.toString();
                if (sig.length() > 600) {
                    // Long string representations are mostly deep anchor
                    // arithmetic; use a hash over the prefix to keep
                    // memory bounded while staying path-distinctive.
                    sig = Integer.toHexString(sig.hashCode()) + ":" + sig.substring(0, 120);
                }
            } catch (Throwable t) {
                sig = "ERR:" + System.identityHashCode(e);
            }
            cumulative.add(sig);
        }
        return count;
    }

    /** Loop-counter-derived mutation — the original pilot baseline. */
    private byte[] structMutate(byte[] buf, int i) {
        byte[] out = buf.clone();
        // Generic version of the tar-targets pattern: perturb several
        // positions deterministically by loop counter. Pilot-specific
        // targets (header-field offsets) are expressed by the caller
        // by seeding a suitable buf; here we pick spread-out offsets.
        int len = out.length;
        int[] offsets = new int[]{
                i % len,
                (i * 7 + 1) % len,
                (i * 13 + 3) % len,
                (i * 23 + 5) % len,
                (i * 41 + 7) % len,
                (i * 61 + 11) % len,
                (i * 97 + 13) % len,
        };
        for (int j = 0; j < offsets.length; j++) {
            out[offsets[j]] = (byte) (((i + 1) * 97) ^ (j * 211));
        }
        return out;
    }

    /** nanoTime-seeded random flips — not rolled back by CROCHET (clock is host-state). */
    private byte[] randomMutate(byte[] buf, int i) {
        byte[] out = buf.clone();
        long nano = System.nanoTime();
        // Derive a short stream of bytes from nanoTime + iter. Local
        // arithmetic only — no heap objects (CROCHET would reset Random).
        long s = nano ^ (0x9E3779B97F4A7C15L * (i + 1));
        int n = 8;
        for (int j = 0; j < n; j++) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            int pos = (int) ((s >>> 33) & 0x7fffffff) % out.length;
            byte v = (byte) ((s >>> 25) & 0xff);
            out[pos] = v;
        }
        return out;
    }

    /**
     * Concolic (branch-negating) mutator. Picks the next un-flipped branch
     * index from the accumulated path condition, asks the solver for an
     * input that satisfies the flipped tree {@code anchors ∧ b0 ∧ ... ∧
     * b(i-1) ∧ NOT(bi)}, and applies the model bytes to the next input.
     *
     * <p>Falls back to null on UNSAT / empty model (caller then uses
     * structural mutation as a failsafe). Round-robins through branch
     * indices that haven't been flipped yet, so successive iters try
     * successive branches rather than hammering the same one.
     */
    private byte[] concolicMutate(byte[] buf, int iter) {
        int branchCount = PathUtils.getCurPC().branchCount();
        if (branchCount == 0) {
            System.out.println(linePrefix + "_CONCOLIC_SKIP iter=" + iter + " reason=no_branches");
            return null;
        }
        // Pick the next unflipped branch index. Start from nextConcolicTarget
        // and walk forward (wrapping) until we find one not yet flipped.
        int chosen = -1;
        for (int k = 0; k < branchCount; k++) {
            int cand = (nextConcolicTarget + k) % branchCount;
            if (!flippedBranches.contains(cand)) {
                chosen = cand;
                break;
            }
        }
        if (chosen < 0) {
            // All branches have been flipped at least once this session;
            // loop back to index 0 and flip again.
            flippedBranches.clear();
            chosen = 0;
        }
        flippedBranches.add(chosen);
        nextConcolicTarget = (chosen + 1) % branchCount;

        System.out.println(linePrefix + "_CONCOLIC_FLIP iter=" + iter
                + " branch_idx=" + chosen
                + " branch_total=" + branchCount);

        ArrayList<SimpleEntry<String, Object>> soln;
        try {
            soln = Symbolicator.dumpConstraintsForFlip(chosen);
        } catch (Throwable t) {
            System.out.println(linePrefix + "_CONCOLIC_EX iter=" + iter
                    + " idx=" + chosen + " ex=" + t.getClass().getSimpleName());
            return null;
        }
        if (soln == null || soln.isEmpty()) {
            System.out.println(linePrefix + "_CONCOLIC_UNSAT iter=" + iter + " idx=" + chosen);
            return null;
        }
        byte[] out = buf.clone();
        String prefix = tagLabel + "_b";
        int applied = 0;
        for (SimpleEntry<String, Object> e : soln) {
            String k = e.getKey();
            if (!k.startsWith(prefix)) continue;
            String idxStr = k.substring(prefix.length());
            int idx;
            try {
                idx = Integer.parseInt(idxStr);
            } catch (NumberFormatException nfe) {
                continue;
            }
            if (idx < 0 || idx >= out.length) continue;
            Object v = e.getValue();
            if (v instanceof Integer) {
                out[idx] = ((Integer) v).byteValue();
                applied++;
            } else if (v instanceof Number) {
                out[idx] = ((Number) v).byteValue();
                applied++;
            }
        }
        System.out.println(linePrefix + "_CONCOLIC_APPLIED iter=" + iter
                + " idx=" + chosen
                + " soln_size=" + soln.size()
                + " bytes_applied=" + applied);
        if (applied == 0) return null;
        return out;
    }

    /** Pull the next input from the solver's model of the accumulated PC. Returns null if solver had nothing useful. */
    private byte[] solverMutate(byte[] buf, int iter) {
        ArrayList<SimpleEntry<String, Object>> soln;
        try {
            soln = Symbolicator.dumpConstraints(null);
        } catch (Throwable t) {
            return null;
        }
        if (soln == null || soln.isEmpty()) return null;
        byte[] out = buf.clone();
        String prefix = tagLabel + "_b";
        int applied = 0;
        for (SimpleEntry<String, Object> e : soln) {
            String k = e.getKey();
            if (!k.startsWith(prefix)) continue;
            String idxStr = k.substring(prefix.length());
            int idx;
            try {
                idx = Integer.parseInt(idxStr);
            } catch (NumberFormatException nfe) {
                continue;
            }
            if (idx < 0 || idx >= out.length) continue;
            Object v = e.getValue();
            if (v instanceof Integer) {
                out[idx] = ((Integer) v).byteValue();
                applied++;
            } else if (v instanceof Number) {
                out[idx] = ((Number) v).byteValue();
                applied++;
            }
        }
        System.out.println(linePrefix + "_SOLVER_APPLIED iter=" + iter
                + " soln_size=" + soln.size()
                + " bytes_applied=" + applied);
        if (applied == 0) return null;
        return out;
    }

    private static boolean sameBytes(byte[] a, byte[] b) {
        if (a == b) return true;
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }
}
