package edu.gmu.swe.knarr.concolic.pilot;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pilot 2 ITCase — drives {@link AntPilotTarget} under the dual JDK and
 * asserts the concolic loop surfaces at least two distinct outcomes on
 * Ant's {@code ProjectHelperImpl.parse}. Entry point + exception policy
 * mirror JQF's {@code edu.berkeley.cs.jqf.examples.ant.ProjectBuilderTest}
 * so the measurements are comparable to that baseline.
 */
public class AntPilotITCase {

    @Test
    void antProjectHelperPilot() throws Exception {
        String out = PilotHarness.runTargetUnderDualJdk(
                AntPilotTarget.class.getName(),
                180,
                List.of());
        PilotHarness.snapshot("ant-pilot-output.txt", out);

        long outcomes = out.lines()
                .filter(l -> l.startsWith("ANT_OUTCOME "))
                .distinct()
                .count();
        Assertions.assertTrue(out.contains("ANT_PILOT_STARTED"),
                "target never started — output:\n" + out);
        Assertions.assertTrue(out.contains("ANT_PILOT_FINISHED"),
                "target never finished cleanly — output:\n" + out);
        Assertions.assertTrue(outcomes >= 2,
                "pilot reached only " + outcomes + " distinct outcome(s); "
                        + "the parser + mutator combination isn't producing "
                        + "diversity. output:\n" + out);
    }
}
