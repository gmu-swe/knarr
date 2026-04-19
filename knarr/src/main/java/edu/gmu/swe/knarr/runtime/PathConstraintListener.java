package edu.gmu.swe.knarr.runtime;

import static edu.neu.ccs.prl.galette.internal.runtime.symbolic.SymbolicOpcodes.*;

import edu.neu.ccs.prl.galette.internal.runtime.Tag;
import edu.neu.ccs.prl.galette.internal.runtime.symbolic.SymbolicExecutionListener;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation.Operator;

/**
 * Knarr's {@link SymbolicExecutionListener} implementation. Translates SPI
 * callbacks into Green {@link Expression}s and records path constraints via
 * {@link PathUtils#getCurPC()}.
 */
public final class PathConstraintListener implements SymbolicExecutionListener {

    // ---------- Arithmetic / unary / convert (return derived tag) ----------

    @Override
    public Tag onIntArith(int opcode, int v1, int v2, Tag t1, Tag t2) {
        // v1 is the lower stack slot (left), v2 is the upper (right). The
        // SPI hands them in stack order: value1, value2 with value2 on top.
        Expression l = ExpressionBuilder.exprForInt(t1, v1);
        Expression r = ExpressionBuilder.exprForInt(t2, v2);

        // Short-circuit for ADD/SUB with neutral 0 — preserve the surviving
        // operand's tag rather than wrapping.
        if (opcode == IADD) {
            if (Tag.isEmpty(t1) && v1 == 0) return t2;
            if (Tag.isEmpty(t2) && v2 == 0) return t1;
        }

        Expression result = ExpressionBuilder.intBinary(opcode, l, r);
        return result == null ? Tag.union(t1, t2) : Tag.of(result);
    }

    @Override
    public Tag onIntUnary(int opcode, int value, Tag tag) {
        Expression e = ExpressionBuilder.exprForInt(tag, value);
        Expression result = ExpressionBuilder.intUnary(opcode, e);
        return result == null ? tag : Tag.of(result);
    }

    @Override
    public Tag onFloatArith(int opcode, float v1, float v2, Tag t1, Tag t2) {
        Expression l = ExpressionBuilder.exprForFloat(t1, v1);
        Expression r = ExpressionBuilder.exprForFloat(t2, v2);

        // FCMPL/FCMPG: record path constraints, no result tag.
        if (opcode == FCMPL || opcode == FCMPG) {
            recordRealCmp(l, r, v1, v2);
            return null;
        }

        Expression result = ExpressionBuilder.floatBinary(opcode, l, r);
        return result == null ? Tag.union(t1, t2) : Tag.of(result);
    }

    @Override
    public Tag onFloatUnary(int opcode, float value, Tag tag) {
        Expression e = ExpressionBuilder.exprForFloat(tag, value);
        Expression result = ExpressionBuilder.floatUnary(opcode, e);
        return result == null ? tag : Tag.of(result);
    }

    @Override
    public Tag onCat1Convert(int opcode, Tag tag) {
        // Cat-1 conversions where we have no concrete value handy; the SPI
        // doesn't pass one. The original Knarr handled I2F/F2I as null taints
        // (DISABLE_FLOATS), so we just propagate.
        return tag;
    }

    @Override
    public Tag onLongArith(int opcode, long v1, long v2, Tag t1, Tag t2) {
        Expression l = ExpressionBuilder.exprForLong(t1, v1);
        Expression r = ExpressionBuilder.exprForLong(t2, v2);
        Expression result = ExpressionBuilder.longBinary(opcode, l, r);
        return result == null ? Tag.union(t1, t2) : Tag.of(result);
    }

    @Override
    public Tag onLongShift(int opcode, long v1, int v2, Tag t1, Tag t2) {
        Expression l = ExpressionBuilder.exprForLong(t1, v1);
        Expression r = ExpressionBuilder.exprForInt(t2, v2);
        Expression result = ExpressionBuilder.longShift(opcode, l, r);
        return result == null ? Tag.union(t1, t2) : Tag.of(result);
    }

