package edu.gmu.swe.knarr.server.concolic;

public interface Mutator {

    public static Input OUT_OF_RANGE = new Input();

    public Input mutateInput(Input in, int whatToMutate);

}
