package edu.gmu.swe.knarr.server.concolic.picker;

import edu.gmu.swe.knarr.server.Canonizer;
import edu.gmu.swe.knarr.server.concolic.Input;

import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

public class MaxConstraintsPicker extends Picker {

    int max = 0;

    @Override
    public Input doPickInput() {
        return (((TreeSet<Input>)inCirculation)).last();
    }

    @Override
    protected boolean shouldSaveInput(Input in) {
        int n = countConstraints(in.constraints);

        if (n > max) {
            max = n;
            return true;
        }

        return false;
    }

    @Override
    protected Collection<Input> createInCirculation() {
        return new TreeSet<>(new Comparator<Input>() {
            @Override
            public int compare(Input o1, Input o2) {
                long l1 = countConstraints(o1.constraints);
                long l2 = countConstraints(o2.constraints);

                return Long.compare(l1, l2);
            }
        });
    }

    private static int countConstraints(Canonizer c) {
        int n = 0;

        n += c.getNotCanonical().size();

        for (Collection<?> cc : c.getCanonical().values())
            n += cc.size();

        return n;
    }
}
