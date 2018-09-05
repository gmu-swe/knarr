package edu.gmu.swe.knarr.server.concolic.mutator;

import edu.gmu.swe.knarr.server.concolic.Input;
import edu.gmu.swe.knarr.server.concolic.driver.Driver;

public abstract class Mutator {
    public static final Input OUT_OF_RANGE = new Input();

    protected final Driver driver;

    public Mutator(Driver driver) {
        this.driver = driver;
    }

    public abstract Input mutateInput(Input in, int whatToMutate);
}
