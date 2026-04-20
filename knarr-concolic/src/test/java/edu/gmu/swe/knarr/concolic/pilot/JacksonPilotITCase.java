package edu.gmu.swe.knarr.concolic.pilot;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pilot 4 ITCase — drives {@link JacksonPilotTarget} under the dual JDK
 * through the same five (six w/ {@code KNARR_LLM_RANKER}) mutators as
 * {@link AntPilotITCase}. Anchors CVE-2017-7525 (Jackson polymorphic
 * deserialization gadget-class bypass) and its long tail.
 */
public class JacksonPilotITCase {

    @Test
    void jacksonPolymorphicPilot() throws Exception {
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
                        ? 1800 : 300;
                String out = PilotHarness.runTargetUnderDualJdk(
                        JacksonPilotTarget.class.getName(),
                        timeout,
                        List.of("--mutator=" + mutator));
                PilotHarness.snapshot("jackson-pilot-" + mutator + "-output.txt", out);
                PilotHarness.RunResult r = PilotHarness.parseRun("JACKSON", out);
                results.put(mutator, r);
                System.out.println("[JacksonPilotITCase] " + mutator
                        + " iters=" + r.iters
                        + " outcomes=" + r.uniqueOutcomes
                        + " branches=" + r.uniqueBranches);
                Assertions.assertTrue(r.started,
                        "target never started (" + mutator + ") — output:\n" + out);
                Assertions.assertTrue(r.finished,
                        "target never finished (" + mutator + ") — output:\n" + out);
                int minOutcomes = ("solver".equals(mutator) || "concolic".equals(mutator)
                        || "guided".equals(mutator) || "llm-guided".equals(mutator)) ? 1 : 2;
                Assertions.assertTrue(r.uniqueOutcomes >= minOutcomes,
                        "pilot (" + mutator + ") reached only " + r.uniqueOutcomes
                                + " distinct outcome(s); output:\n" + out);
            }
        }

        PilotHarness.writeTable("jackson-pilot-table.txt", results);

        for (String m : List.of("struct", "random", "solver", "concolic", "guided")) {
            Assertions.assertTrue(results.get(m).uniqueBranches > 0,
                    m + " recorded 0 branches. Table:\n" + results);
        }
    }
}
