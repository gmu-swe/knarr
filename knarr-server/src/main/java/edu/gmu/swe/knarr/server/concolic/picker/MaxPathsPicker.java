package edu.gmu.swe.knarr.server.concolic.picker;

import edu.gmu.swe.knarr.server.concolic.Input;

import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

public class MaxPathsPicker extends MaxConstraintsPicker {

    @Override
    public Input doPickInput() {
        return (((TreeSet<Input>)inCirculation)).last();
    }

    @Override
    public void score(Input in) {
        in.score = in.coverage.countCoverage();
    }

    @Override
    protected String shouldSaveInput(Input in) {
        super.shouldSaveInput(in);

        if (!current.coversTheSameAs(in.coverage))
            return "newPath";
        else
            return null;
    }

    @Override
    protected Collection<Input> createInCirculation() {
        return new TreeSet<>(new Comparator<Input>() {
            @Override
            public int compare(Input o1, Input o2) {
                long l1 = o1.coverage.countCoverage();
                long l2 = o2.coverage.countCoverage();

                return Long.compare(l1, l2);
            }
        });
    }
}
