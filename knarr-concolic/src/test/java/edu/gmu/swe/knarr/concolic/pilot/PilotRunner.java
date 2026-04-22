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
import za.ac.sun.cs.green.expr.UnaryOperation;

/**
 * Shared pilot iteration driver. Both the Tar and Ant pilots run the same
 * checkpoint/rollback loop with one of six mutation strategies:
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
 *   <li>{@code concolic} — classic branch-negation. Pick a branch index
 *       round-robin, request {@code anchors ∧ b0 ∧ ... ∧ NOT(bi)} from
 *       the solver, apply the model bytes. Falls back to {@code struct}
 *       on UNSAT.</li>
 *   <li>{@code guided} — heuristic concolic: maintain a session-scoped
 *       UNSAT skip list (keyed by {@code class/method:line}), a
 *       cumulative per-site taken/not-taken counter, and rank candidate
 *       flip indices (one-sided sites first, deepest-first tiebreak).</li>
 *   <li>{@code llm-guided} — identical ranking to {@code guided}, but
 *       the final pick is delegated to the subprocess named by
 *       {@code $KNARR_LLM_RANKER}. Intended as a seam for a future
 *       session to wire a Claude (or other LLM) ranker; unset in CI,
 *       so the mutator degrades to the local ranking. See
 *       {@code designs/llm-guided-concolic.md}.</li>
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

    public enum Mutator { STRUCT, RANDOM, SOLVER, CONCOLIC, GUIDED, LLM_GUIDED }

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

    /**
     * Guided mutator state — persists across iterations of this session.
     *
     * <p>{@link #guidedUnsatSites} records branch <em>sites</em> (not
     * indices) that {@code dumpConstraintsForFlip} has already declined.
     * Indices shift iteration to iteration (the PC is rebuilt on every
     * iter) so the skip list keys on a stable fingerprint derived from
     * the branch expression itself — see {@link #guidedSiteKey}.
     *
     * <p>{@link #guidedSiteHits} accumulates, per site, how many times
     * each concrete side has been observed across <em>all</em> past
     * iterations of this session. The classic coverage-guided fuzzing
     * heuristic "a site we've only ever taken one way is a high-priority
     * flip target" reads directly off this map.
     *
     * <p>Telemetry counters ({@code guidedOneWayPicks},
     * {@code guidedDeepestPicks}, {@code guidedUnsatSkips}) are dumped
     * on session finish so the design note can call out which heuristic
     * paid for itself on each pilot.
     */
    private final java.util.HashSet<String> guidedUnsatSites = new java.util.HashSet<>();
    private final java.util.HashMap<String, int[]> guidedSiteHits = new java.util.HashMap<>();
    private int guidedOneWayPicks = 0;
    private int guidedDeepestPicks = 0;
    private int guidedUnsatSkips = 0;
    private int guidedLlmPicks = 0;
    private int guidedLlmFallbacks = 0;
    /** When the previous iter's flip succeeded, re-test one site from the UNSAT
     *  skip list — a successful flip may have shifted the prefix enough that a
     *  previously-UNSAT branch is now reachable. */
    private boolean lastGuidedFlipSucceeded = false;

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
                // Accept "llm-guided" / "llm_guided" synonymously.
                v = v.replace('-', '_');
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

                // Update the cumulative site-hit table BEFORE picking the
                // next mutation — guided/llm_guided branches off the
                // freshest observation of which sites are still one-sided.
                // Gated to only those mutators to avoid paying the cost in
                // baselines that ignore the map.
                if (mutator == Mutator.GUIDED || mutator == Mutator.LLM_GUIDED) {
                    updateGuidedSiteHits();
                }

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
                } else if (mutator == Mutator.GUIDED) {
                    next = guidedMutate(buf, i, false);
                    if (next == null || sameBytes(next, buf)) {
                        next = structMutate(buf, i);
                    }
                } else if (mutator == Mutator.LLM_GUIDED) {
                    next = guidedMutate(buf, i, true);
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
        if (mutator == Mutator.GUIDED || mutator == Mutator.LLM_GUIDED) {
            System.out.println(linePrefix + "_GUIDED_SUMMARY"
                    + " one_way_picks=" + guidedOneWayPicks
                    + " deepest_picks=" + guidedDeepestPicks
                    + " unsat_skips=" + guidedUnsatSkips
                    + " unsat_sites=" + guidedUnsatSites.size()
                    + " llm_picks=" + guidedLlmPicks
                    + " llm_fallbacks=" + guidedLlmFallbacks
                    + " total_sites_observed=" + guidedSiteHits.size());
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

    /**
     * Per-branch site fingerprint derived from the branch expression
     * itself. The rule:
     * <ul>
     *   <li>If the outermost operator is {@code NOT}, strip it; the
     *       inner cmp is the un-polarised "site" and the NOT marks the
     *       concrete outcome as "not-taken".</li>
     *   <li>Otherwise the expression IS the un-polarised site; treat
     *       as "taken".</li>
     * </ul>
     * Returns a {@code (siteKey, taken)} pair (taken=true in index 0,
     * otherwise index 1). Using the expression toString keeps fingerprints
     * stable across iterations — the same syntactic branch (e.g., the
     * "b0 == '/'" check in xerces) yields the same key regardless of
     * which side fired this iter.
     *
     * <p>Trading perfect source-site identification for toString equality
     * is deliberate: capturing a true {@code class/method:bci} requires
     * a stack walk on every branch emission, which under Galette
     * instrumentation on xerces-heavy targets slows a 10-iter pilot
     * from ~30s to &gt;15min. Expression fingerprints are O(toString)
     * and run in microseconds.
     */
    private static String guidedSiteKey(Expression branch) {
        Expression core = branch;
        if (branch instanceof UnaryOperation
                && ((UnaryOperation) branch).getOperator() == Operator.NOT) {
            core = ((UnaryOperation) branch).getOperand(0);
        }
        String s;
        try {
            s = core.toString();
        } catch (Throwable t) {
            return "ERR:" + System.identityHashCode(core);
        }
        if (s.length() > 300) {
            // Long prints (deep anchor-like expressions) get hashed to
            // bound memory; prefix gives enough distinctiveness to
            // distinguish siblings.
            return Integer.toHexString(s.hashCode()) + ":" + s.substring(0, 80);
        }
        return s;
    }

    private static boolean guidedBranchTaken(Expression branch) {
        return !(branch instanceof UnaryOperation
                && ((UnaryOperation) branch).getOperator() == Operator.NOT);
    }

    /**
     * Fold the current iteration's per-branch (site, taken) pairs into the
     * session-scoped {@link #guidedSiteHits} counters. Called from the
     * main loop after every iteration's body so the guided picker reads
     * a complete history.
     */
    private void updateGuidedSiteHits() {
        java.util.LinkedList<Expression> branches = PathUtils.getCurPC().branchConstraints;
        // Snapshot to avoid concurrent mod worries; the pilot is single-
        // threaded at this point but the listener lives on a shared field.
        Expression[] arr = branches.toArray(new Expression[0]);
        for (Expression b : arr) {
            String key = guidedSiteKey(b);
            boolean taken = guidedBranchTaken(b);
            int[] pair = guidedSiteHits.computeIfAbsent(key, k -> new int[2]);
            if (taken) pair[0]++;
            else pair[1]++;
        }
    }

    /**
     * Guided concolic mutator. Ranks candidate branch indices by:
     * <ol>
     *   <li>Session-scoped UNSAT skip list — sites that
     *       {@code dumpConstraintsForFlip} has already declined get
     *       dropped from the pool (with a single re-test after each
     *       successful flip to let a changed prefix re-enable them).</li>
     *   <li>One-sided-site priority — sites in
     *       {@link #guidedSiteHits} that have so far been observed on
     *       only ONE side come first; flipping them is most likely to
     *       open up previously unreachable code.</li>
     *   <li>Deepest-first tiebreak — among equally-ranked sites, pick
     *       the deepest PC index (classic generational search).</li>
     * </ol>
     *
     * <p>When {@code useLlm} is true, the ranked candidate list is
     * forwarded to an external ranker subprocess
     * ({@code $KNARR_LLM_RANKER}) that picks the final index. Any
     * failure / timeout / unset env var falls back to the local ranking.
     * The LLM seam is behind this flag so CI never executes an external
     * process unintentionally — the scaffold is for a future session
     * to wire against {@code claude} or any other LLM CLI. See
     * {@code designs/llm-guided-concolic.md}.
     */
    private byte[] guidedMutate(byte[] buf, int iter, boolean useLlm) {
        int branchCount = PathUtils.getCurPC().branchCount();
        if (branchCount == 0) {
            System.out.println(linePrefix + "_GUIDED_SKIP iter=" + iter + " reason=no_branches");
            return null;
        }
        // Build per-index site keys by walking the current PC's branch
        // list once. Expression fingerprint, not a source-site string —
        // see guidedSiteKey.
        Expression[] branchArr =
                PathUtils.getCurPC().branchConstraints.toArray(new Expression[0]);
        String[] sites = new String[branchArr.length];
        for (int i = 0; i < branchArr.length; i++) {
            sites[i] = guidedSiteKey(branchArr[i]);
        }

        // If last iter's flip succeeded, let previously-UNSAT sites back
        // into the pool once — the shifted PC prefix may have changed
        // enough to satisfy a previously-declined flip.
        if (lastGuidedFlipSucceeded && !guidedUnsatSites.isEmpty()) {
            System.out.println(linePrefix + "_GUIDED_RETRY iter=" + iter
                    + " reopen_sites=" + guidedUnsatSites.size());
            guidedUnsatSites.clear();
        }
        // Build ranked candidate list over indices 0..branchCount-1.
        // Cap pick depth to keep the flipped-PC tree within the Green
        // serializer's budget. On Ant iter 0 we see branch_total=920
        // (xerces tagged-byte cascade), where flipping idx=919 serialises
        // a PC deep enough to trigger Galette's Tag.hashCode recursion
        // during the next iter's parse. GUIDED_MAX_DEPTH caps the
        // deepest pick the ranker will return.
        int maxDepth = Math.min(branchCount - 1, 200);
        java.util.ArrayList<int[]> oneWay = new java.util.ArrayList<>(); // idx, depth
        java.util.ArrayList<int[]> rest = new java.util.ArrayList<>();
        int scan = Math.min(branchCount, sites.length);
        for (int idx = 0; idx <= maxDepth && idx < scan; idx++) {
            String s = sites[idx];
            if (guidedUnsatSites.contains(s)) continue;
            int[] pair = guidedSiteHits.get(s);
            boolean isOneWay = pair != null && (pair[0] == 0 || pair[1] == 0);
            (isOneWay ? oneWay : rest).add(new int[]{idx, idx});
        }
        // Deepest first: sort by idx descending inside each bucket.
        java.util.Comparator<int[]> deepest = (a, b) -> Integer.compare(b[0], a[0]);
        oneWay.sort(deepest);
        rest.sort(deepest);
        java.util.ArrayList<int[]> ranked = new java.util.ArrayList<>(oneWay);
        ranked.addAll(rest);
        if (ranked.isEmpty()) {
            System.out.println(linePrefix + "_GUIDED_SKIP iter=" + iter
                    + " reason=all_unsat branch_total=" + branchCount);
            // Clear the skip list so next iter can try fresh.
            guidedUnsatSites.clear();
            lastGuidedFlipSucceeded = false;
            return null;
        }

        // Pick the top candidate, optionally via LLM subprocess.
        int chosen;
        String reason;
        if (useLlm) {
            Integer llmChoice = askLlmRanker(iter, ranked, sites);
            if (llmChoice != null) {
                chosen = llmChoice;
                reason = "llm";
                guidedLlmPicks++;
            } else {
                chosen = ranked.get(0)[0];
                reason = guidedReasonFor(chosen, oneWay);
                guidedLlmFallbacks++;
            }
        } else {
            chosen = ranked.get(0)[0];
            reason = guidedReasonFor(chosen, oneWay);
        }
        if ("one-way".equals(reason)) guidedOneWayPicks++;
        else if ("deepest".equals(reason)) guidedDeepestPicks++;

        String chosenSite = chosen < sites.length ? sites[chosen] : "?";
        System.out.println(linePrefix + "_GUIDED_PICK iter=" + iter
                + " idx=" + chosen
                + " reason=" + reason
                + " site=" + chosenSite
                + " pool_one_way=" + oneWay.size()
                + " pool_rest=" + rest.size()
                + " branch_total=" + branchCount);

        ArrayList<SimpleEntry<String, Object>> soln;
        try {
            soln = Symbolicator.dumpConstraintsForFlip(chosen);
        } catch (Throwable t) {
            System.out.println(linePrefix + "_GUIDED_EX iter=" + iter
                    + " idx=" + chosen + " ex=" + t.getClass().getSimpleName());
            guidedUnsatSites.add(chosenSite);
            guidedUnsatSkips++;
            lastGuidedFlipSucceeded = false;
            return null;
        }
        if (soln == null || soln.isEmpty()) {
            System.out.println(linePrefix + "_GUIDED_UNSAT iter=" + iter
                    + " idx=" + chosen + " site=" + chosenSite);
            guidedUnsatSites.add(chosenSite);
            guidedUnsatSkips++;
            lastGuidedFlipSucceeded = false;
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
        System.out.println(linePrefix + "_GUIDED_APPLIED iter=" + iter
                + " idx=" + chosen
                + " soln_size=" + soln.size()
                + " bytes_applied=" + applied);
        if (applied == 0) {
            lastGuidedFlipSucceeded = false;
            return null;
        }
        lastGuidedFlipSucceeded = true;
        return out;
    }

    /** Labels the chosen index for telemetry: one-way > deepest. */
    private String guidedReasonFor(int chosen, java.util.ArrayList<int[]> oneWay) {
        for (int[] r : oneWay) {
            if (r[0] == chosen) return "one-way";
        }
        return "deepest";
    }

    /**
     * Scaffold for an external LLM ranker. Serialises the candidate list
     * to stdin of {@code $KNARR_LLM_RANKER}, reads a one-line JSON
     * response on stdout, returns the chosen index. Failure modes (env
     * var unset, subprocess non-zero exit, timeout &gt; 10s, unparseable
     * output) all return {@code null} so the caller falls back to the
     * local ranking. See {@code designs/llm-guided-concolic.md} for the
     * JSON protocol.
     */
    private Integer askLlmRanker(int iter,
                                 java.util.ArrayList<int[]> ranked,
                                 String[] sites) {
        String cmd = System.getenv("KNARR_LLM_RANKER");
        if (cmd == null || cmd.isEmpty()) {
            // Scaffold-only: the mutator is designed to be a seam, not a
            // hard dependency on an external tool. Without the env var
            // the LLM mode is structurally identical to guided.
            return null;
        }
        // Build compact JSON manually — no dependency on a JSON library
        // in the target process, keeping the fork's classpath small.
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"pilot\":\"").append(linePrefix.toLowerCase())
          .append("\",\"iter\":").append(iter)
          .append(",\"candidates\":[");
        int limit = Math.min(ranked.size(), 32);
        for (int i = 0; i < limit; i++) {
            int idx = ranked.get(i)[0];
            String s = idx < sites.length ? sites[idx] : "?";
            int[] pair = guidedSiteHits.get(s);
            int takenC = pair == null ? 0 : pair[0];
            int notC = pair == null ? 0 : pair[1];
            if (i > 0) sb.append(',');
            sb.append("{\"idx\":").append(idx)
              .append(",\"site\":\"").append(jsonEscape(s))
              .append("\",\"taken\":").append(takenC)
              .append(",\"notTaken\":").append(notC).append('}');
        }
        sb.append("]}");
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cmd);
            pb.redirectErrorStream(false);
            Process p = pb.start();
            try (java.io.OutputStream os = p.getOutputStream()) {
                os.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                System.out.println(linePrefix + "_LLM_TIMEOUT iter=" + iter);
                return null;
            }
            byte[] data = p.getInputStream().readAllBytes();
            if (p.exitValue() != 0) {
                System.out.println(linePrefix + "_LLM_EXIT iter=" + iter
                        + " code=" + p.exitValue());
                return null;
            }
            String resp = new String(data, java.nio.charset.StandardCharsets.UTF_8).trim();
            // Cheap hand-parse: look for "idx":<int>
            int at = resp.indexOf("\"idx\"");
            if (at < 0) return null;
            int colon = resp.indexOf(':', at);
            if (colon < 0) return null;
            int end = colon + 1;
            while (end < resp.length() && (resp.charAt(end) == ' ' || resp.charAt(end) == '-'
                    || Character.isDigit(resp.charAt(end)))) end++;
            try {
                int pick = Integer.parseInt(resp.substring(colon + 1, end).trim());
                int branchCount = PathUtils.getCurPC().branchCount();
                if (pick < 0 || pick >= branchCount) return null;
                return pick;
            } catch (NumberFormatException nfe) {
                return null;
            }
        } catch (Throwable t) {
            System.out.println(linePrefix + "_LLM_EX iter=" + iter
                    + " ex=" + t.getClass().getSimpleName());
            return null;
        }
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.toString();
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
