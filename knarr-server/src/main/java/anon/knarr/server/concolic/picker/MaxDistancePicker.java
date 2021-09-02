package anon.knarr.server.concolic.picker;

import anon.knarr.server.concolic.Input;
import anon.knarr.server.Canonizer;

import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

public class MaxDistancePicker extends Picker {

    int max = -1;

    @Override
    public Input doPickInput(Collection<Input> inputs) {
        return (((TreeSet<Input>)inputs)).last();
    }

    private static int distance(Input in) {
        int ret = 0;

        while (in.parent != null) {
            ret++;
            in = in.parent;
        }

        return ret;
    }

    @Override
    protected String shouldSaveInput(Input in, Collection<Input> ins) {
        int n = distance(in);

        if (n > max) {
            max = n;
            return "maxDistance";
        }

        return null;
    }

    @Override
    protected Collection<Input> createInCirculation() {
        return new TreeSet<>(new Comparator<Input>() {
            @Override
            public int compare(Input o1, Input o2) {
                long l1 = distance(o1);
                long l2 = distance(o2);

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
