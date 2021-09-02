package anon.knarr.server.concolic.mutator;

import anon.knarr.runtime.Coverage;
import anon.knarr.server.ConstraintServerHandler;
import anon.knarr.server.concolic.Input;
import anon.knarr.server.concolic.driver.Driver;
import anon.knarr.server.Canonizer;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.UnaryOperation;

import java.util.*;
import java.util.Map.Entry;

/**
 * Mutates contraints using coverage info:
 * - Check if negating a constraint would improve coverage
 * - Negates that constraint, drops all later, and solves:
 *   # SAT: Use that input
 *   # UNSAT: Give up
 */
public class ConstraintMutator extends Mutator {

    private Coverage master;
    private boolean reverseDirection;
    private boolean pathSensitive;
    private boolean breakLoops;

    private HashMap<Input, Integer> memory = new HashMap<>();

    public ConstraintMutator(Driver d, Coverage master, boolean reverse, boolean pathSensitive, boolean breakLoops) {
        super(d);
        this.master = master;
        this.reverseDirection = reverse;
        this.pathSensitive = pathSensitive;
        this.breakLoops = breakLoops;
    }

    private HashMap<Input, Set<String>> tried = new HashMap<>();

    @Override
    public Input mutateInput(Input in, int which) {
        Integer m = memory.get(in);
        if (m != null && m > which) {
            which += m;
        }

        memory.put(in, which);

        // Make a copy of the input so we can modify it
        Canonizer c = new Canonizer(in.constraints);
        Coverage cov = in.coverage;

        Expression toNegate = null;
        Coverage newCoverage = null;

        // Compute key constraints to date
        HashSet<Expression>  keyConstraints = new HashSet<>();
        for (Input parent = in ; parent != null && parent.newConstraint != null ; parent = parent.parent)
            keyConstraints.add(parent.newConstraint);

        // Pick constraint
        {
            int i = 0;
            Iterator<Expression> iter = (reverseDirection ? c.getOrder().descendingIterator() : c.getOrder().iterator());
            while (iter.hasNext()) {
                if (i > (c.getOrder().size() / 2))
                    // Only search half, other mutators will search other half
                    return OUT_OF_RANGE;

                newCoverage = null;
                Expression e  = iter.next();
                if (e.metadata != null && (!this.breakLoops || (this.breakLoops && ((Coverage.BranchData)e.metadata).breaksLoop)) && !keyConstraints.contains(e)) {

                    Set<String> tried = this.tried.get(in);
                    if (tried == null)  {
                        tried = new HashSet<>();
                        this.tried.put(in, tried);
                    }

                    if (tried.contains(e.toString()))
                        continue;

                    // This expression was used on a jump
                    Coverage.BranchData branches = (Coverage.BranchData) e.metadata;
                    Integer id = (pathSensitive ? branches.notTakenPath : branches.notTakenCode);
                    toNegate = e;
                    newCoverage = Coverage.newCoverage();
                    if (pathSensitive)
                        newCoverage.setPath(id);
                    else
                        newCoverage.setCode(id);

                    // Check if it improves coverage
                    if (!master.coversTheSameAs(newCoverage)) {
                        if (i++ == which) {
                            System.out.println(id + " " + e);
                            tried.add(e.toString());
                            break;
                        }
//                    } else {
//                        System.out.print("");
                    }
                }
                newCoverage = null;
            }
        }

        if (newCoverage == null) {
            System.out.println("\tOut of range");
            return OUT_OF_RANGE;
        }

        // Drop constraint to negate (and later)
        boolean found = false;
        LinkedList<Expression> newOrder = new LinkedList<>();
        LinkedList<Expression> toRemove = new LinkedList<>();
        for (Expression e : c.getOrder()) {
            if (!found && (e == toNegate || keyConstraints.contains(e)))
                found = true;

            (!found ? newOrder : toRemove).addLast(e);
        }

        // Remove it and all later constraints
        for (HashSet<Expression> es : c.getCanonical().values())
            es.removeAll(toRemove);

        for (HashSet<Expression> es : c.getConstArrayInits().values())
            es.removeAll(toRemove);

        c.getNotCanonical().removeAll(toRemove);

        // Negate constraint
        Expression negated = new UnaryOperation(Operator.NOT, toNegate);
//        System.out.println(negated);

        while (true) {
            // Add negated constraint
            c.getNotCanonical().add(negated);
//            c.getOrder().addLast(negated);

            // Add key constraints to date
            for (Expression e : keyConstraints)
                c.getNotCanonical().add(new UnaryOperation(Operator.NOT, e));

            // Solve
            Map<String, Expression> res = c.getExpressionMap();
            ArrayList<AbstractMap.SimpleEntry<String, Object>> sat = new ArrayList<>();
            HashSet<String> unsat = new HashSet<>();

            sat.clear();
            unsat.clear();
            ConstraintServerHandler.solve(res, sat, unsat);

            if (!sat.isEmpty()) {
                // SAT -> generate input
                int max = 0;
                for (Entry<String, Object> e: sat) {
                    if (!e.getKey().startsWith("autoVar_"))
                        break;
                    Integer b = (Integer) e.getValue();
                    if (b == null)
                        break;

                    int n = Integer.parseInt(e.getKey().substring("autoVar_".length()));

                    max = Math.max(n, max);
                }

                Object sol = driver.solution(max+1);
                for (Entry<String, Object> e: sat) {
                    if (!e.getKey().startsWith("autoVar_"))
                        break;
                    Integer b = (Integer) e.getValue();
                    if (b == null)
                        break;

                    int n = Integer.parseInt(e.getKey().substring("autoVar_".length()));

                    driver.interpret(sol, n, b);
                }

                Input ret = new Input();
                ret.input = sol;
                ret.parent = in;
                ret.newConstraint = toNegate;
                ret.how = negated.toString();
                return ret;
            } else if (!unsat.isEmpty()) {
                // UNSAT, maybe we can still find new paths

                System.out.println("\tUNSAT");

//                return null;

//                System.out.println(toNegate);
//                System.out.println(negated);
//                System.out.println(unsat);
//
//                if (pathSensitive)
//                    return null;
//

                HashSet<String> escapedUnsat = new HashSet<>();

                for (String s : unsat)
                    escapedUnsat.add(s.replaceAll("\\\\", ""));

                // Find earliest constraint in UNSAT core
                newOrder = new LinkedList<>();
                toRemove = new LinkedList<>();
                found = false;
                for (Expression e : c.getOrder()) {
                    if (escapedUnsat.contains(e.toString()))
                        found = true;

                    (!found ? newOrder : toRemove).addLast(e);
                }

                if (toRemove.isEmpty())
                    return null;

                // Drop it and all later constraints
                for (HashSet<Expression> es : c.getCanonical().values())
                    es.removeAll(toRemove);

                for (HashSet<Expression> es : c.getConstArrayInits().values())
                    es.removeAll(toRemove);

                c.getNotCanonical().removeAll(toRemove);

                // Try again
                continue;

            } else {
                System.out.println("\tTimeout or error");
                return null;
            }

        }
    }
}
