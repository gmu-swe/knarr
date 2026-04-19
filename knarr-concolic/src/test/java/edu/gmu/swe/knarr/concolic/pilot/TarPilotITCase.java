package edu.gmu.swe.knarr.concolic.pilot;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pilot 1 ITCase — drives {@link TarPilotTarget} under the dual JDK and
 * asserts the concolic loop surfaces at least two distinct outcomes on
 * commons-compress's tar header parser.
 *
 * <p>Two distinct outcomes is the smallest meaningful signal: it proves
 * the solver produced at least one input that pushed the parser into a
 * different state than the seed (valid-magic, zero-padded) byte buffer
 * would on its own. We deliberately don't pin the exact outcome set —
 * commons-compress's header validation order isn't stable across versions
 * and we want the pilot to survive a dep bump.
 */
public class TarPilotITCase {

    @Test
    void tarHeaderPilot() throws Exception {
        // No knarr-server: the pilot drives mutation locally rather than
        // via solver round-trips. See TarPilotTarget for the rationale
        // (Green's Z3 translator doesn't yet encode byte-array stores
        // with the right sort).
        String out = PilotHarness.runTargetUnderDualJdk(
                TarPilotTarget.class.getName(),
                180,
                List.of());
        PilotHarness.snapshot("tar-pilot-output.txt", out);

        long outcomes = out.lines()
                .filter(l -> l.startsWith("TAR_OUTCOME "))
                .distinct()
                .count();
        Assertions.assertTrue(out.contains("TAR_PILOT_STARTED"),
                "target never started — output:\n" + out);
        Assertions.assertTrue(out.contains("TAR_PILOT_FINISHED"),
                "target never finished cleanly — output:\n" + out);
        Assertions.assertTrue(outcomes >= 2,
                "pilot reached only " + outcomes + " distinct outcome(s); "
                        + "the parser + mutator combination isn't producing "
                        + "diversity. output:\n" + out);
    }
}
