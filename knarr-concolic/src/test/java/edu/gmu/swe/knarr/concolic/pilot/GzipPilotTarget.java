package edu.gmu.swe.knarr.concolic.pilot;

import java.io.ByteArrayInputStream;
import java.util.zip.GZIPInputStream;

/**
 * Pilot 6 target: drive {@link GZIPInputStream#readAllBytes()} with
 * symbolic bytes. Anchors the long tail of CVE-2012-2098 / CVE-2023-44487
 * style decompression-bomb + malformed-member CVEs — the header / CRC /
 * ISIZE fields are exactly the symbolic branches the concolic mutator
 * should flip.
 */
public final class GzipPilotTarget {

    static final int ITERATIONS = 10;

    /** Minimal gzip member encoding the deflated string "hi" (precomputed). */
    static final byte[] SEED = new byte[] {
            (byte) 0x1F, (byte) 0x8B, (byte) 0x08, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0xFF, (byte) 0xCB, (byte) 0xC8,
            (byte) 0x04, (byte) 0x00, (byte) 0xAC, (byte) 0x2A,
            (byte) 0x93, (byte) 0xD8, (byte) 0x02, (byte) 0x00,
            (byte) 0x00, (byte) 0x00
    };

    public static void main(String[] args) {
        new PilotRunner(
                "GZIP",
                "gz",
                SEED.clone(),
                GzipPilotTarget::parseOne,
                ITERATIONS,
                PilotRunner.parseMutator(args)).run();
    }

    static String parseOne(byte[] tagged) {
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(tagged))) {
            byte[] out = gz.readAllBytes();
            return "DECODED bucket=" + sizeBucket(out.length);
        } catch (java.util.zip.ZipException ze) {
            return "GZIP_EX " + ze.getClass().getSimpleName();
        } catch (java.io.IOException ioe) {
            return "GZIP_EX " + ioe.getClass().getSimpleName();
        } catch (Throwable t) {
            return "CRASH " + t.getClass().getSimpleName();
        }
    }

    private static int sizeBucket(int n) {
        if (n == 0) return 0;
        if (n < 8) return 1;
        if (n < 64) return 2;
        return 3;
    }
}
