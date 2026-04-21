package edu.gmu.swe.knarr.concolic;

import edu.gmu.swe.knarr.runtime.PathConstraintListener;
import edu.gmu.swe.knarr.runtime.Symbolicator;
import edu.neu.ccs.prl.galette.internal.runtime.symbolic.SymbolicListener;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;
import edu.neu.ccs.prl.crochet.runtime.CheckpointRollbackAgent;

/**
 * A minimal concolic-execution driver that couples Knarr's path-constraint
 * collection with CROCHET's checkpoint/rollback primitive.
 *
 * <p>Each iteration of the loop:
 * <ol>
 *   <li>Takes (or reuses) a heap checkpoint via CROCHET's
 *       {@link CheckpointRollbackAgent#checkpointAll()}.</li>
 *   <li>Invokes the user-supplied target closure with the current input —
 *       the target is responsible for tagging values via
 *       {@link Symbolicator}, and the installed
 *       {@link PathConstraintListener} records the resulting path
 *       condition as the target runs.</li>
 *   <li>Sends the path condition to the knarr-server via
 *       {@link Symbolicator#dumpConstraints(String)} and receives a
 *       satisfying model. In a full implementation the driver would
 *       negate a specific branch before solving; the MVP uses whatever
 *       the solver returns for the accumulated PC.</li>
 *   <li>Feeds the model back through the user-supplied mutator, which
 *       produces the next input.</li>
 *   <li>Rolls the heap state back via
 *       {@link CheckpointRollbackAgent#rollbackAll(int)} so the next
 *       iteration sees the pristine setup. Knarr's runtime state is
 *       reset via {@link Symbolicator#reset()}.</li>
 * </ol>
 *
 * <p>The driver is agnostic to the application; callers provide a target
 * {@link Consumer} that takes an input of type {@code T} and a mutator
 * {@code Mutator<T>} that converts a Z3 model back into an input.
 *
 * <p><em>Deployment prerequisites:</em>
 * <ul>
 *   <li>JVM started with both the Galette and CROCHET agents, on a JDK
 *       image that was instrumented by both transformers. The
 *       {@code knarr-bootstrap} script (TODO) sets this up.</li>
 *   <li>knarr-server running on {@code 127.0.0.1:9090} (override via
 *       {@code -DSATServer} / {@code -DSATPort}).</li>
 * </ul>
 */
public final class ConcolicDriver<T> {

    /** Converts a Z3 model (variable → value pairs) into the next input. */
    public interface Mutator<T> {
        T fromSolution(T previous, ArrayList<SimpleEntry<String, Object>> solution);
    }

    private final Consumer<T> target;
    private final Mutator<T> mutator;
    private final int maxIterations;

    public ConcolicDriver(Consumer<T> target, Mutator<T> mutator, int maxIterations) {
        this.target = Objects.requireNonNull(target, "target");
        this.mutator = Objects.requireNonNull(mutator, "mutator");
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations must be positive");
        }
        this.maxIterations = maxIterations;
    }

    /**
     * Runs the concolic loop starting from {@code initialInput}. Returns a
     * list of solutions produced, one per iteration.
     */
    public ArrayList<ArrayList<SimpleEntry<String, Object>>> run(T initialInput) {
        ArrayList<ArrayList<SimpleEntry<String, Object>>> solutions = new ArrayList<>();
        SymbolicListener.setListener(new PathConstraintListener());
        // Force-initialize the Knarr runtime's static state BEFORE taking
        // the checkpoint. If we skip this, a class whose <clinit> runs
        // during the first target.accept call (e.g. PathUtils, which lazy-
        // assigns usedLabels = new HashSet<>()) gets captured by CROCHET in
        // its pre-init state. After rollbackAll, its static fields revert
        // to null and the next iteration NPEs in Symbolicator.reset. Touch
        // the API surface here so the checkpoint captures a fully-
        // initialized runtime.
        Symbolicator.reset();
        int checkpoint = CheckpointRollbackAgent.checkpointAll();
        try {
            T input = initialInput;
            for (int i = 0; i < maxIterations; i++) {
                try {
                    target.accept(input);
                } catch (RuntimeException | Error unhandled) {
                    // Record that this input drove execution into an
                    // exception — callers often care about these paths.
                    // Continue the loop; the exception does not interrupt
                    // concolic progress.
                }
                ArrayList<SimpleEntry<String, Object>> soln = Symbolicator.dumpConstraints(null);
                // A null / empty solution means the solver either errored
                // out on the current path condition (e.g. Green's Z3
                // translator chokes on a sort mismatch — see byte-array
                // pilots) or it simply returned no model. We still let
                // the mutator run with an empty solution so the loop
                // keeps exploring via its own fallback mutation. Breaking
                // early here is the OLD behaviour that masked instrument-
                // ation wins as "no progress".
                if (soln != null && !soln.isEmpty()) {
                    solutions.add(soln);
                }
                ArrayList<SimpleEntry<String, Object>> mutatorSoln = soln != null
                        ? soln : new ArrayList<>();
                T next = mutator.fromSolution(input, mutatorSoln);
                if (next == null || next.equals(input)) {
                    break;
                }
                input = next;
                Symbolicator.reset();
                CheckpointRollbackAgent.rollbackAll(checkpoint);
                checkpoint = CheckpointRollbackAgent.checkpointAll();
            }
        } finally {
            SymbolicListener.setListener(null);
            Symbolicator.reset();
        }
        return solutions;
    }
}
