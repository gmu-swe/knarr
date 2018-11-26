package edu.gmu.swe.knarr.server.concolic.mutator;

import edu.gmu.swe.knarr.server.Canonizer;
import edu.gmu.swe.knarr.server.ConstraintServerHandler;
import edu.gmu.swe.knarr.server.concolic.Input;
import edu.gmu.swe.knarr.server.concolic.driver.Driver;
import za.ac.sun.cs.green.expr.*;
import za.ac.sun.cs.green.expr.Operation.Operator;

import java.lang.reflect.Array;
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
public class VariableMutator extends Mutator {

    public VariableMutator(Driver driver) {
        super(driver);
    }

    @Override
    public Input mutateInput(Input in, int inputToNegate) {

        // Make a copy of the input so we can modify it
        Canonizer c = new Canonizer(in.constraints);
//        in = null;

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
                if (!v.getName().startsWith("autoVar"))
                    continue;
                if (i++ == inputToNegate) {
                    varToNegate = v;
                    break;
                }
            }
        }

        if (varToNegate == null)
            return Mutator.OUT_OF_RANGE;

        // Find value to negate
        Object valueToNegate = null;
        for (SimpleEntry<String, Object> e : sat) {
            if (e.getKey().equals(varToNegate.getName())) {
                valueToNegate = e.getValue();
                break;
            }
        }

        if (valueToNegate == null)
            return null; // Mutator.OUT_OF_RANGE;

        System.out.println(varToNegate);
        System.out.println(valueToNegate);

        Expression negatedInput = new Operation(
                Operator.NOT,
                new Operation(Operator.EQUALS, varToNegate, new BVConstant((int)valueToNegate, ((BVVariable)varToNegate).getSize()))
        );

        // Solve
        while (true) {
            // Add negated input to constraints
            HashSet<Expression> s = c.getCanonical().get(varToNegate.getName());
            if (s != null)
                s.add(negatedInput);
            c.getOrder().addLast(negatedInput);

            // Add key constraints to date
            for (Input parent = in ; parent != null && parent.newConstraint != null ; parent = parent.parent)
                c.getNotCanonical().add(parent.newConstraint);

            res = c.getExpressionMap();

            sat.clear();
            unsat.clear();
            ConstraintServerHandler.solve(res, sat, unsat);

            if (!sat.isEmpty() && unsat.isEmpty()) {
                // SAT -> generate input
                Object sol = driver.solution(sat.size());
                int i = 0;
                for (Entry<String, Object> e: sat) {
                    if (!e.getKey().startsWith("autoVar_"))
                        break;
                    Integer b = (Integer) e.getValue();
                    if (b == null)
                        break;

                    driver.interpret(sol, i++, b);
                }

                Input ret = new Input();
                ret.input = sol;
                ret.parent = in;
                ret.newConstraint = negatedInput;
                ret.how = negatedInput.toString();
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
