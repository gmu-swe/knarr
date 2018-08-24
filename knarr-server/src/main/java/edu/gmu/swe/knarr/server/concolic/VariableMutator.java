package edu.gmu.swe.knarr.server.concolic;

import edu.gmu.swe.knarr.server.Canonizer;
import edu.gmu.swe.knarr.server.ConstraintServerHandler;
import za.ac.sun.cs.green.expr.*;
import za.ac.sun.cs.green.expr.Operation.Operator;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Mutates variable i by:
 * - Solve current constraints to get a solution for i
 * - Add constraint (i != solution)
 * - Solve again (1):
 *   - SAT: Generate input
 *   - UNSAT:
 *      # Find out latest constraint in UNSAT core
 *      # Drop it (and all constraints after)
 *      # Re-add (i != solution)
 *      # Go to (1)
 */
public class VariableMutator implements Mutator {

    @Override
    public Input mutateInput(Input in, int inputToNegate) {

        // Make a copy of the input so we can modify it
        Canonizer c = new Canonizer();
        for (Entry<String, HashSet<Expression>> entry : in.constraints.getCanonical().entrySet())
            c.getCanonical().put(entry.getKey(), new HashSet<>(entry.getValue()));

        for (Entry<String, HashSet<Expression>> entry : in.constraints.getConstArrayInits().entrySet())
            c.getConstArrayInits().put(entry.getKey(), new HashSet<>(entry.getValue()));

        c.getNotCanonical().addAll(in.constraints.getNotCanonical());
        c.getVariables().addAll(in.constraints.getVariables());
        c.getExpressionMap().putAll(in.constraints.getExpressionMap());
        c.getOrder().addAll(in.constraints.getOrder());

        in = null;

        Map<String, Expression> res = c.getExpressionMap();
        ArrayList<SimpleEntry<String, Object>> sat = new ArrayList<>();
        HashSet<String> unsat = new HashSet<>();
        ConstraintServerHandler.solve(res, sat, unsat);

        // Solve the initial constraints
        // TODO maybe we can cache this to avoid one solver call
        if (sat.isEmpty())
            throw new Error("UNSAT constraints to start with");

        // Find variable holding input to negate
        Variable varToNegate = null;
        {
            int i = 0;
            for (Variable v : c.getVariables()) {
                if (i++ == inputToNegate) {
                    varToNegate = v;
                    break;
                }
            }
        }

        if (varToNegate == null)
            return null;

        // Find value to negate
        Object valueToNegate = null;
        for (SimpleEntry<String, Object> e : sat) {
            if (e.getKey().equals(varToNegate.getName())) {
                valueToNegate = e.getValue();
                break;
            }
        }

        if (valueToNegate == null)
            return null;

        // Add negated input to constraints
        Expression negatedInput = new Operation(
                Operator.NOT,
                new Operation(Operator.EQUALS, varToNegate, new BVConstant((int)valueToNegate, ((BVVariable)varToNegate).getSize()))
        );

        // Solve
        while (true) {
            c.getCanonical().get(varToNegate.getName()).add(negatedInput);
            c.getOrder().addLast(negatedInput);
            c.getExpressionMap().put(negatedInput.toString(), negatedInput);

            res = c.getExpressionMap();

            sat.clear();
            unsat.clear();
            ConstraintServerHandler.solve(res, sat, unsat);

            if (!sat.isEmpty()) {
                // SAT -> generate input
                byte[] buf = new byte[sat.size()];
                int i = 0;
                for (Entry<String, Object> e: sat) {
                    if (!e.getKey().startsWith("autoVar_"))
                        break;
                    Integer b = (Integer) e.getValue();
                    if (b == null)
                        break;

                    buf[i++] = b.byteValue();
                }

                Input ret = new Input();
                ret.input = buf;
                return ret;
            } else if (!unsat.isEmpty()) {
                // UNSAT

                // Find latest constraint in UNSAT core
                boolean found = false;
                LinkedList<Expression> newOrder = new LinkedList<>();
                LinkedList<Expression> toRemove = new LinkedList<>();
                for (Expression e : c.getOrder()) {
                    if (unsat.contains(e.toString())) {
                        found = true;
                    }

                    (!found ? newOrder : toRemove).addLast(e);
                }

                // Remove it and all later constraints
                for (HashSet<Expression> es : c.getCanonical().values())
                    es.removeAll(toRemove);

                for (HashSet<Expression> es : c.getConstArrayInits().values())
                    es.removeAll(toRemove);

                c.getNotCanonical().removeAll(toRemove);

                // Try again
                continue;
            } else {
                return null;
            }
        }
    }
}
