package anon.knarr.server.concolic;

import anon.knarr.server.concolic.mutator.Mutator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Log {
    private static final char SEP = ';';
    private BufferedWriter bw;
    private long start = System.currentTimeMillis();

    String[] cols = new String[] {
            "Input file", "When", "Score", "How", "Why", "Mutator",
    };

    public Log(File f) throws IOException {
        this.bw = new BufferedWriter(new FileWriter(f));

        // Header
        for (String s : cols) {
            bw.write(s);
            bw.write(SEP);
        }

        bw.newLine();
    }

    public synchronized void log(Input in, String reason, String file, Mutator m) throws IOException {
        // File
        bw.write(file);
        bw.write(SEP);

        // When
        long now = System.currentTimeMillis();
        bw.write(Long.toString(now - start));
        bw.write(SEP);

        // Score
        bw.write(Long.toString(in.score));
        bw.write(SEP);

        // How
        bw.write(in.how);
        bw.write(SEP);

        // Why
        bw.write(reason);
        bw.write(SEP);

        // Mutator
        bw.write(m != null ? m.getName() : "");
        bw.write(SEP);

        bw.newLine();

        bw.flush();
    }
}
