package edu.gmu.swe.knarr.server.concolic.picker;

import edu.gmu.swe.knarr.runtime.Coverage;
import edu.gmu.swe.knarr.server.concolic.Input;

import java.util.Collection;
import java.util.HashSet;

public abstract class Picker {
    protected Coverage current = new Coverage();

    protected Collection<Input> inCirculation = createInCirculation();
    protected HashSet<Input> outOfCirculation = new HashSet<>();

    public Input pickInput() {
        if (inCirculation.isEmpty())
            return null;

        Input ret = doPickInput();
        System.out.println("Picking new input (" + inCirculation.size() + ")");

        return ret;
    }

    protected abstract Input doPickInput();

    public void reset() {
        inCirculation.addAll(outOfCirculation);
        outOfCirculation.clear();
    }

    public void removeInput(Input in) {
        inCirculation.remove(in);
        outOfCirculation.add(in);
    }

    public boolean saveInput(Input in) {
        if (shouldSaveInput(in)) {
            inCirculation.add(in);

            // Update current coverage
            current.merge(in.coverage);

            return true;
        } else if (!current.coversTheSameAs(in.coverage)) {
            inCirculation.add(in);
            current.merge(in.coverage);

            return false;
        } else {
            return false;
        }
    }

    protected abstract boolean shouldSaveInput(Input in);

    protected abstract Collection<Input> createInCirculation();

    public Coverage getCurrentCoverage() {
        return current;
    }
}
