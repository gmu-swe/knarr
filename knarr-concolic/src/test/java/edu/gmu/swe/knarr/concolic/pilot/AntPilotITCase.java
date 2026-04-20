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
            for (String mutator : List.of("struct", "random", "solver")) {
                String out = PilotHarness.runTargetUnderDualJdk(
                        AntPilotTarget.class.getName(),
                        300,
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
    }
}
