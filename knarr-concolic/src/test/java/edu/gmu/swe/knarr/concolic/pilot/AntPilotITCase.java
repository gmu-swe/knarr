package edu.gmu.swe.knarr.concolic.pilot;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pilot 2 ITCase — drives {@link AntPilotTarget} under the dual JDK, three
 * times (struct / random / solver). Entry point + exception policy mirror
 * JQF's {@code edu.berkeley.cs.jqf.examples.ant.ProjectBuilderTest}.
 */
public class AntPilotITCase {

    @Test
    void antProjectHelperPilot() throws Exception {
        PilotHarness.ensureTargetDir();
        Map<String, PilotHarness.RunResult> results = new LinkedHashMap<>();

        try (PilotHarness.Server ignored =
                     PilotHarness.startServer(Paths.get("target"))) {
            for (String mutator : List.of("struct", "random", "solver", "concolic")) {
                // Concolic adds one additional solver round-trip per iter
                // on top of the flipped-PC serialization; give it the same
                // headroom as the Tar pilot to avoid flakes.
                int timeout = "concolic".equals(mutator) ? 900 : 300;
                String out = PilotHarness.runTargetUnderDualJdk(
                        AntPilotTarget.class.getName(),
                        timeout,
                        List.of("--mutator=" + mutator));
                PilotHarness.snapshot("ant-pilot-" + mutator + "-output.txt", out);
                PilotHarness.RunResult r = PilotHarness.parseRun("ANT", out);
                results.put(mutator, r);
                System.out.println("[AntPilotITCase] " + mutator
                        + " iters=" + r.iters
                        + " outcomes=" + r.uniqueOutcomes
                        + " branches=" + r.uniqueBranches);
                Assertions.assertTrue(r.started,
                        "target never started (" + mutator + ") — output:\n" + out);
                Assertions.assertTrue(r.finished,
                        "target never finished (" + mutator + ") — output:\n" + out);
                Assertions.assertTrue(r.uniqueOutcomes >= 2,
                        "pilot (" + mutator + ") reached only " + r.uniqueOutcomes
                                + " distinct outcome(s); output:\n" + out);
            }
        }

        PilotHarness.writeTable("ant-pilot-table.txt", results);

        int structBranches = results.get("struct").uniqueBranches;
        int solverBranches = results.get("solver").uniqueBranches;
        Assertions.assertTrue(solverBranches >= structBranches,
                "solver mutator reached " + solverBranches + " branches, fewer than struct "
                        + structBranches + ". Table:\n" + results);

        int structOutcomes = results.get("struct").uniqueOutcomes;
        int concolicOutcomes = results.get("concolic").uniqueOutcomes;
        // Concolic's strength is discovering new outcome classes (flipped
        // branch ⇒ new control flow ⇒ potentially new exception / success
        // bucket). Branch count is secondary — a single flipped branch can
        // easily produce a shallower path than 10 iters of structural
        // mutation. Assert outcomes is at least struct's.
        Assertions.assertTrue(concolicOutcomes >= structOutcomes,
                "concolic mutator reached " + concolicOutcomes
                        + " distinct outcomes, fewer than struct "
                        + structOutcomes + ". Table:\n" + results);
    }
}
