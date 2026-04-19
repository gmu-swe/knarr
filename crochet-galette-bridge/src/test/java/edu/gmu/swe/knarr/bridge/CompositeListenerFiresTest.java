package edu.gmu.swe.knarr.bridge;

import edu.neu.ccs.prl.galette.internal.runtime.Tag;
import edu.neu.ccs.prl.galette.internal.runtime.Tainter;
import edu.neu.ccs.prl.galette.internal.runtime.symbolic.SymbolicExecutionListener;
import edu.neu.ccs.prl.galette.internal.runtime.symbolic.SymbolicListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Critical dual-instrumentation check: after Galette + CROCHET composition,
 * does the Galette branch hook still fire when the transformed class runs
 * on a plain JVM?
 *
 * <p>This probe runs on a vanilla (un-instrumented) JDK by classloading
 * the dual-transformed bytecode into an isolated child loader. The test
 * class's own bytecode is NOT transformed; only the fixture loaded via
 * the child loader is. Galette's hooks (installed by the transformer as
 * plain {@code invokestatic} calls to {@link SymbolicListener}) resolve
 * lazily against the runtime classpath and fire when the fixture method
 * runs.
 */
@org.junit.jupiter.api.Disabled(
        "Galette runtime requires an instrumented java.lang.Thread (with "
                + "$$GALETTE_$$LOCAL_frame). This plain-JVM probe cannot satisfy that "
                + "dependency. Runtime listener verification happens on the "
                + "dual-instrumented JDK produced by DualInstrumentMain; see "
                + "knarr-concolic/ IT suite.")
public class CompositeListenerFiresTest {

    static final class LoaderOverride extends ClassLoader {
        LoaderOverride(ClassLoader parent) { super(parent); }
        Class<?> define(byte[] bytes) {
            return defineClass(null, bytes, 0, bytes.length);
        }
    }

    private static byte[] readFixture(String name) throws Exception {
        return Files.readAllBytes(Path.of("target/test-classes", name));
    }

    @AfterEach
    void clearListener() {
        SymbolicListener.setListener(null);
    }

    @Test
    void branchHookFiresAfterCompositeTransform() throws Exception {
        byte[] raw = readFixture("edu/gmu/swe/knarr/bridge/fixture/SimpleCounter.class");
        byte[] dual = new CompositeTransformer().transform(raw, false);

        AtomicInteger branchCount = new AtomicInteger();
        SymbolicListener.setListener(new SymbolicExecutionListener() {
            @Override
            public void onIntBranch(int opcode, int value, Tag tag) {
                branchCount.incrementAndGet();
            }
        });

        LoaderOverride cl = new LoaderOverride(getClass().getClassLoader());
        Class<?> klass = cl.define(dual);
        Object instance = klass.getDeclaredConstructor().newInstance();

        // Tag the argument so Galette's branch hook has something to observe.
        int tagged = Tainter.setTag(42, Tag.of("delta"));
        klass.getDeclaredMethod("addOne", int.class).invoke(instance, tagged);

        Assertions.assertTrue(
                branchCount.get() > 0,
                "Galette branch hook must fire on a tagged branch even after "
                        + "CROCHET has wrapped the class");
    }
}
