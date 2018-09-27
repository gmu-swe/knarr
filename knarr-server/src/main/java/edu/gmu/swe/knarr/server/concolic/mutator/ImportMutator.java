package edu.gmu.swe.knarr.server.concolic.mutator;

import edu.gmu.swe.knarr.server.concolic.Input;
import edu.gmu.swe.knarr.server.concolic.driver.Driver;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

public class ImportMutator extends Mutator {
    private File dir;
    private HashSet<String> imported = new HashSet<>();

    public ImportMutator(Driver driver, File dir) {
        super(driver);
        this.dir = dir;
    }

    @Override
    public Input mutateInput(Input in, int whatToMutate) {

        for (File f : this.dir.listFiles()) {
            if (imported.contains(f.getName()))
                continue;

            imported.add(f.getName());
            Input ret = new Input();
            ret.how = "import " + f.getName();
            try {
                ret.input = driver.fromFile(f);
            } catch (IOException e) {
                System.out.println(e);
                e.printStackTrace();
                continue;
            }

            return ret;
        }

        return Mutator.OUT_OF_RANGE;
    }

}
