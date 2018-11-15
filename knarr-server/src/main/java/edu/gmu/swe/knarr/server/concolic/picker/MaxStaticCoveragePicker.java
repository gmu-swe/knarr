package edu.gmu.swe.knarr.server.concolic.picker;

import edu.gmu.swe.knarr.server.concolic.Input;

import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

public class MaxStaticCoveragePicker extends Picker {

    @Override
    public Input doPickInput() {
        return (((TreeSet<Input>)inCirculation)).last();
    }

    @Override
    protected String shouldSaveInput(Input in) {
        if (!current.coversTheSameCodeAs(in.coverage))
            return "newCode";
        else
            return null;
    }

    @Override
    protected Collection<Input> createInCirculation() {
        return new TreeSet<>(new Comparator<Input>() {
            @Override
            public int compare(Input o1, Input o2) {
                long l1 = o1.coverage.countCodeCoverage();
                long l2 = o2.coverage.countCodeCoverage();

                int ret = Long.compare(l1, l2);

                if (ret == 0) {
                    l1 = o1.coverage.countCoverage();
                    l2 = o2.coverage.countCoverage();

                    return Long.compare(l1, l2);
                }

                return ret;
            }
        });
    }

    @Override
    public void score(Input in) {
        in.score = (int) in.coverage.countCodeCoverage();
    }
}
