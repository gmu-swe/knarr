package edu.gmu.swe.knarr.concolic.pilot;

import java.io.ByteArrayInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

/**
 * Pilot 3 target: drive commons-compress
 * {@link ZipArchiveInputStream#getNextEntry()} with a synthesised 1KB ZIP
 * header buffer. Mirrors the coarse-outcome-bucket pattern of
 * {@link TarPilotTarget}.
 *
 * <p>Historically anchors the long tail of zip-bomb / malformed-CDE CVEs
 * (CVE-2018-11771, CVE-2023-42503, etc.) — the parser fans out into
 * many distinct exception classes on small header-field mutations, which
 * is exactly the signal the concolic mutator can chase.
 */
public final class ZipPilotTarget {

    static final int BUF_SIZE = 1024;
    static final int ITERATIONS = 10;

    public static void main(String[] args) {
        byte[] buf = new byte[BUF_SIZE];
        // Local file header magic "PK\x03\x04" at offset 0; zero padding.
        buf[0] = 'P';
        buf[1] = 'K';
        buf[2] = 0x03;
        buf[3] = 0x04;

        new PilotRunner(
                "ZIP",
                "zip",
                buf,
                ZipPilotTarget::parseOne,
                ITERATIONS,
                PilotRunner.parseMutator(args)).run();
    }

    static String parseOne(byte[] tagged) {
        try (ZipArchiveInputStream zin = new ZipArchiveInputStream(
                new ByteArrayInputStream(tagged))) {
            ZipArchiveEntry entry = zin.getNextZipEntry();
            if (entry == null) return "NULL_ENTRY";
            return "ENTRY_READ";
        } catch (Throwable t) {
            return "EX " + t.getClass().getSimpleName();
        }
    }
}
