package edu.gmu.swe.knarr;

import edu.gmu.swe.knarr.runtime.PathConstraintListener;
import edu.gmu.swe.knarr.runtime.PathUtils;
import edu.gmu.swe.knarr.runtime.Symbolicator;
import edu.neu.ccs.prl.galette.internal.runtime.symbolic.SymbolicListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.ac.sun.cs.green.expr.Expression;

/**
 * End-to-end smoke tests for the Knarr-on-Galette port. Each test runs inside
 * a Galette-instrumented JDK: a tagged primitive flows through arithmetic or
 * a branch, and we verify that a Green {@link Expression} was built and/or a
 * path constraint recorded. These do not exercise the Z3 server — they check
 * the listener pipeline only.
 */
public class SmokeITCase {

    @BeforeEach
    void installListener() {
        SymbolicListener.setListener(new PathConstraintListener());
    }

    @AfterEach
    void uninstallListener() {
        SymbolicListener.setListener(null);
        Symbolicator.reset();
    }

    @Test
    void symbolicIntPropagatesThroughArith() {
        int x = Symbolicator.symbolic("x", 5);
        int y = x + 3;
        Expression expr = Symbolicator.getExpression(y);
        Assertions.assertNotNull(expr, "result of x + 3 should carry a symbolic expression");
    }

    @Test
    void symbolicIntBranchRecordsPathConstraint() {
        int x = Symbolicator.symbolic("branch_x", 5);
        // Consume a branch outcome so the side effect (recording the
        // constraint) is what we observe. We avoid Assertions on tagged x.
        if (x > 0) {
            // taken
        }
        Assertions.assertNotNull(
                PathUtils.getCurPC().constraints,
                "branch on tagged int should have recorded at least one constraint");
    }

    @Test
    void untaggedBranchRecordsNothing() {
        int x = 7;
        if (x > 0) {
            // taken
        }
        // No tagged value was introduced in this body (Symbolicator not
        // called) so getCurPC().constraints should remain null.
        Assertions.assertNull(PathUtils.getCurPC().constraints);
    }

    @Test
    void symbolicLongPropagatesThroughArith() {
        long x = Symbolicator.symbolic("long_x", 100L);
        long y = x * 3L;
        Expression expr = Symbolicator.getExpression(y);
        Assertions.assertNotNull(expr);
    }

    @Test
    void chainedArithmeticBuildsCompositeExpression() {
        int a = Symbolicator.symbolic("a", 2);
        int b = Symbolicator.symbolic("b", 3);
        int c = (a + b) * 4;
        Expression expr = Symbolicator.getExpression(c);
        Assertions.assertNotNull(expr);
        String exprStr = expr.toString();
        // The expression should reference both variables by label.
        Assertions.assertTrue(exprStr.contains("a"), "expected 'a' in " + exprStr);
        Assertions.assertTrue(exprStr.contains("b"), "expected 'b' in " + exprStr);
    }
}
