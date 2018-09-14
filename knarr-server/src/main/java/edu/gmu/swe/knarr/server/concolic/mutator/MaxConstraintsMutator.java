package edu.gmu.swe.knarr.server.concolic.mutator;

import edu.gmu.swe.knarr.runtime.Coverage;
import edu.gmu.swe.knarr.server.Canonizer;
import edu.gmu.swe.knarr.server.ConstraintServerHandler;
import edu.gmu.swe.knarr.server.concolic.Input;
import edu.gmu.swe.knarr.server.concolic.driver.Driver;
import edu.gmu.swe.knarr.server.concolic.picker.MaxConstraintsPicker;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.Variable;

import java.util.*;
import java.util.Map.Entry;

/**
 * Mutates contraints using coverage info:
 * - Check if negating a constraint would improve coverage
 * - Negates that constraint, drops all later, and solves:
 *   # SAT: Use that input
 *   # UNSAT: Give up
 */
public class MaxConstraintsMutator extends Mutator {

    private MaxConstraintsPicker picker;

    public MaxConstraintsMutator(Driver d, MaxConstraintsPicker picker) {
        super(d);
        this.picker = picker;
    }

    @Override
    public Input mutateInput(Input in, int which) {

        // Make a copy of the input so we can modify it
        Canonizer c = new Canonizer(in.constraints);
        Coverage cov = in.coverage;

        Expression toNegate = null;
        Coverage newCoverage = null;

        // Compute key constraints to date
        HashSet<Expression>  keyConstraints = new HashSet<>();
        for (Input parent = in ; parent != null && parent.newConstraint != null ; parent = parent.parent)
            keyConstraints.add(parent.newConstraint);

        // Pick pair of variables
        LinkedList<Expression> added = new LinkedList<>();
        {
            if (in == picker.getMaxInput())
                System.out.print("");

            int i = 0;
            for (Variable v : in.constraints.getVariables()) {
                if (which == i) {

                    // Remove all v from input
                    HashSet<Expression> exps = c.getCanonical().get(v.toString());

                    if (exps == null)
                        continue;

                    exps.clear();

                    {
                        Iterator<Expression> iter = c.getNotCanonical().iterator();
                        while (iter.hasNext()) {
                            Expression e = iter.next();
                            if (MaxConstraintsPicker.refersVar(v, e))
                                iter.remove();
                        }
                    }

                    // Add max v from picker
                    Input maxIn = picker.getMaxInput(v);
                    c.getCanonical().get(v.toString()).addAll(maxIn.constraints.getCanonical().get(v.toString()));

                    {
                        Iterator<Expression> iter = maxIn.constraints.getNotCanonical().iterator();
                        while (iter.hasNext()) {
                            Expression e = iter.next();
                            if (MaxConstraintsPicker.refersVar(v, e)) {
                                added.addLast(e);
                                c.getNotCanonical().add(e);
                            }
                        }
                    }

                    break;
                } else {
                    i++;
                }
            }

            if (which != i)
                return Mutator.OUT_OF_RANGE;
        }

        solving: while (true) {
//            // Add key constraints to date
//            for (Expression e : keyConstraints)
//                c.getNotCanonical().add(new Operation(Operator.NOT, e));

            // Solve
            Map<String, Expression> res = c.getExpressionMap();
            ArrayList<AbstractMap.SimpleEntry<String, Object>> sat = new ArrayList<>();
            HashSet<String> unsat = new HashSet<>();

            sat.clear();
            unsat.clear();
            ConstraintServerHandler.solve(res, sat, unsat);

            if (!sat.isEmpty()) {
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
                return ret;
            } else if (!unsat.isEmpty()) {
                // UNSAT, maybe we can still find new paths

                if (!added.isEmpty()) {
                    Iterator<Expression> iter = added.descendingIterator();
                    while (iter.hasNext()) {
                        Expression e = iter.next();
                        if (unsat.contains(e.toString())) {
                            iter.remove();
                            c.getNotCanonical().remove(e);
                            continue solving;
                        }
                    }
                }

                System.out.println("\tUNSAT");
                return null;

//                System.out.println(toNegate);
//                System.out.println(negated);
//                System.out.println(unsat);
//
//                if (pathSensitive)
//                    return null;
//
//                // Find earliest constraint in UNSAT core
//                newOrder = new LinkedList<>();
//                toRemove = new LinkedList<>();
//                found = false;
//                for (Expression e : c.getOrder()) {
//                    if (unsat.contains(e.toString()))
//                        found = true;
//
//                    (!found ? newOrder : toRemove).addLast(e);
//                }
//
//                // Drop it and all later constraints
//                for (HashSet<Expression> es : c.getCanonical().values())
//                    es.removeAll(toRemove);
//
//                for (HashSet<Expression> es : c.getConstArrayInits().values())
//                    es.removeAll(toRemove);
//
//                c.getNotCanonical().removeAll(toRemove);
//
//                // Try again
//                continue;

            } else {
                throw new Error("Should never happen");
            }

        }
    }
}