    @Override
    public Tag onLongCmp(long v1, long v2, Tag t1, Tag t2) {
        // Original behaviour: emit an EQ/GT/LT path constraint and propagate
        // no result tag.
        Expression l = ExpressionBuilder.exprForLong(t1, v1);
        Expression r = ExpressionBuilder.exprForLong(t2, v2);
        recordIntCmp(l, r, v1 == v2, v1 < v2);
        return null;
    }

    @Override
    public Tag onLongUnary(int opcode, long value, Tag tag) {
        Expression e = ExpressionBuilder.exprForLong(tag, value);
        Expression result = ExpressionBuilder.longUnary(opcode, e);
        return result == null ? tag : Tag.of(result);
    }

    @Override
    public Tag onDoubleArith(int opcode, double v1, double v2, Tag t1, Tag t2) {
        Expression l = ExpressionBuilder.exprForDouble(t1, v1);
        Expression r = ExpressionBuilder.exprForDouble(t2, v2);
        Expression result = ExpressionBuilder.doubleBinary(opcode, l, r);
        return result == null ? Tag.union(t1, t2) : Tag.of(result);
    }

    @Override
    public Tag onDoubleCmp(int opcode, double v1, double v2, Tag t1, Tag t2) {
        Expression l = ExpressionBuilder.exprForDouble(t1, v1);
        Expression r = ExpressionBuilder.exprForDouble(t2, v2);
        recordRealCmp(l, r, v1, v2);
        return null;
    }

    @Override
    public Tag onDoubleUnary(int opcode, double value, Tag tag) {
        Expression e = ExpressionBuilder.exprForDouble(tag, value);
        Expression result = ExpressionBuilder.doubleUnary(opcode, e);
        return result == null ? tag : Tag.of(result);
    }

    @Override
    public Tag onIntWiden(int opcode, int value, Tag tag) {
        Expression e = ExpressionBuilder.exprForInt(tag, value);
        Expression result = ExpressionBuilder.convert(opcode, e);
        return result == null ? tag : Tag.of(result);
    }

    @Override
    public Tag onFloatWiden(int opcode, float value, Tag tag) {
        Expression e = ExpressionBuilder.exprForFloat(tag, value);
        Expression result = ExpressionBuilder.convert(opcode, e);
        return result == null ? tag : Tag.of(result);
    }

    @Override
    public Tag onLongConvert(int opcode, long value, Tag tag) {
        Expression e = ExpressionBuilder.exprForLong(tag, value);
        Expression result = ExpressionBuilder.convert(opcode, e);
        return result == null ? tag : Tag.of(result);
    }

    @Override
    public Tag onDoubleConvert(int opcode, double value, Tag tag) {
        Expression e = ExpressionBuilder.exprForDouble(tag, value);
        Expression result = ExpressionBuilder.convert(opcode, e);
        return result == null ? tag : Tag.of(result);
    }

    // ---------- Branches / switches (record constraints, no return) ----------

    @Override
    public void onIntBranch(int opcode, int value, Tag tag) {
        Expression e = ExpressionBuilder.exprForInt(tag, value);
        boolean taken = evalUnaryBranch(opcode, value);
        PathUtils.addConstraint(e, opcode, taken);
    }

    @Override
    public void onIntCmpBranch(int opcode, int v1, int v2, Tag t1, Tag t2) {
        Expression l = ExpressionBuilder.exprForInt(t1, v1);
        Expression r = ExpressionBuilder.exprForInt(t2, v2);
        boolean taken = evalCmpBranch(opcode, v1, v2);
        PathUtils.addCmpConstraint(opcode, l, r, taken);
    }

    @Override
    public void onRefBranch(int opcode, Object value, Tag tag) {
        // IFNULL/IFNONNULL: original Knarr did not emit constraints over
        // reference nullness. Phase 2 MVP: skip.
    }

    @Override
    public void onRefCmpBranch(int opcode, Object v1, Object v2, Tag t1, Tag t2) {
        // IF_ACMPEQ/IF_ACMPNE: original Knarr's addConstraint had a no-op
        // case for these. Skip for MVP.
    }

