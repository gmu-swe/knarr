package edu.gmu.swe.knarr.concolic.pilot;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pilot 3 ITCase — drives {@link ZipPilotTarget} under the dual JDK through
 * the same five (six w/ {@code KNARR_LLM_RANKER}) mutators as
 * {@link AntPilotITCase}. Anchors the long tail of ZIP header parser
 * CVEs (CVE-2018-11771 family) in commons-compress.
 */
public class ZipPilotITCase {

    @Test
    void zipHeaderPilot() throws Exception {
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
                        ZipPilotTarget.class.getName(),
                        timeout,
                        List.of("--mutator=" + mutator));
                PilotHarness.snapshot("zip-pilot-" + mutator + "-output.txt", out);
                PilotHarness.RunResult r = PilotHarness.parseRun("ZIP", out);
                results.put(mutator, r);
                System.out.println("[ZipPilotITCase] " + mutator
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

        PilotHarness.writeTable("zip-pilot-table.txt", results);

        for (String m : List.of("struct", "random", "solver", "concolic", "guided")) {
            Assertions.assertTrue(results.get(m).uniqueBranches > 0,
                    m + " recorded 0 branches. Table:\n" + results);
        }
    }
}
