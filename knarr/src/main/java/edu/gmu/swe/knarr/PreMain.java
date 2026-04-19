package edu.gmu.swe.knarr;

import java.lang.instrument.Instrumentation;
import java.util.Optional;

import edu.gmu.swe.knarr.runtime.Coverage;
import edu.gmu.swe.knarr.runtime.PathConstraintListener;
import edu.neu.ccs.prl.galette.internal.runtime.symbolic.SymbolicListener;

/**
 * Knarr's premain entry point under Galette. Galette is installed
 * separately as its own {@code -javaagent}; Knarr is added as an additional
 * {@code -javaagent} that simply registers a {@link PathConstraintListener}
 * with Galette's {@link SymbolicListener} dispatcher.
 */
public class PreMain {
    public static void premain(String args, Instrumentation inst) {
        Optional<String[]> o = Optional.empty();
        Coverage.setCov(true, o);
        SymbolicListener.setListener(new PathConstraintListener());
    }
}
