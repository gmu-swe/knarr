package anon.knarr.server.concolic.picker;

import anon.knarr.server.concolic.Input;

public class MaxBytecodePicker extends MaxConstraintsPicker {

    @Override
    public void score(Input in) {
        in.score = in.coverage.thisCount;
    }

}
