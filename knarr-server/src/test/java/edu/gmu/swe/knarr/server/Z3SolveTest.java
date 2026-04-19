package edu.gmu.swe.knarr.server;

import org.junit.Test;
import static org.junit.Assert.*;

import za.ac.sun.cs.green.Green;
import za.ac.sun.cs.green.Instance;
import za.ac.sun.cs.green.expr.BVConstant;
import za.ac.sun.cs.green.expr.BVVariable;
import za.ac.sun.cs.green.expr.BinaryOperation;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.service.z3.ModelZ3JavaService;
import za.ac.sun.cs.green.service.z3.ModelZ3JavaService.Solution;
import za.ac.sun.cs.green.service.z3.Z3JavaTranslator.Z3GreenBridge;

/**
 * Tests that the Green → Z3 pipeline — as assembled by this module — can
 * solve a constraint end-to-end. Runs on a plain (non-instrumented) JVM,
 * which is how knarr-server always runs in practice.
 *
 * <p>Requires {@code libz3java.so} / {@code libz3.so} on the process's
 * LD_LIBRARY_PATH. The pom's surefire config sets this up.
 */
public class Z3SolveTest {

    private Green green;
    private ModelZ3JavaService solver;
    private ConstraintOptionGenerator generator;

    @org.junit.Before
    public void setup() {
        green = new Green();
        solver = new ModelZ3JavaService(green, null);
        generator = new ConstraintOptionGenerator();
    }

    @Test
    public void satEquality() {
        // x == 42
        BVVariable x = new BVVariable("x", 32);
        Expression c = new BinaryOperation(Operator.EQ, x, new BVConstant(42, 32));
        assertTrue("x == 42 should be SAT", solve(c).sat);
    }

    @Test
    public void satConjunction() {
        // x > 0 AND x + y == 10 AND y < x
        BVVariable x = new BVVariable("x", 32);
        BVVariable y = new BVVariable("y", 32);
        Operation c1 = new BinaryOperation(Operator.GT, x, new BVConstant(0, 32));
        Operation c2 = new BinaryOperation(
                Operator.EQ,
                new BinaryOperation(Operator.ADD, x, y),
                new BVConstant(10, 32));
        Operation c3 = new BinaryOperation(Operator.LT, y, x);
        Expression all = new BinaryOperation(
                Operator.AND, new BinaryOperation(Operator.AND, c1, c2), c3);
        assertTrue(solve(all).sat);
    }

    @Test
    public void unsatContradiction() {
        // x > 10 AND x < 5
        BVVariable x = new BVVariable("x", 32);
        Operation gt10 = new BinaryOperation(Operator.GT, x, new BVConstant(10, 32));
        Operation lt5 = new BinaryOperation(Operator.LT, x, new BVConstant(5, 32));
        Expression both = new BinaryOperation(Operator.AND, gt10, lt5);
        assertFalse("x > 10 AND x < 5 must be UNSAT", solve(both).sat);
    }

    private Solution solve(Expression expr) {
        Instance inst = new Instance(green, null, expr);
        Z3GreenBridge bridge = solver.getUnderlyingExpr(inst);
        // ConstraintOptionGenerator populates the Map<String,Expression>
        // that Z3GreenBridge.convertToZ3 consumes; calling solve() directly
        // without it NPEs.
        Z3GreenBridge[] options = generator.generateOptions(bridge)
                .toArray(new Z3GreenBridge[0]);
        if (options.length == 0) {
            // No further options means the original bridge is already the
            // minimal SAT query.
            return solver.solve(bridge);
        }
        return solver.solve(options[0]);
    }
}