    @Override
    public void onTableSwitch(int opcode, int value, Tag tag, int min, int max) {
        Expression e = ExpressionBuilder.exprForInt(tag, value);
        PathUtils.addTableSwitchConstraint(e, value, min, max);
    }

    @Override
    public void onLookupSwitch(int opcode, int value, Tag tag, int[] keys) {
        Expression e = ExpressionBuilder.exprForInt(tag, value);
        // Find taken arm: index into keys, or keys.length for default.
        int takenArm = keys.length;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == value) {
                takenArm = i;
                break;
            }
        }
        PathUtils.addLookupSwitchConstraint(e, takenArm, keys);
    }

    @Override
    public void onIinc(int varIndex, int increment, Tag tag) {
        // Galette tags are immutable; the original Knarr mutated the tag's
        // single Expression label in-place to (label + increment). With an
        // immutable tag we cannot rewrite the variable's tag from inside
        // this void callback. See report: known semantic gap; subsequent
        // arithmetic on the variable will rebuild correctly via onIntArith
        // (the constant increment will be folded in there).
    }

    // ---------- Arrays ----------

    @Override
    public Tag onArrayLoad(int opcode, Object array, int index, Tag arrayTag, Tag indexTag, Tag elemTag) {
        // For Phase 2 MVP we keep array semantics simple: pass through the
        // element tag. The full ArrayVariable / SELECT/STORE machinery in
        // the original TaintListener depended on Phosphor's per-slot lazy
        // taint storage that Galette manages differently (via
        // ArrayTagStore). Adding symbolic select/store support over the SPI
        // is a Phase 3 task.
        if (Tag.isEmpty(elemTag)) {
            return null;
        }
        return elemTag;
    }

    @Override
    public Tag onArrayStore(int opcode, Object array, int index, Tag arrayTag, Tag indexTag, Tag valueTag) {
        // Same caveat as onArrayLoad; for now the value tag flows into
        // Galette's shadow store unchanged.
        return valueTag;
    }

    // ---------- Helpers ----------

    /** True iff the single-operand integer branch is taken at runtime. */
    private static boolean evalUnaryBranch(int opcode, int v) {
        switch (opcode) {
            case IFEQ:
                return v == 0;
            case IFNE:
                return v != 0;
            case IFLT:
                return v < 0;
            case IFGE:
                return v >= 0;
            case IFGT:
                return v > 0;
            case IFLE:
                return v <= 0;
            default:
                return false;
        }
    }

    /** True iff the two-operand integer branch is taken at runtime. */
    private static boolean evalCmpBranch(int opcode, int v1, int v2) {
        switch (opcode) {
            case IF_ICMPEQ:
                return v1 == v2;
            case IF_ICMPNE:
                return v1 != v2;
            case IF_ICMPLT:
                return v1 < v2;
            case IF_ICMPGE:
                return v1 >= v2;
            case IF_ICMPGT:
                return v1 > v2;
            case IF_ICMPLE:
                return v1 <= v2;
            default:
                return false;
        }
    }

    private static void recordIntCmp(Expression l, Expression r, boolean eq, boolean lt) {
        if (eq) {
            PathUtils.getCurPC()._addDet(Operator.EQ, l, r);
        } else if (lt) {
            PathUtils.getCurPC()._addDet(Operator.GT, l, r);
        } else {
            PathUtils.getCurPC()._addDet(Operator.LT, l, r);
        }
    }

    private static void recordRealCmp(Expression l, Expression r, double v1, double v2) {
        if (v1 == v2) {
            PathUtils.getCurPC()._addDet(Operator.EQ, l, r);
        } else if (v1 < v2) {
            PathUtils.getCurPC()._addDet(Operator.GT, l, r);
        } else {
            PathUtils.getCurPC()._addDet(Operator.LT, l, r);
        }
    }

    private static void recordRealCmp(Expression l, Expression r, float v1, float v2) {
        recordRealCmp(l, r, (double) v1, (double) v2);
    }
}
