package edu.gmu.swe.knarr.bridge.agent;

import edu.gmu.swe.knarr.bridge.CompositeTransformer;
import edu.neu.ccs.prl.galette.internal.runtime.frame.SpareFrameStore;
import edu.neu.ccs.prl.galette.internal.transform.GaletteLog;
import edu.neu.ccs.prl.galette.internal.transform.GaletteTransformer;
import edu.neu.ccs.prl.galette.internal.transform.TransformationCache;
import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import edu.neu.ccs.prl.crochet.runtime.ArrayRegistry;
import edu.neu.ccs.prl.crochet.runtime.CheckpointRollbackAgent;

/**
 * Single java-agent whose {@code premain} installs one {@link
 * ClassFileTransformer} that applies {@link CompositeTransformer} per
 * class. Replaces the two-{@code -javaagent} attack mode which the two
 * worktree experiments (Galette-first via jimage-rewrite, CROCHET-first
 * via the same path) both proved unworkable — each independent agent's
 * transformer ran after the other, producing methods the other had
 * already finalised its shadow surface around, yielding {@code
 * AbstractMethodError} (Galette-first) or infinite clinit recursion in
 * {@code sun.instrument.InstrumentationImpl} (CROCHET-first).
 *
 * <p>Because the transformer runs once per class load, ordering of the
 * two surfaces is decided at a single point (in {@link
 * CompositeTransformer}, where CROCHET runs after Galette — see that
 * class's javadoc for rationale). Every method visible to the runtime
 * gets BOTH surfaces in the expected layout.
 *
 * <p>Both agents' non-transformer init steps (Galette {@code
 * SpareFrameStore} init, CROCHET {@code CheckpointRollbackAgent}
 * instrumentation handle publication, {@code ArrayRegistry.warmup}) run
 * here too so their runtimes are safe to call from instrumented code.
 *
 * <p>Classes in the skip list below are never passed through either
 * transformer, to break three known recursion loops that the parallel
 * agent experiments discovered:
 * <ol>
 *   <li>{@code sun.instrument.InstrumentationImpl} triggers a
 *       StackOverflow via {@code $$crochet*} + Galette shadow dispatch
 *       during {@code -javaagent} attach (Agent B's wall).</li>
 *   <li>Galette's own {@code StringSymbolicMasks.lengthMask} reaches
 *       {@code StringLatin1.inflate} and triggers {@code
 *       CrochetRuntimeReady.beforeStore}, which re-enters
 *       {@code GaletteTransformer.transform} via {@code MaskRegistry.getKey}
 *       (Agent A's wall).</li>
 *   <li>CROCHET's own runtime classes (e.g. {@code CheckpointRollbackAgent})
 *       cannot be Galette-instrumented without cyclic init during
 *       first-use.</li>
 * </ol>
 */
public final class CombinedAgent {

    private CombinedAgent() {
        throw new AssertionError();
    }

    public static void premain(String args, Instrumentation inst) throws Exception {
        // Galette-side init. Mirrors edu.neu.ccs.prl.galette.internal.agent.GaletteAgent.
        SpareFrameStore.initialize();
        GaletteLog.initialize(System.err);
        String cachePath = System.getProperty("galette.cache");
        TransformationCache cache = cachePath == null ? null : new TransformationCache(new File(cachePath));
        GaletteTransformer.setCache(cache);

        // CROCHET-side init. Mirrors edu.neu.ccs.prl.crochet.agent.CrochetAgent.
        try {
            CheckpointRollbackAgent.setInstrumentation(inst);
        } catch (Throwable ignored) {
        }
        try {
            ArrayRegistry.warmup();
        } catch (Throwable ignored) {
        }
        // Pre-initialize classes whose first-use during a post-checkpoint
        // VERSION_GATE != 0 window triggers a ClassCircularityError. The
        // failure mode: ConcurrentHashMap.fullAddCount lazy-inits
        // ThreadLocalRandom, whose <clinit> path re-enters ConcurrentHashMap
        // via ArrayRegistry.drainQueue's remove()-on-META, and because the
        // inner CHM bucket it touches also needs ThreadLocalRandom, class
        // init cycles. CROCHET's own demos happen to exercise all of these
        // before their first checkpoint; with Galette also active and the
        // knarr driver's narrower warm-up surface we can't rely on that.
        preInit(
                "java.util.concurrent.ThreadLocalRandom",
                "java.util.concurrent.locks.LockSupport",
                "java.lang.invoke.StringConcatFactory",
                "java.lang.invoke.LambdaForm",
                "java.lang.invoke.MethodHandleImpl");

        inst.addTransformer(new Wrapper());
    }

    public static void agentmain(String args, Instrumentation inst) throws Exception {
        premain(args, inst);
    }

    private static void preInit(String... names) {
        for (String n : names) {
            try {
                Class.forName(n, true, ClassLoader.getSystemClassLoader());
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class Wrapper implements ClassFileTransformer {
        private final CompositeTransformer composite = new CompositeTransformer();

        @Override
        public byte[] transform(
                ClassLoader loader,
                String className,
                Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain,
                byte[] classFileBuffer) {
            if (classBeingRedefined != null) {
                // Redefinition — Galette returns null (unchanged); do the same.
                return null;
            }
            if (isSkipped(className)) {
                return null;
            }
            try {
                return composite.transform(classFileBuffer, false, loader);
            } catch (Throwable t) {
                // Same silent-fail policy as the two upstream agents: a
                // transform failure must not abort user class loading.
                return null;
            }
        }

        /**
         * Skip list for classes whose composite transformation would
         * trigger one of the three known bootstrap recursions. See class
         * javadoc for the rationale for each group.
         */
        private static boolean isSkipped(String name) {
            if (name == null) {
                return false;
            }
            // Agent infrastructure — Agent B's wall.
            if (name.startsWith("sun/instrument/")
                    || name.startsWith("java/lang/instrument/")) {
                return true;
            }
            // CROCHET and Galette runtime packages — already handled by each
            // transformer's own exclusion list, but listed here for
            // defence-in-depth when the two share the same class loader.
            if (name.startsWith("edu/neu/ccs/prl/crochet/")
                    || name.startsWith("edu/neu/ccs/prl/galette/")) {
                return true;
            }
            return false;
        }
    }
}
