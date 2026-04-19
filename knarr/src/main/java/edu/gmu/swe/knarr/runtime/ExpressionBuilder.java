package edu.gmu.swe.knarr.runtime;

import edu.neu.ccs.prl.galette.internal.runtime.Tag;
import edu.neu.ccs.prl.galette.internal.runtime.symbolic.SymbolicOpcodes;
import za.ac.sun.cs.green.expr.BinaryOperation;
import za.ac.sun.cs.green.expr.BVConstant;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.RealConstant;
import za.ac.sun.cs.green.expr.UnaryOperation;

/**
 * Static helpers that turn (Tag, value) pairs into Green {@link Expression}s
 * and that build derived {@link Expression}s for each instrumented JVM op.
 *
 * <p>If a tag is empty, the helpers fall back to a constant of the given
 * concrete value. Otherwise they use the tag's first label, which is the
 * {@link Expression} {@link Symbolicator} attaches to symbolic values.
 */
public final class ExpressionBuilder {
    private ExpressionBuilder() {}

    // ---------- (Tag, concrete) -> Expression ----------

    /**
     * @return the tag's single Expression label, or an int constant of {@code value} if the tag is empty.
     */
    public static Expression exprForInt(Tag tag, int value) {
        if (Tag.isEmpty(tag)) {
            return intConstant(value);
        }
        Object label = Tag.getLabels(tag)[0];
        return (Expression) label;
    }

    public static Expression exprForLong(Tag tag, long value) {
        if (Tag.isEmpty(tag)) {
            return longConstant(value);
        }
        Object label = Tag.getLabels(tag)[0];
        return (Expression) label;
    }

    public static Expression exprForFloat(Tag tag, float value) {
        if (Tag.isEmpty(tag)) {
            return new RealConstant((double) value);
        }
        Object label = Tag.getLabels(tag)[0];
        return (Expression) label;
    }

    public static Expression exprForDouble(Tag tag, double value) {
        if (Tag.isEmpty(tag)) {
            return new RealConstant(value);
        }
        Object label = Tag.getLabels(tag)[0];
        return (Expression) label;
    }

    public static Expression exprForBool(Tag tag, boolean value) {
        if (Tag.isEmpty(tag)) {
            return intConstant(value ? 1 : 0);
        }
        Object label = Tag.getLabels(tag)[0];
        return (Expression) label;
    }

    public static Expression exprForRef(Tag tag) {
        if (Tag.isEmpty(tag)) {
            return null;
        }
        Object label = Tag.getLabels(tag)[0];
        return (Expression) label;
    }

    // ---------- Constants ----------

    public static IntConstant intConstant(int v) {
        switch (v) {
            case Integer.MIN_VALUE:
                return IntConstant.ICONST_MIN_INT;
            case 0:
                return IntConstant.ICONST_0;
            case 1:
                return IntConstant.ICONST_1;
            case '0':
                return IntConstant.ICONST_CHAR_0;
            case '9':
                return IntConstant.ICONST_CHAR_9;
            case Integer.MAX_VALUE:
                return IntConstant.ICONST_MAX_INT;
            default:
                return new IntConstant(v);
        }
    }

    public static IntConstant longConstant(long v) {
        if (v == Integer.MIN_VALUE) return IntConstant.ICONST_MIN_INT;
        if (v == 0) return IntConstant.ICONST_0;
        if (v == 1) return IntConstant.ICONST_1;
        if (v == Integer.MAX_VALUE) return IntConstant.ICONST_MAX_INT;
        return new IntConstant(v);
    }

    // ---------- Per-opcode result builders ----------

    /**
     * Build the result Expression for an integer binary op (IADD, ISUB, IMUL,
     * IDIV, IREM, ISHL, ISHR, IUSHR, IAND, IOR, IXOR). Returns null if both
     * operands are concrete constants (caller should drop the tag).
     */
    public static Expression intBinary(int opcode, Expression l, Expression r) {
        if (l == null && r == null) return null;
        switch (opcode) {
            case SymbolicOpcodes.IADD:
                return new BinaryOperation(Operator.ADD, l, r);
            case SymbolicOpcodes.ISUB:
                return new BinaryOperation(Operator.SUB, l, r);
            case SymbolicOpcodes.IMUL:
                return new BinaryOperation(Operator.MUL, l, r);
            case SymbolicOpcodes.IDIV:
                return new BinaryOperation(Operator.DIV, l, r);
            case SymbolicOpcodes.IREM:
                return new BinaryOperation(Operator.MOD, l, r);
            case SymbolicOpcodes.ISHL:
                return new BinaryOperation(Operator.SHIFTL, l, r);
            case SymbolicOpcodes.ISHR:
                return new BinaryOperation(Operator.SHIFTR, l, r);
            case SymbolicOpcodes.IUSHR:
                return new BinaryOperation(Operator.SHIFTUR, l, r);
            case SymbolicOpcodes.IAND:
                return new BinaryOperation(Operator.BIT_AND, l, r);
            case SymbolicOpcodes.IOR:
                return new BinaryOperation(Operator.BIT_OR, l, r);
            case SymbolicOpcodes.IXOR:
                return new BinaryOperation(Operator.BIT_XOR, l, r);
            default:
                return null;
        }
    }

