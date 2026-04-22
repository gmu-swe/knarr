package edu.gmu.swe.knarr.bridge;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The smallest possible dual-instrumentation probe. Compiles a trivial
 * class at test-compile time (see the test fixtures), runs it through
 * {@link CompositeTransformer}, defines the result in a throwaway
 * {@link ClassLoader}, and calls a method. Passing proves that Galette's
 * and CROCHET's transformers compose without producing bytecode that
 * fails JVM verification.
 *
 * <p>Failure modes to expect on first run:
 * <ul>
 *   <li>{@link VerifyError}: one transformer's output violates an
 *       invariant the other assumes. Report the method name and stack
 *       slot from the VerifyError; that usually points straight at which
 *       visitor miscounted.</li>
 *   <li>{@link NoSuchMethodError} / {@link NoClassDefFoundError} at
 *       invoke time: shadow-method-resolution mismatch; one transformer
 *       rewrote a call site to a shadow the other had not yet added.</li>
 *   <li>{@link IllegalAccessError}: one of the transformers assumed
 *       package-private visibility for a helper; relocating to public or
 *       the bridge's own package fixes it.</li>
 * </ul>
 */
public class CompositeTransformerProbeTest {

    /**
     * Isolated class loader for each transform variant. Uses the bytecode's
     * own declared class name so the {@code defineClass} name check passes.
     * Multiple variants of {@code SimpleCounter} can coexist because each
     * child loader is independent (no class-name collision across loaders).
     */
    static final class LoaderOverride extends ClassLoader {
        LoaderOverride(ClassLoader parent) { super(parent); }
        Class<?> define(byte[] bytes) {
            return defineClass(null, bytes, 0, bytes.length);
        }
    }

    private static final String FIXTURE = "edu.gmu.swe.knarr.bridge.fixture.SimpleCounter";

    private static byte[] readFixture(String name) throws Exception {
        Path p = Path.of("target/test-classes", name);
        Assertions.assertTrue(Files.exists(p),
                "fixture class file should have been compiled: " + p);
        return Files.readAllBytes(p);
    }

    @Test
    void galetteAloneYieldsVerifiableClass() throws Exception {
        byte[] raw = readFixture("edu/gmu/swe/knarr/bridge/fixture/SimpleCounter.class");
        byte[] g = new edu.neu.ccs.prl.galette.internal.transform.GaletteTransformer()
                .transform(raw, false);
        Assertions.assertNotNull(g);
        LoaderOverride cl = new LoaderOverride(getClass().getClassLoader());
        Class<?> c = cl.define(g);
        Assertions.assertNotNull(c);
        Assertions.assertTrue(c.getDeclaredMethods().length > 0);
    }

    @Test
    void crochetAloneYieldsVerifiableClass() throws Exception {
        byte[] raw = readFixture("edu/gmu/swe/knarr/bridge/fixture/SimpleCounter.class");
        byte[] c = new net.jonbell.crochet.transform.CrochetTransformer()
                .transform(raw, false);
        Assertions.assertNotNull(c);
        LoaderOverride cl = new LoaderOverride(getClass().getClassLoader());
        Class<?> klass = cl.define(c);
        Assertions.assertNotNull(klass);
        Assertions.assertTrue(klass.getDeclaredMethods().length > 0);
    }

    @Test
    void compositeYieldsVerifiableClass() throws Exception {
        byte[] raw = readFixture("edu/gmu/swe/knarr/bridge/fixture/SimpleCounter.class");
        byte[] out = new CompositeTransformer().transform(raw, false);
        Assertions.assertNotNull(out, "composite transformer should have modified the class");
        LoaderOverride cl = new LoaderOverride(getClass().getClassLoader());
        Class<?> klass = cl.define(out);
        Assertions.assertNotNull(klass);
        Assertions.assertTrue(klass.getDeclaredMethods().length > 0);
    }

    @Test
    void compositeClassExecutes() throws Exception {
        byte[] raw = readFixture("edu/gmu/swe/knarr/bridge/fixture/SimpleCounter.class");
        byte[] out = new CompositeTransformer().transform(raw, false);
        LoaderOverride cl = new LoaderOverride(getClass().getClassLoader());
        Class<?> klass = cl.define(out);
        Object instance = klass.getDeclaredConstructor().newInstance();
        Object result = klass.getDeclaredMethod("addOne", int.class).invoke(instance, 41);
        Assertions.assertEquals(42, result);
    }
}
