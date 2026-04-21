package edu.gmu.swe.knarr.bridge;

import edu.neu.ccs.prl.galette.internal.transform.GaletteTransformer;
import edu.neu.ccs.prl.crochet.transform.CrochetTransformer;

/**
 * Composes Galette's and CROCHET's class-file transformers into a single
 * pass. Galette runs first so it sees original bytecode (shadow methods
 * get added, tag frames get plumbed); CROCHET then runs over the
 * already-instrumented output, adding its checkpoint/rollback surface on
 * top.
 *
 * <p>This is the lynchpin of the dual-instrumented JDK image required by
 * the CROCHET-driven concolic test suite. See
 * {@code designs/dual-instrumentation.md}.
 */
public final class CompositeTransformer {

    private final GaletteTransformer galette;
    private final CrochetTransformer crochet;

    public CompositeTransformer() {
        this.galette = new GaletteTransformer();
        this.crochet = new CrochetTransformer();
    }

    /**
     * Applies Galette then CROCHET. Returns {@code null} if both passes
     * would have left the class unchanged (Galette's convention); if only
     * one of them declined, the other's output is returned.
     */
    public byte[] transform(byte[] classFileBuffer, boolean isHostedAnonymous) {
        byte[] galetteBytes = galette.transform(classFileBuffer, isHostedAnonymous);
        byte[] forCrochet = galetteBytes == null ? classFileBuffer : galetteBytes;
        byte[] crochetBytes = crochet.transform(forCrochet, isHostedAnonymous);
        byte[] afterCrochet = crochetBytes != null ? crochetBytes : galetteBytes;
        byte[] base = afterCrochet != null ? afterCrochet : classFileBuffer;
        byte[] stubbed = SkippedSurfaceStub.addStubIfMissing(base);
        if (stubbed != base) {
            return stubbed;
        }
        return afterCrochet;
    }

    /**
     * Variant that accepts a {@link ClassLoader} for CROCHET's deferred
     * reflection filter. Galette does not require a loader hint.
     */
    public byte[] transform(byte[] classFileBuffer, boolean isHostedAnonymous, ClassLoader loader) {
        byte[] galetteBytes = galette.transform(classFileBuffer, isHostedAnonymous);
        byte[] forCrochet = galetteBytes == null ? classFileBuffer : galetteBytes;
        byte[] crochetBytes = crochet.transform(forCrochet, isHostedAnonymous, loader);
        byte[] afterCrochet = crochetBytes != null ? crochetBytes : galetteBytes;
        byte[] base = afterCrochet != null ? afterCrochet : classFileBuffer;
        byte[] stubbed = SkippedSurfaceStub.addStubIfMissing(base);
        if (stubbed != base) {
            return stubbed;
        }
        return afterCrochet;
    }
}
