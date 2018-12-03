package edu.gmu.swe.knarr.server.concolic.picker;

import edu.gmu.swe.knarr.server.Canonizer;
import edu.gmu.swe.knarr.server.concolic.Input;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Variable;
import za.ac.sun.cs.green.expr.Visitor;
import za.ac.sun.cs.green.expr.VisitorException;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

public class MaxConstraintsPicker extends Picker {

    private long max = 0;
    private Input maxIn = null;
    private HashMap<Variable, Integer> maxVars = new HashMap<>();
    private HashMap<Variable, Input>   maxIns = new HashMap<>();

    @Override
    public Input doPickInput(Collection<Input> inputs) {
        if (inputs.isEmpty())
            return null;

        return (((TreeSet<Input>)inputs)).last();
    }

    public static boolean refersVar(Variable v, Expression e) {
        FindVariableVisitor visitor = new FindVariableVisitor(v);

        try {
            e.accept(visitor);
        } catch (VisitorException e1) {
            throw new Error(e1);
        }

        return visitor.found;
    }

    @Override
    public void score(Input in) {
        in.score = countConstraints(in.constraints);
    }

    @Override
    protected String shouldSaveInput(Input in, Collection<Input> ins) {
        String maxVar = "";
        String plus = "";

        boolean keepTrackOfMaxVars = false;

        if (keepTrackOfMaxVars) {
            for (Variable v : in.constraints.getVariables()) {
                HashSet<Expression> exps = in.constraints.getCanonical().get(v.toString());

                int vars = 0;
                if (exps != null)
                    vars = exps.size();

                for (Expression e : in.constraints.getNotCanonical())
                    if (refersVar(v, e))
                        vars++;

                Integer count = maxVars.get(v);
                if (count == null || vars > count) {
                    // This input maximizes constraints for a variable, save it
                    maxVars.put(v, vars);
                    Input toRemove = maxIns.remove(v);

                    // Remove the previous max input for this var, we don't care about it now
                    if (toRemove != null && !maxIns.containsValue(toRemove)) {
                        ins.remove(toRemove);
                        toRemove.input = null;
                        toRemove.constraints = null;
                    }
                    maxIns.put(v, in);
                    if (maxVar.length() < 30) {
                        if (!maxVar.isEmpty())
                            maxVar += ",";
                        maxVar += (v.toString() + "=" + vars);
                    } else {
                        plus = "...";
                    }
                }
            }
        }

        if (in.score > max) {
            max = in.score;
            maxIn = in;
            return "maxConstraints=" + in.score;
        }

        if (!maxVar.isEmpty())
            return "maxVar-"+maxVar+plus;

        return null;
    }

    @Override
    protected Collection<Input> createInCirculation() {
        return new TreeSet<>(new Comparator<Input>() {
            @Override
            public int compare(Input o1, Input o2) {
                return Long.compare(o1.score, o2.score);
            }
        });
    }

    public static int countConstraints(Canonizer c) {
        int n = 0;

        n += c.getNotCanonical().size();

        for (Collection<?> cc : c.getCanonical().values())
            n += cc.size();

        return n;
    }

    public Input getMaxInput(Variable v) {
        return maxIns.get(v);
    }

    public Input getMaxInput() {
        return maxIn;
    }

    private static class FindVariableVisitor extends Visitor {
        private Variable toFind;
        private boolean found = false;

        public FindVariableVisitor(Variable toFind) {
            this.toFind = toFind;
        }

        @Override
        public void preVisit(Variable variable) throws VisitorException {
            if (variable.equals(toFind))
                found = true;
        }
    }
}
