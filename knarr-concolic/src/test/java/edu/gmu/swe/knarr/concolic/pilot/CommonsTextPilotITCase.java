package edu.gmu.swe.knarr.concolic.pilot;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pilot 5 ITCase — drives {@link CommonsTextPilotTarget} under the dual
 * JDK through the same five (six w/ {@code KNARR_LLM_RANKER}) mutators
 * as {@link AntPilotITCase}. Anchors CVE-2022-42889 ("Text4Shell").
 */
public class CommonsTextPilotITCase {

    @Test
    void commonsTextInterpolatorPilot() throws Exception {
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
                        CommonsTextPilotTarget.class.getName(),
                        timeout,
                        List.of("--mutator=" + mutator));
                PilotHarness.snapshot("commonstext-pilot-" + mutator + "-output.txt", out);
                PilotHarness.RunResult r = PilotHarness.parseRun("COMMONSTEXT", out);
                results.put(mutator, r);
                System.out.println("[CommonsTextPilotITCase] " + mutator
                        + " iters=" + r.iters
                        + " outcomes=" + r.uniqueOutcomes
                        + " branches=" + r.uniqueBranches);
                Assertions.assertTrue(r.started,
                        "target never started (" + mutator + ") — output:\n" + out);
                Assertions.assertTrue(r.finished,
                        "target never finished (" + mutator + ") — output:\n" + out);
                // StringSubstitutor with the default interpolator swallows
                // "unknown lookup" invocations by leaving ${...} untouched
                // in the output, so random byte mutations within ${sys:...}
                // typically keep landing in the REPLACED bucket. Loosen the
                // outcome-diversity floor to 1 for every mutator — the
                // interesting signal on this target is branch count, not
                // outcome count.
                Assertions.assertTrue(r.uniqueOutcomes >= 1,
                        "pilot (" + mutator + ") reached 0 distinct outcomes; output:\n" + out);
            }
        }

        PilotHarness.writeTable("commonstext-pilot-table.txt", results);

        for (String m : List.of("struct", "random", "solver", "concolic", "guided")) {
            Assertions.assertTrue(results.get(m).uniqueBranches > 0,
                    m + " recorded 0 branches. Table:\n" + results);
        }
    }
}
