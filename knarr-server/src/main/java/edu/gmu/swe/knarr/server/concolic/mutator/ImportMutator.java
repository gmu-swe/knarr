package edu.gmu.swe.knarr.server.concolic.mutator;

import edu.gmu.swe.knarr.server.concolic.Input;
import edu.gmu.swe.knarr.server.concolic.driver.Driver;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

public class ImportMutator extends Mutator {
    private File dir;
    private HashSet<String> imported = new HashSet<>();

    public static int WAIT_TIME = 60*1000; // 60 seconds

    public ImportMutator(Driver driver, File dir) {
        super(driver);
        this.dir = dir;
    }

    @Override
    public Input mutateInput(Input in, int whatToMutate) {
        while (true) {
            if (this.dir != null && this.dir.listFiles() != null) {
                for (File f : this.dir.listFiles()) {
                    if (imported.contains(f.getName()))
                        continue;

                    imported.add(f.getName());
                    Input ret = new Input();
                    ret.how = "import " + f.getParentFile().getParentFile().getName() + "-" + f.getName();
                    try {
                        ret.input = driver.fromFile(f);
                    } catch (IOException e) {
                        System.out.println(e);
                        e.printStackTrace();
                        continue;
                    }

                    return ret;
                }
            }

            synchronized (this) {
                try {
                    if (this.dir == null)
                        // Block since we are not importing results
                        this.wait();
                    else
                        // Wait one minute for more input
                        this.wait(WAIT_TIME);
                } catch (InterruptedException e) {
                    continue;
                }
            }
        }
    }

}