    /**
     * Build the result Expression for a long binary op (LADD, LSUB, LMUL,
     * LDIV, LREM, LAND, LOR, LXOR).
     */
    public static Expression longBinary(int opcode, Expression l, Expression r) {
        if (l == null && r == null) return null;
        switch (opcode) {
            case SymbolicOpcodes.LADD:
                return new BinaryOperation(Operator.ADD, l, r);
            case SymbolicOpcodes.LSUB:
                return new BinaryOperation(Operator.SUB, l, r);
            case SymbolicOpcodes.LMUL:
                return new BinaryOperation(Operator.MUL, l, r);
            case SymbolicOpcodes.LDIV:
                return new BinaryOperation(Operator.DIV, l, r);
            case SymbolicOpcodes.LREM:
                return new BinaryOperation(Operator.MOD, l, r);
            case SymbolicOpcodes.LAND:
                return new BinaryOperation(Operator.BIT_AND, l, r);
            case SymbolicOpcodes.LOR:
                return new BinaryOperation(Operator.BIT_OR, l, r);
            case SymbolicOpcodes.LXOR:
                return new BinaryOperation(Operator.BIT_XOR, l, r);
            default:
                return null;
        }
    }

    /**
     * LSHL/LSHR/LUSHR. Right is an int; left is a long.
     */
    public static Expression longShift(int opcode, Expression l, Expression r) {
        if (l == null && r == null) return null;
        // The original PathUtils widened IntConstant lefts to BVConstant for LSHL.
        if (l instanceof IntConstant && opcode == SymbolicOpcodes.LSHL) {
            l = new BVConstant(((IntConstant) l).getValueLong(), 64);
        }
        switch (opcode) {
            case SymbolicOpcodes.LSHL:
                return new BinaryOperation(Operator.SHIFTL, l, r);
            case SymbolicOpcodes.LSHR:
                return new BinaryOperation(Operator.SHIFTR, l, r);
            case SymbolicOpcodes.LUSHR:
                return new BinaryOperation(Operator.SHIFTUR, l, r);
            default:
                return null;
        }
    }

    /**
     * FADD/FSUB/FMUL/FDIV/FREM and FCMPL/FCMPG. Comparisons return null —
     * callers should record a path constraint and not propagate a tag.
     */
    public static Expression floatBinary(int opcode, Expression l, Expression r) {
        if (l == null && r == null) return null;
        switch (opcode) {
            case SymbolicOpcodes.FADD:
                return new BinaryOperation(Operator.ADD, l, r);
            case SymbolicOpcodes.FSUB:
                return new BinaryOperation(Operator.SUB, l, r);
            case SymbolicOpcodes.FMUL:
                return new BinaryOperation(Operator.MUL, l, r);
            case SymbolicOpcodes.FDIV:
                return new BinaryOperation(Operator.DIV, l, r);
            case SymbolicOpcodes.FREM:
                // JPF/Choco doesn't support real MOD; original code returned null.
                return null;
            case SymbolicOpcodes.FCMPL:
            case SymbolicOpcodes.FCMPG:
                return null;
            default:
                return null;
        }
    }

    /**
     * DADD/DSUB/DMUL/DDIV/DREM.
     */
    public static Expression doubleBinary(int opcode, Expression l, Expression r) {
        if (l == null && r == null) return null;
        switch (opcode) {
            case SymbolicOpcodes.DADD:
                return new BinaryOperation(Operator.ADD, l, r);
            case SymbolicOpcodes.DSUB:
                return new BinaryOperation(Operator.SUB, l, r);
            case SymbolicOpcodes.DMUL:
                return new BinaryOperation(Operator.MUL, l, r);
            case SymbolicOpcodes.DDIV:
                return new BinaryOperation(Operator.DIV, l, r);
            case SymbolicOpcodes.DREM:
                return null;
            default:
                return null;
        }
    }

