package edu.gmu.swe.knarr.server.concolic;

import edu.gmu.swe.knarr.runtime.Coverage;
import edu.gmu.swe.knarr.server.Canonizer;
import edu.gmu.swe.knarr.server.concolic.driver.Driver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.nio.file.Paths;

public class Input {
    public Canonizer constraints;
    public Coverage coverage;
    public Object input;

    public void toFiles(File dirToSave, int nth, Driver driver) {
        try {
            save(constraints, "constraints_" + nth, dirToSave);
            save(coverage, "coverage_" + nth, dirToSave);
            save(input, "input_" + nth, dirToSave, driver);
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
