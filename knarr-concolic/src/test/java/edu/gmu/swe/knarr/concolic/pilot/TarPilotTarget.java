package edu.gmu.swe.knarr.concolic.pilot;

import java.io.ByteArrayInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Pilot 1 target: feed a symbolic 512-byte buffer (one Tar header block)
 * into {@link TarArchiveInputStream#getNextEntry()} and report distinct
 * branch outcomes + branch-coverage across a short concolic loop.
 *
 * <p>Mutator is selected by the {@code --mutator=<struct|random|solver>}
 * CLI flag; see {@link PilotRunner} for what each one does.
 *
 * <p>The {@code solver} mutator was previously blocked by a sort-mismatch
 * in Green's Z3 translator and a GT/LT swap in {@code recordIntCmp}.
 * As of commits {@code 744e1d5} / {@code 72a2655} the byte-array pilot
 * constraint tree solves to a 1037-entry model, so we can now loop:
 * tag, parse, dump, solve, apply model, reparse.
 */
public final class TarPilotTarget {

    static final int HEADER_SIZE = 512;
    static final int ITERATIONS = 10;

    public static void main(String[] args) {
        byte[] buf = new byte[HEADER_SIZE];
        byte[] magic = "ustar\0".getBytes();
        System.arraycopy(magic, 0, buf, 257, magic.length);

        new PilotRunner(
                "TAR",
                "tar",
                buf,
                TarPilotTarget::parseOne,
                ITERATIONS,
                PilotRunner.parseMutator(args)).run();
    }

    static String parseOne(byte[] tagged) {
        try (TarArchiveInputStream tin = new TarArchiveInputStream(
                new ByteArrayInputStream(tagged))) {
            Object entry = tin.getNextEntry();
            if (entry == null) return "NULL_ENTRY";
            return "ENTRY_READ bucket=" + signalLen(entry);
        } catch (Throwable t) {
            return "EX " + t.getClass().getSimpleName();
        }
    }

    private static int signalLen(Object entry) {
        try {
            String name = entry.getClass().getMethod("getName").invoke(entry).toString();
            int len = name.length();
            if (len == 0) return 0;
            if (len < 8) return 1;
            if (len < 64) return 2;
            return 3;
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }
}
