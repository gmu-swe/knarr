package edu.gmu.swe.knarr.server.concolic.picker;

import edu.gmu.swe.knarr.server.concolic.Input;

public class MaxBytecodePicker extends MaxConstraintsPicker {

    @Override
    public void score(Input in) {
        in.score = in.coverage.thisCount;
    }

}
