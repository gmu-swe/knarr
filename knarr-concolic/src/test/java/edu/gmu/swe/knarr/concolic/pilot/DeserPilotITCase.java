package edu.gmu.swe.knarr.concolic.pilot;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pilot 8 ITCase (DeserPilot) — drives {@link DeserPilotTarget} under
 * the dual JDK through the five standard mutators
 * ({@code struct}, {@code random}, {@code solver}, {@code concolic},
 * {@code guided}; plus the optional {@code llm-guided} when
 * {@code $KNARR_LLM_RANKER} is set).
 *
 * <p>Pure defensive measurement — we are NOT trying to construct a
 * working gadget chain. The output metric is: how many distinct
 * {@code CLASS_SEEN <SimpleName>} buckets each mutator opens in its 10
 * iterations. A solver / concolic mutator "winning" on this pilot looks
 * like "reached more distinct commons-collections class names in the
 * JDK's ObjectInputStream.resolveClass callback than structural /
 * random mutation did", without any of those classes ever being
 * instantiated (the {@link DeserPilotTarget.FilteringOIS} plus the
 * deny-all {@link DeserPilotTarget.DenyAllSecurityManager} combination
 * guarantees that).
 *
 * <p>Assertion-wise this pilot is less strict than the others: the
 * interesting signal is whether any CLASS_SEEN bucket opens at all
 * within 10 iters, which may not happen (especially for structural /
 * random mutation). We therefore assert only {@code started},
 * {@code finished}, and {@code uniqueBranches > 0}; no lower bound on
 * outcome count, no lower bound on CLASS_SEEN bucket count. The per-
 * mutator stdout snapshot is persisted under {@code target/} for
 * offline bucket-counting by the design note.
 */
public class DeserPilotITCase {

    @Test
    void deserClassReachPilot() throws Exception {
        PilotHarness.ensureTargetDir();
        Map<String, PilotHarness.RunResult> results = new LinkedHashMap<>();

        try (PilotHarness.Server ignored =
                     PilotHarness.startServer(Paths.get("target"))) {
            List<String> mutators = new java.util.ArrayList<>(
                    List.of("struct", "random", "solver", "concolic", "guided"));
            if (System.getenv("KNARR_LLM_RANKER") != null
                    && !System.getenv("KNARR_LLM_RANKER").isEmpty()) {
                mutators.add("llm-guided");
            }
            for (String mutator : mutators) {
                int timeout = (mutator.equals("solver") || mutator.equals("concolic")
                        || mutator.equals("guided") || mutator.equals("llm-guided"))
                        ? 1800 : 600;
                String out = PilotHarness.runTargetUnderDualJdk(
                        DeserPilotTarget.class.getName(),
                        timeout,
                        List.of("--mutator=" + mutator));
                PilotHarness.snapshot("deser-pilot-" + mutator + "-output.txt", out);
                PilotHarness.RunResult r = PilotHarness.parseRun("DESER", out);
                results.put(mutator, r);
                System.out.println("[DeserPilotITCase] " + mutator
                        + " iters=" + r.iters
                        + " outcomes=" + r.uniqueOutcomes
                        + " branches=" + r.uniqueBranches);
                Assertions.assertTrue(r.started,
                        "target never started (" + mutator + ") — output:\n" + out);
                Assertions.assertTrue(r.finished,
                        "target never finished (" + mutator + ") — output:\n" + out);
                // Deliberately NO outcome-count lower bound. This pilot's
                // value is the CLASS_SEEN coverage map, which may or may
                // not open a bucket in 10 iters (deserialization input
                // has a rigid TC_OBJECT / TC_CLASSDESC framing that many
                // random mutations trivially corrupt into StreamCorrupted
                // before any resolveClass call fires). The design note
                // reads the raw snapshot, not a pass/fail threshold.
            }
        }

        PilotHarness.writeTable("deser-pilot-table.txt", results);

        for (String m : List.of("struct", "random", "solver", "concolic", "guided")) {
            Assertions.assertTrue(results.get(m).uniqueBranches > 0,
                    m + " recorded 0 branches. Table:\n" + results);
        }
    }
}
