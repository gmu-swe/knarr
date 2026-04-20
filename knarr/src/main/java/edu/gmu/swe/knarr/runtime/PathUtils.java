package edu.gmu.swe.knarr.runtime;

import static edu.neu.ccs.prl.galette.internal.runtime.symbolic.SymbolicOpcodes.*;

import java.util.HashSet;

import za.ac.sun.cs.green.expr.BVConstant;
import za.ac.sun.cs.green.expr.BinaryOperation;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.UnaryOperation;

/**
 * Path-condition state for Knarr running atop Galette. Shared across
 * {@link PathConstraintListener} (records branch / switch constraints),
 * {@link Symbolicator} (creates symbolic variables), and the Z3 server
 * dump in {@link Symbolicator#dumpConstraints(String)}.
 *
 * <p>This class no longer contains per-opcode arithmetic builders;
 * see {@link ExpressionBuilder} for those.
 */
public class PathUtils {
    public static BVConstant BV0_32;
    private static PathConditionWrapper curPC;
    public static final boolean IGNORE_SHIFTS = true;
    public static final String INTERNAL_NAME = "edu/gmu/swe/knarr/runtime/PathUtils";

    public static PathConditionWrapper getCurPC() {
        if (curPC == null)
            curPC = new PathConditionWrapper();
        return curPC;
    }

    static HashSet<String> usedLabels = new HashSet<>();

    public static void checkLabelAndInitJPF(String label) {
        if (label == null || usedLabels.contains(label))
            throw new IllegalArgumentException("Invalid (dup?) label: \"" + label + "\"");
        if (label.contains(" "))
            throw new IllegalArgumentException("label has spaces, but must not: \"" + label + "\"");
        usedLabels.add(label);
        if (!JPFInited)
            initJPF();
    }

    static boolean JPFInited = false;

    static void initJPF() {
        BV0_32 = new BVConstant(0, 32);
        if (!JPFInited) {
            JPFInited = true;
        }
    }

    /**
     * Legacy entry point — early iterations of the guided mutator tracked
     * per-branch source sites via a {@link StackWalker} call. Under
     * Galette instrumentation a walker per branch added seconds of
     * overhead to each Ant pilot iteration. The heuristic mutator now
     * derives its fingerprints from the branch expression itself (see
     * {@code PilotRunner.guidedSiteKey}), so the walker is gone. This
     * method is retained as a no-op for source compatibility with older
     * listener paths.
     */
    public static String captureSite() {
        return "?";
    }

    // ---------- Constraint emission (called from PathConstraintListener) ----------

    /**
     * Records a single-operand integer branch constraint
     * ({@code IFEQ}..{@code IFLE}).
     */
    public static void addConstraint(Expression e, int opcode, boolean taken) {
        if (e == null) return;
        if (!JPFInited) initJPF();

        Expression cmp;
        switch (opcode) {
            case IFEQ:
                cmp = new BinaryOperation(Operator.EQ, Operation.ZERO, e);
                break;
            case IFGE:
                cmp = new BinaryOperation(Operator.GE, e, Operation.ZERO);
                break;
            case IFLE:
                cmp = new BinaryOperation(Operator.LE, e, Operation.ZERO);
                break;
            case IFLT:
                cmp = new BinaryOperation(Operator.LT, e, Operation.ZERO);
                break;
            case IFGT:
                cmp = new BinaryOperation(Operator.GT, e, Operation.ZERO);
                break;
            case IFNE:
                cmp = new BinaryOperation(Operator.NE, e, Operation.ZERO);
                break;
            default:
                throw new IllegalArgumentException("Unimplemented branch opcode: " + opcode);
        }

        Expression toAdd = taken ? cmp : new UnaryOperation(Operator.NOT, cmp);
        getCurPC()._addBranchDet(toAdd);
    }

    /**
     * Records a two-operand integer branch constraint
     * ({@code IF_ICMPEQ}..{@code IF_ICMPLE}).
     */
    public static void addCmpConstraint(int opcode, Expression l, Expression r, boolean taken) {
        if (l == null && r == null) return;
        if (!JPFInited) initJPF();

        Expression cmp;
        switch (opcode) {
            case IF_ACMPEQ:
            case IF_ACMPNE:
                return;
            case IF_ICMPEQ:
                cmp = new BinaryOperation(Operator.EQ, l, r);
                break;
            case IF_ICMPGE:
                cmp = new BinaryOperation(Operator.GE, l, r);
                break;
            case IF_ICMPGT:
                cmp = new BinaryOperation(Operator.GT, l, r);
                break;
            case IF_ICMPLE:
                cmp = new BinaryOperation(Operator.LE, l, r);
                break;
            case IF_ICMPLT:
                cmp = new BinaryOperation(Operator.LT, l, r);
                break;
            case IF_ICMPNE:
                cmp = new BinaryOperation(Operator.NE, l, r);
                break;
            default:
                throw new IllegalArgumentException("Unimplemented branch opcode: " + opcode);
        }

        Expression toAdd = taken ? cmp : new UnaryOperation(Operator.NOT, cmp);
        getCurPC()._addBranchDet(toAdd);
    }

    /**
     * Records constraints for a {@code TABLESWITCH} instruction. Adds the
     * limit constraint ({@code lExp in [min, max]}) plus a {@code != i}
     * disequation for every key {@code i} in the range that does not match
     * the runtime value {@code v}.
     */
    public static void addTableSwitchConstraint(Expression lExp, int v, int min, int max) {
        if (lExp == null) return;
        if (!JPFInited) initJPF();

        Operation limit = new BinaryOperation(Operator.LE, lExp, ExpressionBuilder.intConstant(max));
        limit = new BinaryOperation(Operator.AND, limit,
                new BinaryOperation(Operator.GE, lExp, ExpressionBuilder.intConstant(min)));

        for (int i = min; i < max; i++) {
            if (i == v) continue;
            Expression notTaken = new BinaryOperation(Operator.NE, lExp, ExpressionBuilder.intConstant(i));
            getCurPC()._addBranchDet(Operator.AND, limit, notTaken);
        }
    }

    /**
     * Records constraints for a {@code LOOKUPSWITCH} instruction.
     * @param takenArm index into {@code keys}, or {@code keys.length} for default.
     */
    public static void addLookupSwitchConstraint(Expression lExp, int takenArm, int[] keys) {
        if (lExp == null) return;
        if (!JPFInited) initJPF();

        Operation defaultCase = null;
        for (int i = 0; i < keys.length; i++) {
            Operation thisDefault = new BinaryOperation(Operator.NE, lExp, ExpressionBuilder.intConstant(keys[i]));
            if (defaultCase == null)
                defaultCase = thisDefault;
            else
                defaultCase = new BinaryOperation(Operator.AND, defaultCase, thisDefault);

            if (i == takenArm) {
                Operation expr = new BinaryOperation(Operator.EQ, lExp, ExpressionBuilder.intConstant(keys[i]));
                getCurPC()._addBranchDet(expr);
            } else {
                Operation expr = new BinaryOperation(Operator.NE, lExp, ExpressionBuilder.intConstant(keys[i]));
                getCurPC()._addBranchDet(expr);
            }
        }
        if (takenArm == keys.length) {
            if (defaultCase != null) {
                getCurPC()._addBranchDet(defaultCase);
            }
        } else if (defaultCase != null) {
            Operation neg = new UnaryOperation(Operator.NOT, defaultCase);
            getCurPC()._addBranchDet(neg);
        }
    }
}
