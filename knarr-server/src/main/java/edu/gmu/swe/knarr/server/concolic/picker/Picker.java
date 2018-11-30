package edu.gmu.swe.knarr.server.concolic.picker;

import edu.gmu.swe.knarr.runtime.Coverage;
import edu.gmu.swe.knarr.server.concolic.Input;
import edu.gmu.swe.knarr.server.concolic.mutator.Mutator;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public abstract class Picker {
    protected Coverage current = Coverage.newCoverage();

    protected HashMap<Mutator, Collection<Input>> inputs = new HashMap<>();

    public void initMutators(Mutator[] mutators) {
        for (Mutator m : mutators)
            inputs.put(m, createInCirculation());
    }

    public synchronized Input pickInput(Mutator m) {

        Input ret;
        while (true) {
            Collection<Input> ins = inputs.get(m);
            ret = doPickInput(ins);
            if (ret == null) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    continue;
                }
            } else {
                break;
            }
        }

        return ret;
    }

    protected abstract Input doPickInput(Collection<Input> inputs);

    public synchronized void removeInput(Input in, Mutator mutator) {
        inputs.get(mutator).remove(in);
    }

    public synchronized String saveInput(Mutator m, Input in) {
        String reason;

        if ((reason = shouldSaveInput(in, m != null ? inputs.get(m) : null)) != null) {
            for (Collection<Input> ins : inputs.values())
                ins.add(in);

            this.notifyAll();

            // Update current coverage
            current.merge(in.coverage);

            return reason;
        }

        if (!current.coversTheSameCodeAs(in.coverage)) {
            for (Collection<Input> ins : inputs.values())
                ins.add(in);
            current.merge(in.coverage);

            this.notifyAll();

            return "newCode";
        }

//        int score = MaxConstraintsPicker.countConstraints(in.constraints);
//        if (score < threshold)
            return null;

//        if (!current.coversTheSameAs(in.coverage)) {
//            inCirculation.add(in);
//            current.merge(in.coverage);
//
//            return "newPath";
//        } else {
//            return null;
//        }
    }

    public void score(Input in) {
        in.score = 0;
    }

    protected abstract String shouldSaveInput(Input in, Collection<Input> ins);

    protected abstract Collection<Input> createInCirculation();

    public Coverage getCurrentCoverage() {
        return current;
    }
}
