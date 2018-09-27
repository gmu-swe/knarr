package edu.gmu.swe.knarr.server.concolic.picker;

import edu.gmu.swe.knarr.runtime.Coverage;
import edu.gmu.swe.knarr.server.concolic.Input;

import java.util.Collection;
import java.util.HashSet;

public abstract class Picker {
    protected Coverage current = Coverage.newCoverage();

    protected Collection<Input> inCirculation = createInCirculation();
    protected HashSet<Input> outOfCirculation = new HashSet<>();

    protected int threshold = Integer.MAX_VALUE;

    public Input pickInput() {
        if (inCirculation.isEmpty())
            return null;

        Input ret = doPickInput();

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

    public String saveInput(Input in) {
        String reason;
        if ((reason = shouldSaveInput(in)) != null) {
            inCirculation.add(in);

            // Update current coverage
            current.merge(in.coverage);

            return reason;
        }

        if (!current.coversTheSameCodeAs(in.coverage)) {
            inCirculation.add(in);
            current.merge(in.coverage);

            return "newCode";
        }

//        int score = MaxConstraintsPicker.countConstraints(in.constraints);
//        if (score < threshold)
//            return null;

        if (!current.coversTheSameAs(in.coverage)) {
            inCirculation.add(in);
            current.merge(in.coverage);

            return "newPath";
        } else {
            return null;
        }
    }

    public void score(Input in) {
        in.score = 0;
    }

    protected abstract String shouldSaveInput(Input in);

    protected abstract Collection<Input> createInCirculation();

    public Coverage getCurrentCoverage() {
        return current;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public int getThreshold() {
        return this.threshold;
    }
}
