package edu.gmu.swe.knarr.server.concolic;

public interface Mutator {

    public Input mutateInput(Input in, int whatToMutate);

}
