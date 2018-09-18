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

    private int max = 0;
    private Input maxIn = null;
    private HashMap<Variable, Integer> maxVars = new HashMap<>();
    private HashMap<Variable, Input>   maxIns = new HashMap<>();

    @Override
    public Input doPickInput() {
        return (((TreeSet<Input>)inCirculation)).last();
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
    protected String shouldSaveInput(Input in) {
        int n = countConstraints(in.constraints);

        String maxVar = "";
        String plus = "";
        for (Variable v : in.constraints.getVariables()) {
            HashSet<Expression> exps = in.constraints.getCanonical().get(v.toString());

            if (exps == null)
                continue;

            int vars = exps.size();

            for (Expression e : in.constraints.getNotCanonical())
                if (refersVar(v, e))
                    vars++;

            Integer count = maxVars.get(v);
            if (count == null || vars > count) {
                maxVars.put(v, vars);
                Input toRemove = maxIns.get(v);
                if (toRemove != null && countConstraints(toRemove.constraints) < threshold) {
                    inCirculation.remove(toRemove);
                    outOfCirculation.remove(toRemove);
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

        if (n > max) {
            max = n;
            maxIn = in;
            return "maxConstraints=" + n;
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
                long l1 = countConstraints(o1.constraints);
                long l2 = countConstraints(o2.constraints);

                return Long.compare(l1, l2);
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