    /**
     * LCMP — produces an int. Original Knarr emitted the comparison as a
     * direct path constraint and did not propagate a result tag. We keep that
     * behaviour and return null.
     */
    public static Expression longCmp(Expression l, Expression r) {
        return null;
    }

    /**
     * DCMPL/DCMPG — produces an int. Same convention as LCMP: the listener
     * records a path constraint, no result tag.
     */
    public static Expression doubleCmp(int opcode, Expression l, Expression r) {
        return null;
    }

    // ---------- Unary ----------

    /**
     * INEG: arithmetic negation. I2B/I2C/I2S: cat-1 conversions that stay
     * int-sized but truncate.
     */
    public static Expression intUnary(int opcode, Expression e) {
        if (e == null) return null;
        switch (opcode) {
            case SymbolicOpcodes.INEG:
                return new UnaryOperation(Operator.NEG, e);
            case SymbolicOpcodes.I2B: {
                Expression trunc = new UnaryOperation(Operator.EXTRACT, 7, 0, e);
                return new UnaryOperation(Operator.SIGN_EXT, 24, trunc);
            }
            case SymbolicOpcodes.I2C: {
                // Original PathUtils used BIT_AND with 0x0000FFFF; we keep that
                // form for compatibility with downstream optimizers.
                return new BinaryOperation(Operator.BIT_AND, e, new IntConstant(0x0000FFFF));
            }
            case SymbolicOpcodes.I2S: {
                // Original PathUtils also produced (e & 0x0000FFFF) for I2S
                // (matching the unsigned mask), then later overwrote that with
                // a sign-extended truncation that was discarded. We follow the
                // surviving behaviour: BIT_AND with 0x0000FFFF.
                return new BinaryOperation(Operator.BIT_AND, e, new IntConstant(0x0000FFFF));
            }
            default:
                return e;
        }
    }

    public static Expression floatUnary(int opcode, Expression e) {
        if (e == null) return null;
        if (opcode == SymbolicOpcodes.FNEG) {
            return new UnaryOperation(Operator.NEG, e);
        }
        return e;
    }

    public static Expression longUnary(int opcode, Expression e) {
        if (e == null) return null;
        if (opcode == SymbolicOpcodes.LNEG) {
            return new UnaryOperation(Operator.NEG, e);
        }
        return e;
    }

    public static Expression doubleUnary(int opcode, Expression e) {
        if (e == null) return null;
        if (opcode == SymbolicOpcodes.DNEG) {
            return new UnaryOperation(Operator.NEG, e);
        }
        return e;
    }

    // ---------- Size-changing conversions ----------

    /**
     * Handles all of: I2L, I2F, I2D, F2I, F2L, F2D, L2I, L2F, L2D, D2I, D2F, D2L.
     *
     * <p>Floats and doubles share the same Real representation in Green, so
     * F-to-D and D-to-F propagate unchanged. Conversions involving floats and
     * I2F/I2D/F2I/F2L/D2I/D2F are not supported by the original Knarr
     * pipeline (PathUtils returned null taints with DISABLE_FLOATS=true) so
     * we return null here.
     */
    public static Expression convert(int opcode, Expression e) {
        if (e == null) return null;
        switch (opcode) {
            case SymbolicOpcodes.I2L:
                return new UnaryOperation(Operator.SIGN_EXT, 32, e);
            case SymbolicOpcodes.L2I:
                return new UnaryOperation(Operator.EXTRACT, 31, 0, e);
            case SymbolicOpcodes.L2D:
                return new UnaryOperation(Operator.I2R, e);
            case SymbolicOpcodes.D2L:
                return new UnaryOperation(Operator.R2I, e);
            case SymbolicOpcodes.F2D:
                // Floats and doubles share representation in Green; pass through.
                return e;
            case SymbolicOpcodes.D2F:
                return e;
            case SymbolicOpcodes.I2F:
            case SymbolicOpcodes.I2D:
            case SymbolicOpcodes.F2I:
            case SymbolicOpcodes.F2L:
            case SymbolicOpcodes.L2F:
            case SymbolicOpcodes.D2I:
                // Not supported by Knarr's solver pipeline.
                return null;
            default:
                return e;
        }
    }
}
