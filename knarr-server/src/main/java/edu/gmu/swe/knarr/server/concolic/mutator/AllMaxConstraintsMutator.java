package edu.gmu.swe.knarr.server.concolic.mutator;

import edu.gmu.swe.knarr.runtime.Coverage;
import edu.gmu.swe.knarr.server.Canonizer;
import edu.gmu.swe.knarr.server.ConstraintServerHandler;
import edu.gmu.swe.knarr.server.concolic.Input;
import edu.gmu.swe.knarr.server.concolic.driver.Driver;
import edu.gmu.swe.knarr.server.concolic.picker.MaxConstraintsPicker;
import za.ac.sun.cs.green.expr.Expression;
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
public class AllMaxConstraintsMutator extends Mutator {

    private MaxConstraintsPicker picker;

    public AllMaxConstraintsMutator(Driver d, MaxConstraintsPicker picker) {
        super(d);
        this.picker = picker;
    }

    private Input last;

    @Override
    public Input mutateInput(Input in, int which) {

//        if (which > 0 || (last != null && in != last))
//            return Mutator.OUT_OF_RANGE;

        last = in;

        // Make a copy of the input so we can modify it
        Canonizer c = new Canonizer();

        Expression toNegate = null;
        Coverage newCoverage = null;

        // Pick all max constraints for each variable
        ArrayList<Variable> vars = new ArrayList<>();
        synchronized (in.constraints) {
            vars.addAll(in.constraints.getVariables());
        }

        for (Variable v : vars) {
            if (!v.getName().startsWith("autoVar"))
                continue;

            // Add max v from picker
            Input maxIn = picker.getMaxInput(v);

            synchronized (maxIn.constraints) {
                if (maxIn.constraints.getCanonical().get(v.toString()) == null)
                    maxIn.constraints.getCanonical().put(v.toString(), new HashSet<Expression>());

                c.getCanonical().put(v.toString(), new HashSet<Expression>());
                c.getCanonical().get(v.toString()).addAll(maxIn.constraints.getCanonical().get(v.toString()));

                {
                    Iterator<Expression> iter = maxIn.constraints.getNotCanonical().iterator();
                    while (iter.hasNext()) {
                        Expression e = iter.next();
                        if (MaxConstraintsPicker.refersVar(v, e)) {
                            c.getNotCanonical().add(e);
                        }
                    }
                }
            }
        }

        // Solve
        Map<String, Expression> res = c.getExpressionMap();
        ArrayList<AbstractMap.SimpleEntry<String, Object>> sat = new ArrayList<>();
        HashSet<String> unsat = new HashSet<>();

        solving: while (true) {
            System.out.println(res.size());
//            // Add key constraints to date
//            for (Expression e : keyConstraints)
//                c.getNotCanonical().add(new Operation(Operator.NOT, e));


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
                ret.how = "allMaxConstraints";
                return ret;
            } else if (!unsat.isEmpty()) {
                // UNSAT, maybe we can still find new paths

                for (String s : unsat) {
                    res.remove(s);
                }
                continue solving;

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
                System.out.println("Timeout?");
                return null;
//                throw new Error("Should never happen");
            }

        }
    }
}
