package anon.knarr.server.concolic.mutator;

import anon.knarr.server.concolic.Input;
import anon.knarr.server.concolic.driver.Driver;

public class FixedOutputMutator extends Mutator {

    int[][] inputs;

    public FixedOutputMutator(Driver driver, int[] ... inputs) {
        super(driver);
        this.inputs = inputs;
    }

    @Override
    public Input mutateInput(Input in, int whatToMutate) {
        if (whatToMutate >= inputs.length)
            return Mutator.OUT_OF_RANGE;

        int[] ints = inputs[whatToMutate];
        Object sol = driver.solution(ints.length);

        for (int i = 0 ; i < ints.length ; i++) {
            driver.interpret(sol, i, ints[i]);
        }

        Input ret = new Input();
        ret.input = sol;
        ret.parent = in;
        ret.how = "fixed";
        return ret;
    }
}
