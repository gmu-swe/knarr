package edu.gmu.swe.knarr.concolic;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Compile-time + construction-time sanity checks for {@link ConcolicDriver}.
 *
 * <p>The driver's {@code run()} method calls into Galette's
 * {@link edu.neu.ccs.prl.galette.internal.runtime.symbolic.SymbolicListener}
 * and CROCHET's {@link edu.neu.ccs.prl.crochet.runtime.CheckpointRollbackAgent},
 * both of which require a Galette/CROCHET-instrumented JVM to actually
 * function. This class exercises only the cold paths so the test fits the
 * normal Surefire (plain JDK) runner — the hot path is covered by the E2E
 * integration tests that run on the dual-instrumented JDK (see the
 * concolic/ IT suite gated by the integration-tests profile).
 */
public class ConcolicDriverStructureTest {

    @Test
    void constructorRejectsBadArgs() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new ConcolicDriver<Integer>(null, (prev, sol) -> prev, 1));
        Assertions.assertThrows(NullPointerException.class,
                () -> new ConcolicDriver<Integer>(x -> {}, null, 1));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ConcolicDriver<Integer>(x -> {}, (prev, sol) -> prev, 0));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ConcolicDriver<Integer>(x -> {}, (prev, sol) -> prev, -1));
    }

    @Test
    void mutatorInterfaceShape() {
        ConcolicDriver.Mutator<Integer> m = (prev, sol) -> prev + 1;
        ArrayList<SimpleEntry<String, Object>> fakeSolution = new ArrayList<>();
        fakeSolution.add(new SimpleEntry<>("x", 42));
        Assertions.assertEquals(6, m.fromSolution(5, fakeSolution));
    }

    @Test
    void constructorHappyPath() {
        ConcolicDriver<Integer> driver = new ConcolicDriver<>(
                x -> {}, (prev, sol) -> null, 5);
        Assertions.assertNotNull(driver);
    }
}
