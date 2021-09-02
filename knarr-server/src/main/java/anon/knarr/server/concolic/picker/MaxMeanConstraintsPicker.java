package anon.knarr.server.concolic.picker;

import anon.knarr.server.concolic.Input;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Variable;

import java.util.*;

public class MaxMeanConstraintsPicker extends MaxConstraintsPicker {
    @Override
    public void score(Input in) {
        long total = 0;

        for (Variable v : in.constraints.getVariables()) {
            HashSet<Expression> exps = in.constraints.getCanonical().get(v.toString());

            if (exps != null)
                total += exps.size();

            for (Expression e : in.constraints.getNotCanonical())
                if (refersVar(v, e))
                    total++;
        }

        in.score = (int) (total / in.constraints.getVariables().size());
    }
}
