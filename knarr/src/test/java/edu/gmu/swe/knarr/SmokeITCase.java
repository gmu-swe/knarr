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

    @Test
    void iincKeepsSymbolicExpression() {
        int i = Symbolicator.symbolic("iinc_i", 10);
        i += 3; // IINC
        Expression expr = Symbolicator.getExpression(i);
        Assertions.assertNotNull(expr, "IINC should preserve symbolic tag");
        Assertions.assertTrue(expr.toString().contains("iinc_i"));
    }

    @Test
    void arrayLoadWithSymbolicIndexRecordsSelectConstraint() {
        int[] arr = {10, 20, 30, 40};
        int idx = Symbolicator.symbolic("idx", 2);
        int v = arr[idx];
        // v should be tagged — its tag is the SELECT expression.
        Expression vExpr = Symbolicator.getExpression(v);
        Assertions.assertNotNull(vExpr, "tagged-index array load should produce a symbolic element");
        // Path condition should contain constraints (bounds + cell anchor).
        Assertions.assertNotNull(PathUtils.getCurPC().constraints);
    }

    @Test
    void arrayStoreWithSymbolicValueRecordsStoreConstraint() {
        int[] arr = {0, 0, 0, 0};
        int v = Symbolicator.symbolic("stored_v", 42);
        arr[1] = v;
        // Subsequent load should re-enter the SELECT/STORE chain.
        int readBack = arr[1];
        Expression readExpr = Symbolicator.getExpression(readBack);
        Assertions.assertNotNull(readExpr, "after symbolic store the cell should be symbolic");
    }

    @Test
    void symbolicStringPropagatesThroughCharAt() {
        String s = Symbolicator.symbolic("msg", "hi");
        // charAt routes through a JDK method that reads the backing array,
        // so the returned char should carry the per-character BVVariable.
        char c0 = s.charAt(0);
        char c1 = s.charAt(1);
        Expression e0 = Symbolicator.getExpression(c0);
        Expression e1 = Symbolicator.getExpression(c1);
        Assertions.assertNotNull(e0, "char at 0 should be symbolic");
        Assertions.assertNotNull(e1, "char at 1 should be symbolic");
        Assertions.assertTrue(e0.toString().contains("msg_c0"), "expected msg_c0 label in " + e0);
        Assertions.assertTrue(e1.toString().contains("msg_c1"), "expected msg_c1 label in " + e1);
    }

    @Test
    void stringEqualsRecordsPredicateConstraint() {
        String s = Symbolicator.symbolic("eq_s", "hi");
        boolean r = s.equals("hi");
        Assertions.assertTrue(r);
        Expression pc = PathUtils.getCurPC().constraints;
        Assertions.assertNotNull(pc, "String.equals on tagged String should record a constraint");
        Assertions.assertTrue(pc.toString().contains("EQUALS"),
                "expected EQUALS in PC: " + pc);
    }

    @Test
    void stringStartsWithRecordsPredicate() {
        String s = Symbolicator.symbolic("prefix_s", "helloworld");
        boolean r = s.startsWith("hello");
        Assertions.assertTrue(r);
        Expression pc = PathUtils.getCurPC().constraints;
        Assertions.assertNotNull(pc);
        Assertions.assertTrue(pc.toString().contains("STARTSWITH"),
                "expected STARTSWITH in PC: " + pc);
    }

    @Test
    void stringLengthReturnsSymbolicInt() {
        String s = Symbolicator.symbolic("len_s", "abc");
        int len = s.length();
        Assertions.assertEquals(3, len);
        // Length expression should be attached to the int.
        Expression e = Symbolicator.getExpression(len);
        Assertions.assertNotNull(e, "s.length() should carry a symbolic tag");
        Assertions.assertTrue(e.toString().contains("LENGTH"),
                "expected LENGTH in expression: " + e);
    }

    @Test
    void untaggedStringDoesNotFireMask() {
        // No symbolic tagging — String.equals with tagged=false on both
        // sides should not add any constraint.
        boolean r = "concrete".equals("concrete");
        Assertions.assertTrue(r);
        Assertions.assertNull(PathUtils.getCurPC().constraints);
    }
}
