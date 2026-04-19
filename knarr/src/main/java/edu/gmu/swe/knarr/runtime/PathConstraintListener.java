package edu.gmu.swe.knarr.runtime;

import static edu.neu.ccs.prl.galette.internal.runtime.symbolic.SymbolicOpcodes.*;

import edu.neu.ccs.prl.galette.internal.runtime.Tag;
import edu.neu.ccs.prl.galette.internal.runtime.symbolic.SymbolicExecutionListener;
import za.ac.sun.cs.green.expr.ArrayVariable;
import za.ac.sun.cs.green.expr.BVConstant;
import za.ac.sun.cs.green.expr.BinaryOperation;
import za.ac.sun.cs.green.expr.BoolConstant;
import za.ac.sun.cs.green.expr.Constant;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.NaryOperation;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.RealConstant;

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
    public Tag onIinc(int varIndex, int increment, Tag tag) {
        // Build a new Expression = (label + increment) and return it as
        // the variable's new tag. Preserves the original Knarr behaviour
        // now that the SPI allows a Tag return.
        Expression e = ExpressionBuilder.exprForInt(tag, 0);
        Expression incremented =
                new BinaryOperation(Operator.ADD, e, ExpressionBuilder.intConstant(increment));
        return Tag.of(incremented);
    }

    // ---------- Arrays ----------

    /** Thresholds for skipping array constraint emission. Matched to the
     *  original {@code TaintListener} defaults. */
    public static int IGNORE_CONCRETE_ARRAY_INITIAL_CONTENTS = 1000;
    public static int IGNORE_LARGE_ARRAY_SIZE = 20000;
    public static int IGNORE_LARGE_ARRAY_INDEX = 500;

    /** Tracks the sequence of symbolic {@link ArrayVariable}s representing
     *  each runtime array's history. The last element in each list is the
     *  current version. */
    private static final java.util.IdentityHashMap<Object, java.util.LinkedList<ArrayVariable>>
            arrayVersions = new java.util.IdentityHashMap<>();

    public static void resetArrays() {
        synchronized (arrayVersions) {
            arrayVersions.clear();
        }
    }

    private static java.util.LinkedList<ArrayVariable> getOrInitArray(Object arr) {
        java.util.LinkedList<ArrayVariable> ret = arrayVersions.get(arr);
        if (ret != null) {
            return ret;
        }
        Class<?> componentType = arr.getClass().getComponentType();
        Class<?> symbolicType = componentType.isPrimitive() ? componentType : Object.class;
        java.util.LinkedList<ArrayVariable> ll = new java.util.LinkedList<>();
        ArrayVariable var = new ArrayVariable("const_array_" + arrayVersions.size(), symbolicType);
        ll.add(var);
        arrayVersions.put(arr, ll);

        // Tell the solver about the initial contents of small concrete arrays.
        int len = java.lang.reflect.Array.getLength(arr);
        if (len < IGNORE_CONCRETE_ARRAY_INITIAL_CONTENTS) {
            ArrayVariable arrVar = new ArrayVariable(var.getName() + "_" + ll.size(), symbolicType);
            for (int i = 0; i < len; i++) {
                Operation select = new BinaryOperation(
                        Operator.SELECT, arrVar, new BVConstant(i, 32));
                Constant val = concreteCellAsConstant(arr, componentType, i);
                if (val != null) {
                    PathUtils.getCurPC()._addDet(Operator.EQ, select, val);
                }
            }
        }
        return ll;
    }

    private static Expression currentArrayVar(Object arr) {
        synchronized (arrayVersions) {
            java.util.LinkedList<ArrayVariable> ll = getOrInitArray(arr);
            ArrayVariable base = ll.getLast();
            return new ArrayVariable(base.getName() + "_" + ll.size(), base.getType());
        }
    }

    private static void recordArrayStore(Object arr, Expression idx, Expression val) {
        synchronized (arrayVersions) {
            java.util.LinkedList<ArrayVariable> ll = getOrInitArray(arr);
            ArrayVariable base = ll.getLast();
            ArrayVariable oldVar = new ArrayVariable(base.getName() + "_" + ll.size(), base.getType());
            ArrayVariable newVar =
                    new ArrayVariable(base.getName() + "_" + (ll.size() + 1), base.getType());
            ll.addLast(base);
            Operation store = new NaryOperation(Operator.STORE, oldVar, idx, val);
            PathUtils.getCurPC()._addDet(Operator.EQ, store, newVar);
        }
    }

    private static Constant concreteCellAsConstant(Object arr, Class<?> component, int i) {
        if (component == boolean.class) {
            return new BoolConstant(((boolean[]) arr)[i]);
        } else if (component == byte.class) {
            return new BVConstant(((byte[]) arr)[i], 32);
        } else if (component == char.class) {
            return new BVConstant(((char[]) arr)[i], 32);
        } else if (component == short.class) {
            return new BVConstant(((short[]) arr)[i], 32);
        } else if (component == int.class) {
            return new BVConstant(((int[]) arr)[i], 32);
        } else if (component == long.class) {
            return new BVConstant(((long[]) arr)[i], 64);
        } else if (component == float.class) {
            return new RealConstant(((float[]) arr)[i]);
        } else if (component == double.class) {
            return new RealConstant(((double[]) arr)[i]);
        }
        // Object arrays: no usable concrete encoding; leave unconstrained.
        return null;
    }

    @Override
    public Tag onArrayLoad(int opcode, Object array, int index, Tag arrayTag, Tag indexTag, Tag elemTag) {
        if (array == null) {
            return elemTag;
        }
        boolean taintedIndex = !Tag.isEmpty(indexTag);
        boolean taintedCell = !Tag.isEmpty(elemTag);
        int len = java.lang.reflect.Array.getLength(array);
        if (len > IGNORE_LARGE_ARRAY_SIZE && index > IGNORE_LARGE_ARRAY_INDEX) {
            return elemTag;
        }
        if (!taintedIndex && !taintedCell) {
            return elemTag;
        }

        BVConstant lenConst = new BVConstant(len, 32);

        if (taintedIndex && !taintedCell) {
            Expression idxExpr = extractLabel(indexTag);
            Expression arrVar = currentArrayVar(array);
            Operation select = new BinaryOperation(Operator.SELECT, arrVar, idxExpr);
            // Anchor the concrete read to the SELECT.
            Constant cellConst = concreteCellAsConstant(
                    array, array.getClass().getComponentType(), index);
            if (cellConst != null) {
                PathUtils.getCurPC()._addDet(Operator.EQ, cellConst, select);
            }
            // In-bounds.
            PathUtils.getCurPC()._addDet(Operator.LT, idxExpr, lenConst);
            PathUtils.getCurPC()._addDet(Operator.GE, idxExpr, PathUtils.BV0_32);
            return Tag.of(select);
        } else if (taintedCell && !taintedIndex) {
            return elemTag;
        } else {
            // Both symbolic: record select = cell's current symbolic tag.
            Expression idxExpr = extractLabel(indexTag);
            Expression arrVar = currentArrayVar(array);
            Operation select = new BinaryOperation(Operator.SELECT, arrVar, idxExpr);
            PathUtils.getCurPC()._addDet(Operator.EQ, extractLabel(elemTag), select);
            PathUtils.getCurPC()._addDet(Operator.LT, idxExpr, lenConst);
            PathUtils.getCurPC()._addDet(Operator.GE, idxExpr, PathUtils.BV0_32);
            return elemTag;
        }
    }

    @Override
    public Tag onArrayStore(int opcode, Object array, int index, Tag arrayTag, Tag indexTag, Tag valueTag) {
        if (array == null) {
            return valueTag;
        }
        boolean taintedIndex = !Tag.isEmpty(indexTag);
        boolean taintedVal = !Tag.isEmpty(valueTag);
        int len = java.lang.reflect.Array.getLength(array);
        if (len > IGNORE_LARGE_ARRAY_SIZE && index > IGNORE_LARGE_ARRAY_INDEX) {
            return valueTag;
        }
        if (!taintedIndex && !taintedVal) {
            return valueTag;
        }
        BVConstant lenConst = new BVConstant(len, 32);

        if (taintedIndex) {
            Expression idxExpr = extractLabel(indexTag);
            // If the value is concrete, we don't have the NEW value in this
            // SPI callback (the actual store happens after the hook). Use
            // the current cell's value as a best-effort anchor; when the
            // value is symbolic, use its expression.
            Expression valExpr;
            if (taintedVal) {
                valExpr = extractLabel(valueTag);
            } else {
                Constant c = concreteCellAsConstant(
                        array, array.getClass().getComponentType(), index);
                if (c == null) {
                    // Object arrays and other unsupported types: skip.
                    return valueTag;
                }
                valExpr = c;
            }
            recordArrayStore(array, idxExpr, valExpr);
            PathUtils.getCurPC()._addDet(Operator.LT, idxExpr, lenConst);
            PathUtils.getCurPC()._addDet(Operator.GE, idxExpr, PathUtils.BV0_32);
        } else if (taintedVal) {
            // Concrete index, symbolic value — record in the array's history.
            Expression valExpr = extractLabel(valueTag);
            recordArrayStore(array, new BVConstant(index, 32), valExpr);
        }
        return valueTag;
    }

    // ---------- Helpers ----------

    /** Pulls the single Green {@link Expression} label out of a non-empty Tag. */
    private static Expression extractLabel(Tag t) {
        return (Expression) Tag.getLabels(t)[0];
    }


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
