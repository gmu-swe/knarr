package edu.gmu.swe.knarr.server.concolic;

import edu.gmu.swe.knarr.runtime.Coverage;
import edu.gmu.swe.knarr.server.Canonizer;
import edu.gmu.swe.knarr.server.concolic.driver.Driver;
import edu.gmu.swe.knarr.server.concolic.picker.MaxConstraintsPicker;
import za.ac.sun.cs.green.expr.Expression;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.nio.file.Paths;
import java.util.HashMap;

public class Input {
    public Canonizer constraints;
    public Coverage coverage;
    public Object input;
    public int nth;
    public String how;
    public int score;

    public Input parent = null;
    public Expression newConstraint;

    private static String FORMAT = "id:%06d,src:%06d,score=%d,op:%s,%s";

    public void toFiles(File dirToSave, Driver driver, String reason) {
        URI queue = Paths.get(dirToSave.getAbsolutePath(), "queue").toUri();
        File q = new File(queue);
        q.mkdirs();
        try {
//            save(constraints, "constraints_" + nth, dirToSave);
//            save(coverage, "coverage_" + nth, dirToSave);
            int score = this.score;
            String inputFilename;
            if (this.parent != null)
                inputFilename = String.format(FORMAT, this.nth, this.parent.nth, score, this.how, reason);
            else
                inputFilename = String.format(FORMAT, this.nth, 0, score, this.how, reason);
            save(input, inputFilename, q, driver);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private void save(Object toSave, String fileName, File dirToSave) throws IOException {
        URI uri = Paths.get(dirToSave.getAbsolutePath(), fileName).toUri();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(uri)))) {
            oos.writeObject(toSave);
        }
    }

    private void save(Object toSave, String fileName, File dirToSave, Driver d) throws IOException {
        URI uri = Paths.get(dirToSave.getAbsolutePath(), fileName).toUri();
        d.toFile(toSave, new File(uri));
    }
}
