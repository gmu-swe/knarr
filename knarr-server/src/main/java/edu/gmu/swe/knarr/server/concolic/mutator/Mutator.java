package edu.gmu.swe.knarr.server.concolic.mutator;

import edu.gmu.swe.knarr.server.concolic.Input;
import edu.gmu.swe.knarr.server.concolic.driver.Driver;

public abstract class Mutator {
    private String name = this.getClass().getSimpleName();
    public static final Input OUT_OF_RANGE = new Input();

    protected final Driver driver;

    public Mutator(Driver driver) {
        this.driver = driver;
    }

    public abstract Input mutateInput(Input in, int whatToMutate);

    public String getName() {
        return name;
    }

    public Mutator setName(String name) {
        this.name = name;
        return this;
    }
}
