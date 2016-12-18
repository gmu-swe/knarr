package edu.gmu.swe.knarr.runtime;


import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import sun.misc.VM;
import edu.columbia.cs.psl.phosphor.TaintUtils;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Opcodes;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.util.Printer;
import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.struct.LazyArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.TaintedBooleanWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedDoubleWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedFloatWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedIntWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedLongWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedPrimitiveWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedWithObjTag;
import gov.nasa.jpf.Config;
import gov.nasa.jpf.symbc.SymbolicInstructionFactory;
import gov.nasa.jpf.symbc.numeric.Comparator;
import gov.nasa.jpf.symbc.numeric.Expression;
import gov.nasa.jpf.symbc.numeric.IntegerConstant;
import gov.nasa.jpf.symbc.numeric.IntegerExpression;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.symbc.numeric.RealConstant;
import gov.nasa.jpf.symbc.numeric.RealExpression;
import gov.nasa.jpf.symbc.numeric.SymbolicInteger;
import gov.nasa.jpf.symbc.string.DerivedStringExpression;
import gov.nasa.jpf.symbc.string.StringComparator;
import gov.nasa.jpf.symbc.string.StringConstant;
import gov.nasa.jpf.symbc.string.StringExpression;
import gov.nasa.jpf.symbc.string.StringOperator;

public class PathUtils {
	private static PathCondition curPC;
	public static final boolean IGNORE_SHIFTS = true;
	public static final String INTERNAL_NAME = "edu/gmu/swe/knarr/runtime/PathUtils";

	public static PathCondition getCurPC()
	{
		if(curPC == null)
			curPC = new PathConditionWrapper();
		return curPC;
	}
	
	static HashSet<String> usedLabels = new HashSet<String>();
	public static void checkLabelAndInitJPF(String label)
	{
		if(label == null || usedLabels.contains(label))
			throw new IllegalArgumentException("Invalid (dup?) label: \""+label+"\"");
		if(label.contains(" "))
			throw new IllegalArgumentException("label has spaces, but must not: \""+label+"\"");
		if(((TaintedWithObjTag)(Object)label).getPHOSPHOR_TAG() != null)
			throw new IllegalArgumentException("label has non-zero taint");
		usedLabels.add(label);
		if(!JPFInited)
			initJPF();
	}

	public static TaintedLongWithObjTag performLongOp(Taint<Expression> lVal, long v2, Taint<Expression> rVal, long v1, int op, TaintedLongWithObjTag ret) {
		if (lVal == null && rVal == null)
			ret.taint = null;
		else
		{
			Expression l, r = null;
			if (lVal == null)
				l = new RealConstant(v2);
			else
				l = lVal.lbl;
			if (rVal == null)
				r = new RealConstant(v1);
			else
				r = rVal.lbl;
			ret.taint = registerBinaryOp(l, r, op);
		}
		switch (op) {
		case Opcodes.LADD:
			ret.val = v2 + v1;
			return ret;
		case Opcodes.LSUB:
			ret.val = v2 - v1;
			return ret;
		case Opcodes.LMUL:
			ret.val = v2 * v1;
			return ret;
		case Opcodes.LDIV:
			ret.val = v2 / v1;
			return ret;
		case Opcodes.LREM:
			ret.val = v2 % v1;
			return ret;
		case Opcodes.LOR:
			ret.val = v2 | v1;
			return ret;
		case Opcodes.LAND:
			ret.val = v2 & v1;
			return ret;
		case Opcodes.LXOR:
			ret.val = v2 ^ v1;
			return ret;
		}
		throw new IllegalArgumentException();
	}

	public static TaintedLongWithObjTag performLongOp(Taint<Expression> t2, long v2, Taint<Expression> t1, int v1, int op, TaintedLongWithObjTag ret) {
		switch (op) {
		case Opcodes.LSHL:
			ret.taint = null;
			ret.val = v2 << v1;
			return ret;
		case Opcodes.LSHR:
			ret.taint = null;
			ret.val = v2 >> v1;
			return ret;
		case Opcodes.LUSHR:
			ret.taint = null;
			ret.val = v2 >>> v1;
			return ret;
		}
		throw new IllegalArgumentException();
	}

	public static TaintedDoubleWithObjTag performDoubleOp(Taint<Expression> lVal, double v2, Taint<Expression> rVal, double v1, int op, TaintedDoubleWithObjTag ret) {
		if (lVal == null && rVal == null)
			ret.taint = null;
		else
		{
			Expression l, r;
			if (lVal == null)
				l = new RealConstant(v2);
			else
				l = lVal.lbl;
			if (rVal == null)
				r = new RealConstant(v1);
			else
				r = rVal.lbl;
			ret.taint = registerBinaryOp(l, r, op);
		}
		switch (op) {
		case Opcodes.DADD:
			ret.val = v1 + v2;
			return ret;
		case Opcodes.DREM:
			ret.val = v2 % v1;
			return ret;
		case Opcodes.DSUB:
			ret.val = v2 - v1;
			return ret;
		case Opcodes.DMUL:
			ret.val = v2 * v1;
			return ret;
		case Opcodes.DDIV:
			ret.val = v2 / v1;
			return ret;
		default:
			throw new IllegalArgumentException();
		}
	}
	public static TaintedFloatWithObjTag performFloatOp(Taint<Expression> lVal, float v2, Taint<Expression> rVal, float v1, int op, TaintedFloatWithObjTag ret) {
		if (lVal == null && rVal == null)
			ret.taint = null;
		else
		{
			Expression l, r;
			if (lVal == null)
				l = new RealConstant(v2);
			else
				l = lVal.lbl;
			if (rVal == null)
				r = new RealConstant(v1);
			else
				r = rVal.lbl;
			ret.taint = registerBinaryOp(l, r, op);
		}
		switch (op) {
		case Opcodes.FADD:
			ret.val = v2 + v1;
			return ret;
		case Opcodes.FREM:
			ret.val = v2 % v1;
			return ret;
		case Opcodes.FSUB:
			ret.val = v2 - v1;
			return ret;
		case Opcodes.FMUL:
			ret.val = v2 * v1;
			return ret;
		case Opcodes.FDIV:
			ret.val = v2 / v1;
			return ret;
		default:
			throw new IllegalArgumentException();
		}
	}

	public static ExpressionTaint performFloatOp(Taint<Expression> lVal, Taint<Expression> rVal, float v2, float v1, int op) {
		Expression l, r = null;
		if (lVal == null && rVal == null)
			return null;
		else {
			if (lVal == null)
				l = new RealConstant(v2);
			else
				l = lVal.lbl;
			if (rVal == null)
				r = new RealConstant(v1);
			else
				r = rVal.lbl;
		}
		return registerBinaryOp(l, r, op);
	}
	
	private static Expression getExpression(Taint<Expression> t, int v) {
		if(t == null)
			return new IntegerConstant(v);
		return t.lbl;
	}
	private static Expression getExpression(Taint<Expression> t, float v) {
		if(t == null)
			return new RealConstant(v);
		return t.lbl;
	}
	private static Expression getExpression(Taint<Expression> t, long v) {
		if(t == null)
			return new RealConstant(v);
		return t.lbl;
	}
	private static Expression getExpression(Taint<Expression> t, double v) {
		if(t == null)
			return new RealConstant(v);
		return t.lbl;
	}
	
	public static TaintedIntWithObjTag IADD(Taint<Expression> lVal, int v2, Taint<Expression> rVal, int v1, TaintedIntWithObjTag ret)
	{
		ret.val = v2 + v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.IADD);
		return ret;
	}

	public static TaintedIntWithObjTag ISUB(Taint<Expression> lVal, int v2, Taint<Expression> rVal, int v1, TaintedIntWithObjTag ret)
	{
		ret.val = v2 - v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.ISUB);
		return ret;
	}
	
	public static TaintedIntWithObjTag IDIV(Taint<Expression> lVal, int v2, Taint<Expression> rVal, int v1, TaintedIntWithObjTag ret)
	{
		ret.val = v2 / v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.IDIV);
		return ret;
	}
	
	public static TaintedIntWithObjTag IREM(Taint<Expression> lVal, int v2, Taint<Expression> rVal, int v1, TaintedIntWithObjTag ret)
	{
		ret.val = v2 % v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.IREM);
		return ret;
	}
	
	public static TaintedIntWithObjTag ISHL(Taint<Expression> lVal, int v2, Taint<Expression> rVal, int v1, TaintedIntWithObjTag ret)
	{
		ret.val = v2 << v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.ISHL);
		return ret;
	}
	
	public static TaintedIntWithObjTag ISHR(Taint<Expression> lVal, int v2, Taint<Expression> rVal, int v1, TaintedIntWithObjTag ret)
	{
		ret.val = v2 >> v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.ISHR);
		return ret;
	}
	
	public static TaintedIntWithObjTag IUSHR(Taint<Expression> lVal, int v2, Taint<Expression> rVal, int v1, TaintedIntWithObjTag ret)
	{
		ret.val = v2 >>> v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.IUSHR);
		return ret;
	}
	
	public static TaintedIntWithObjTag IOR(Taint<Expression> lVal, int v2, Taint<Expression> rVal, int v1, TaintedIntWithObjTag ret)
	{
		ret.val = v2 | v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.IOR);
		return ret;
	}
	
	
	public static TaintedIntWithObjTag IAND(Taint<Expression> lVal, int v2, Taint<Expression> rVal, int v1, TaintedIntWithObjTag ret)
	{
		ret.val = v2 & v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.IAND);
		return ret;
	}
	
	public static TaintedIntWithObjTag IMUL(Taint<Expression> lVal, int v2, Taint<Expression> rVal, int v1, TaintedIntWithObjTag ret)
	{
		ret.val = v2 * v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.IMUL);
		return ret;
	}
	
	public static TaintedIntWithObjTag IXOR(Taint<Expression> lVal, int v2, Taint<Expression> rVal, int v1, TaintedIntWithObjTag ret)
	{
		ret.val = v2 ^ v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.IXOR);
		return ret;
	}
	
	public static TaintedFloatWithObjTag FADD(Taint<Expression> lVal, float v2, Taint<Expression> rVal, float v1, TaintedFloatWithObjTag ret)
	{
		ret.val = v2 + v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.FADD);
		return ret;
	}
	public static TaintedFloatWithObjTag FSUB(Taint<Expression> lVal, float v2, Taint<Expression> rVal, float v1, TaintedFloatWithObjTag ret)
	{
		ret.val = v2 - v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.FSUB);
		return ret;
	}
	public static TaintedFloatWithObjTag FREM(Taint<Expression> lVal, float v2, Taint<Expression> rVal, float v1, TaintedFloatWithObjTag ret)
	{
		ret.val = v2 % v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.FREM);
		return ret;
	}
	public static TaintedFloatWithObjTag FDIV(Taint<Expression> lVal, float v2, Taint<Expression> rVal, float v1, TaintedFloatWithObjTag ret)
	{
		ret.val = v2 / v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.FDIV);
		return ret;
	}
	public static TaintedFloatWithObjTag FMUL(Taint<Expression> lVal, float v2, Taint<Expression> rVal, float v1, TaintedFloatWithObjTag ret)
	{
		ret.val = v2 * v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.FMUL);
		return ret;
	}

	public static TaintedDoubleWithObjTag DADD(Taint<Expression> lVal, double v2, Taint<Expression> rVal, double v1, TaintedDoubleWithObjTag ret)
	{
		ret.val = v2 + v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.DADD);
		return ret;
	}
	
	public static TaintedDoubleWithObjTag DSUB(Taint<Expression> lVal, double v2, Taint<Expression> rVal, double v1, TaintedDoubleWithObjTag ret)
	{
		ret.val = v2 - v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.DSUB);
		return ret;
	}
	
	public static TaintedDoubleWithObjTag DDIV(Taint<Expression> lVal, double v2, Taint<Expression> rVal, double v1, TaintedDoubleWithObjTag ret)
	{
		ret.val = v2 / v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.DDIV);
		return ret;
	}
	
	public static TaintedDoubleWithObjTag DREM(Taint<Expression> lVal, double v2, Taint<Expression> rVal, double v1, TaintedDoubleWithObjTag ret)
	{
		ret.val = v2 % v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.DREM);
		return ret;
	}
	
	public static TaintedDoubleWithObjTag DMUL(Taint<Expression> lVal, double v2, Taint<Expression> rVal, double v1, TaintedDoubleWithObjTag ret)
	{
		ret.val = v2 * v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.DMUL);
		return ret;
	}
	
	public static TaintedLongWithObjTag LADD(Taint<Expression> lVal, long v2, Taint<Expression> rVal, long v1, TaintedLongWithObjTag ret)
	{
		ret.val = v2 + v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.LADD);
		return ret;
	}
	public static TaintedLongWithObjTag LSUB(Taint<Expression> lVal, long v2, Taint<Expression> rVal, long v1, TaintedLongWithObjTag ret)
	{
		ret.val = v2 - v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.LSUB);
		return ret;
	}
	public static TaintedLongWithObjTag LREM(Taint<Expression> lVal, long v2, Taint<Expression> rVal, long v1, TaintedLongWithObjTag ret)
	{
		ret.val = v2 % v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.LREM);
		return ret;
	}
	public static TaintedLongWithObjTag LDIV(Taint<Expression> lVal, long v2, Taint<Expression> rVal, long v1, TaintedLongWithObjTag ret)
	{
		ret.val = v2 / v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.LDIV);
		return ret;
	}
	public static TaintedLongWithObjTag LMUL(Taint<Expression> lVal, long v2, Taint<Expression> rVal, long v1, TaintedLongWithObjTag ret)
	{
		ret.val = v2 * v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.LMUL);
		return ret;
	}
	public static TaintedLongWithObjTag LOR(Taint<Expression> lVal, long v2, Taint<Expression> rVal, long v1, TaintedLongWithObjTag ret)
	{
		ret.val = v2 | v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.LOR);
		return ret;
	}
	public static TaintedLongWithObjTag LXOR(Taint<Expression> lVal, long v2, Taint<Expression> rVal, long v1, TaintedLongWithObjTag ret)
	{
		ret.val = v2 ^ v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.LXOR);
		return ret;
	}
	public static TaintedLongWithObjTag LAND(Taint<Expression> lVal, long v2, Taint<Expression> rVal, long v1, TaintedLongWithObjTag ret)
	{
		ret.val = v2 & v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.LAND);
		return ret;
	}
	
	public static TaintedLongWithObjTag LSHL(Taint<Expression> lVal, long v2, Taint<Expression> rVal, int v1, TaintedLongWithObjTag ret)
	{
		ret.val = v2 << v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.LSHL);
		return ret;
	}
	
	public static TaintedLongWithObjTag LSHR(Taint<Expression> lVal, long v2, Taint<Expression> rVal, int v1, TaintedLongWithObjTag ret)
	{
		ret.val = v2 >> v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.LSHR);
		return ret;
	}
	
	public static TaintedLongWithObjTag LUSHR(Taint<Expression> lVal, long v2, Taint<Expression> rVal, int v1, TaintedLongWithObjTag ret)
	{
		ret.val = v2 >>> v1;
		if (lVal == null && rVal == null)
		{
			ret.taint = null;
			return ret;
		}
		ret.taint = registerBinaryOp(getExpression(lVal, v2), getExpression(rVal, v1), Opcodes.LUSHR);
		return ret;
	}
	
	static Object lock = new Object();
	public static TaintedIntWithObjTag LCMP(Taint<Expression> rVal, long v1, Taint<Expression> lVal, long v2, TaintedIntWithObjTag ret) {
		/*
		 * Takes two two-word long integers off the stack and compares them. If
		 * the two integers are the same, the int 0 is pushed onto the stack. If
		 * value2 is greater than value1, the int 1 is pushed onto the stack. If
		 * value1 is greater than value2, the int -1 is pushed onto the stack.
		 */
		if (v1 == v2)
			ret.val = 0;
		else if (v1 < v2)
			ret.val = -1;
		else
			ret.val = 1;
		if(lVal == null && rVal== null)
			ret.taint = null;
		else {
			Expression l, r;
			if (lVal == null)
				l = new RealConstant(v2);
			else
				l = lVal.lbl;
			if (rVal == null)
				r = new RealConstant(v1);
			else
				r = rVal.lbl;
			ret.taint = null;
			if(v1 == v2)
				getCurPC()._addDet(Comparator.EQ, l, r);
			else if (v1 < v2)
				getCurPC()._addDet(Comparator.GT, l, r);
			else
				getCurPC()._addDet(Comparator.LT, l, r);
		}
		return ret;
	}

	public static TaintedIntWithObjTag FCMPL(Taint<Expression> rVal, float v1, Taint<Expression> lVal, float v2, TaintedIntWithObjTag ret) {
		if (v1 == Float.NaN || v2 == Float.NaN)
			ret.val = 1;
		else if (v1 == v2)
			ret.val = 0;
		else if (v1 < v2)
			ret.val = -1;
		else
			ret.val = 1;
		if(lVal == null && rVal== null)
			ret.taint = null;
		else {
			Expression l, r;
			if (lVal == null)
				l = new RealConstant(v2);
			else
				l = lVal.lbl;
			if (rVal == null)
				r = new RealConstant(v1);
			else
				r = rVal.lbl;
			ret.taint = null;
			if(v1 == v2)
				getCurPC()._addDet(Comparator.EQ, l, r);
			else if (v1 < v2)
				getCurPC()._addDet(Comparator.GT, l, r);
			else
				getCurPC()._addDet(Comparator.LT, l, r);
		}
		return ret;
	}

	public static TaintedIntWithObjTag FCMPG(Taint<Expression> rVal, float v1, Taint<Expression> lVal, float v2, TaintedIntWithObjTag ret) {
		if (v1 == Float.NaN || v2 == Float.NaN)
			ret.val = -1;
		else if (v1 == v2)
			ret.val = 0;
		else if (v1 < v2)
			ret.val = -1;
		else
			ret.val = 1;
		if(lVal == null && rVal== null)
			ret.taint = null;
		else {
			Expression l, r;
			if (lVal == null)
				l = new RealConstant(v2);
			else
				l = lVal.lbl;
			if (rVal == null)
				r = new RealConstant(v1);
			else
				r = rVal.lbl;
			ret.taint = null;
			if(v1 == v2)
				getCurPC()._addDet(Comparator.EQ, l, r);
			else if (v1 < v2)
				getCurPC()._addDet(Comparator.GT, l, r);
			else
				getCurPC()._addDet(Comparator.LT, l, r);
			}
		return ret;
	}

	public static TaintedIntWithObjTag DCMPL(Taint<Expression> rVal, double v1, Taint<Expression> lVal, double v2, TaintedIntWithObjTag ret) {
		if (v1 == Double.NaN || v2 == Double.NaN)
			ret.val = 1;
		else if (v1 == v2)
			ret.val = 0;
		else if (v1 < v2)
			ret.val = -1;
		else
			ret.val = 1;
		if(lVal == null && rVal== null)
			ret.taint = null;
		else {
			Expression l, r;
			if (lVal == null)
				l = new RealConstant(v2);
			else
				l = lVal.lbl;
			if (rVal == null)
				r = new RealConstant(v1);
			else
				r = rVal.lbl;
			ret.taint = null;
			if(v1 == v2)
				getCurPC()._addDet(Comparator.EQ, l, r);
			else if (v1 < v2)
				getCurPC()._addDet(Comparator.GT, l, r);
			else
				getCurPC()._addDet(Comparator.LT, l, r);
			}
		return ret;
	}

	public static TaintedIntWithObjTag DCMPG(Taint<Expression> rVal, double v1, Taint<Expression> lVal, double v2, TaintedIntWithObjTag ret) {
		if (v1 == Double.NaN || v2 == Double.NaN)
			ret.val = -1;
		else if (v1 == v2)
			ret.val = 0;
		else if (v1 < v2)
			ret.val = -1;
		else
			ret.val = 1;
		if(lVal == null && rVal== null)
			ret.taint = null;
		else {
			Expression l, r;
			if (lVal == null)
				l = new RealConstant(v2);
			else
				l = lVal.lbl;
			if (rVal == null)
				r = new RealConstant(v1);
			else
				r = rVal.lbl;
			ret.taint = null;
			if(v1 == v2)
				getCurPC()._addDet(Comparator.EQ, l, r);
			else if (v1 < v2)
				getCurPC()._addDet(Comparator.GT, l, r);
			else
				getCurPC()._addDet(Comparator.LT, l, r);
			}
		return ret;
	}
	
	public static void generateMultiDTaintArray(Object in, Object taintRef) {
		//Precondition is that taintArrayRef is an array with the same number of dimensions as obj, with each allocated.
		for (int i = 0; i < Array.getLength(in); i++) {
			Object entry = Array.get(in, i);
			Class<?> clazz = entry.getClass();
			if (clazz.isArray()) {
				//Multi-D array
				int innerDims = Array.getLength(entry);
				Array.set(taintRef, i, Array.newInstance(Expression.class, innerDims));
				registerAllConstantsArray(entry, taintRef);
			}
		}
	}

	public static void registerAllConstantsArray(Object obj, Object taintArrayRef) {
		//Precondition is that taintArrayRef is an array with the same number of dimensions as obj, with each allocated.
		for (int i = 0; i < Array.getLength(obj); i++) {
			Object entry = Array.get(obj, i);
			Class<?> clazz = entry.getClass();
			if (clazz.isArray()) {
				//Multi-D array
				int innerDims = Array.getLength(entry);
				Array.set(taintArrayRef, i, Array.newInstance(Expression.class, innerDims));
				registerAllConstantsArray(entry, taintArrayRef);
			} else {
//				if (clazz == Integer.TYPE) {
//					Array.set(taintArrayRef, i, registerConcrete(((Integer) entry).intValue()));
//				} else if (clazz == Boolean.TYPE) {
//					Array.set(taintArrayRef, i, registerConcrete(((Boolean) entry).booleanValue()));
//				} else if (clazz == Byte.TYPE) {
//					Array.set(taintArrayRef, i, registerConcrete(((Byte) entry).byteValue()));
//				} else if (clazz == Character.TYPE) {
//					Array.set(taintArrayRef, i, registerConcrete(((Character) entry).charValue()));
//				} else if (clazz == Double.TYPE) {
//					Array.set(taintArrayRef, i, registerConcrete(((Double) entry).doubleValue()));
//				} else if (clazz == Float.TYPE) {
//					Array.set(taintArrayRef, i, registerConcrete(((Float) entry).floatValue()));
//				} else if (clazz == Short.TYPE) {
//					Array.set(taintArrayRef, i, registerConcrete(((Short) entry).shortValue()));
//				}
			}
		}
	}

	public static void registerAllConstants(Object obj) {
		if (obj.getClass().isArray()) {
//			int arrayTaint = registerTaintOnArray(obj);
			for (int i = 0; i < Array.getLength(obj); i++) {
				Object entry = Array.get(obj, i);
				if (entry.getClass().isArray()) {
					registerAllConstants(entry);
				} else if (entry instanceof String) {
//					int valTaint = registerConcrete((String) entry);
//					registerArrayStore(arrayTaint, valTaint, i);
				}
			}
		}
	}
	
	private static IntegerConstant ICONST_0;
	static boolean JPFInited = false;
	
	static void initJPF()
	{
		if(!JPFInited)
		{
			ICONST_0 = new IntegerConstant(0);
			JPFInited = true;
			String[] options = { "+symbolic.dp=choco", "+symbolic.string_dp=sat", "+symbolic.string_dp_timeout_ms=0" };
			Config cfg = new Config(options);
			new SymbolicInstructionFactory(cfg);
			System.out.println("JPF inited");

		}
	}
	public static void addConstraint(Taint<Expression> t, int opcode) {
		if (t == null)
			return;
		if(!JPFInited)
			initJPF();
		IntegerExpression exp =  (IntegerExpression) t.lbl;
		switch(opcode)
		{
		case Opcodes.IFEQ:
			getCurPC()._addDet(Comparator.EQ, ICONST_0, exp);
			break;
		case Opcodes.IFGE:
			getCurPC()._addDet(Comparator.GE, exp, ICONST_0);
			break;
		case Opcodes.IFLE:
			getCurPC()._addDet(Comparator.LE, exp, ICONST_0);
			break;
		case Opcodes.IFLT:
			getCurPC()._addDet(Comparator.LT, exp, ICONST_0);
			break;
		case Opcodes.IFGT:
			getCurPC()._addDet(Comparator.GT, exp, ICONST_0);
			break;
		case Opcodes.IFNE:
			getCurPC()._addDet(Comparator.NE,exp, ICONST_0);
			break;
		default:
				throw new IllegalArgumentException("Unimplemented branch type: " + Printer.OPCODES[opcode]);
		}
	}
	public static void addConstraint(Expression lExp, Expression rExp, Object v1, Object v2, int opcode) {
	
	}
	public static void addConstraint(Taint<Expression> l, Taint<Expression> r, int v1, int v2, int opcode) {
		//		if (VM.isBooted$$INVIVO_PC(new TaintedBoolean()).val && values.get(otherTaint) == null)
		//			System.out.println(Printer.OPCODES[opcode] + " - " + taint + " ; " + otherTaint);
		if(l == null && r == null)
			return;
		Expression lExp, rExp;
		if(l == null)
			lExp = new IntegerConstant(v1);
		else
			lExp = l.lbl;
		if(r == null)
			rExp = new IntegerConstant(v2);
		else
			rExp = r.lbl;
		switch (opcode) {
		case Opcodes.IF_ACMPEQ:
		case Opcodes.IF_ACMPNE:
			//TODO - object equality constraints?
			break;
		case Opcodes.IF_ICMPEQ:
			getCurPC()._addDet(Comparator.EQ, (IntegerExpression) lExp, (IntegerExpression) rExp);
			break;
		case Opcodes.IF_ICMPGE:
			getCurPC()._addDet(Comparator.GE, (IntegerExpression) lExp, (IntegerExpression) rExp);
			break;
		case Opcodes.IF_ICMPGT:
			getCurPC()._addDet(Comparator.GT, (IntegerExpression) lExp, (IntegerExpression) rExp);
			break;
		case Opcodes.IF_ICMPLE:
			getCurPC()._addDet(Comparator.LE, (IntegerExpression) lExp, (IntegerExpression) rExp);
			break;
		case Opcodes.IF_ICMPLT:
			//			System.out.println("Other one is " + branches[branch].rVal.concreteValue_int+ "...."+branches[branch].rVal.expression);
			getCurPC()._addDet(Comparator.LT, (IntegerExpression) lExp, (IntegerExpression) rExp);
			break;
		case Opcodes.IF_ICMPNE:
			getCurPC()._addDet(Comparator.NE, (IntegerExpression) lExp, (IntegerExpression) rExp);
			break;
		default:
			throw new IllegalArgumentException("Unimplemented branch type: " + Printer.OPCODES[opcode]);
		}

	}


	/**
	 * Adds a constraint that the xpression passed in an instance of a type
	 * 
	 * @param taint
	 * @param clazz
	 * @return
	 */
	public static Expression addInstanceOfConstraint(Expression exp, String clazz) {
//		if (taint == 0)
			return null;
//		synchronized (lock) {
//			int ret = nextTaint();
//			ConstrainedValue v = new ConstrainedValue();
//			v.taintID = ret;
//			Constraint c = new Constraint();
//			c.opcode = Opcodes.INSTANCEOF;
//			c.lVal = values.get(taint);
//			c.rVal = new ConstrainedValue();
//			c.rVal.concreteValue = clazz;
//			v.constraints.add(c);
//			values.add(v);
//			v.expression = new SymbolicInteger(); //TODO implement instanceof constraint??
//			return ret;
//		}
	}
	public static HashMap<Object, Expression> arraysHash = new HashMap<Object, Expression>();

	/**
	 * Returns a new taint value that has the constraint that it's the result of
	 * arraylength from the passed taint to clazz
	 * 
	 * @param taint
	 * @return
	 */
	public static Expression addArrayLengthConstraint(Expression expr) {
		if (expr == null)
			return null;

		if (expr.related != null)
			return expr.related;
		expr.related = new SymbolicInteger(((SymbolicInteger) expr).getName() + "_Length", 0, Integer.MAX_VALUE);
		return expr.related;
	}

	public static void registerArrayLength(Object array, int idx) {
		if (array != null && VM.isBooted())
			if (arraysHash.containsKey(array)) {
				Expression obj = arraysHash.get(array);
				((SymbolicInteger) obj)._min = Math.max(((SymbolicInteger) obj)._min, idx + 1);
			}

	}
	static AtomicInteger uniq;
	public static void registerUnaryToTaint(TaintedPrimitiveWithObjTag ret, Expression _exp2, int opcode) {
//		if(((StringExpression)values.get(taint2).expression).getName().getPHOSPHORTAG() != 0)
//		throw new IllegalArgumentException("Got a non-zero taint on the name of this: " + ((StringExpression)values.get(taint2).expression).getName().getPHOSPHORTAG());
//		if(_exp2 == null)
//		{
//			ret.taint = null;
//			return;
//		}
//		if(!JPFInited) initJPF();
//		if(uniq == null)
//			uniq = new AtomicInteger();
//		StringExpression exp2 = (StringExpression) _exp2;
//		switch (opcode) {
//		case StringOpcodes.STR_LEN:
//			if (exp2 != null)
//			{
//				ret.taint = exp2._length();
//			}
//			break;
//		default:
			throw new IllegalArgumentException("unimplemented string op: " + opcode);
//		}
	}

	public static ExpressionTaint registerBinaryStringOp(String strL, String strR, int opcode) {
		if (strL == null || strR == null) {
			return null;
		}
		if (((TaintedWithObjTag) (Object) strL).getPHOSPHOR_TAG() == null && ((TaintedWithObjTag) (Object) strR).getPHOSPHOR_TAG() == null) {
			return null;
		}
		Expression l, r = null;
		ExpressionTaint lVal = (ExpressionTaint) ((TaintedWithObjTag) (Object) strL).getPHOSPHOR_TAG();
		ExpressionTaint rVal = (ExpressionTaint) ((TaintedWithObjTag) (Object) strR).getPHOSPHOR_TAG();
		if(lVal == null)
			l = new StringConstant(strL);
		else
			l = lVal.lbl;
		if(rVal == null)
			r = new StringConstant(strR);
		else
			r = rVal.lbl;
		return registerBinaryOp(l, r, opcode);
	}

	/**
	 * Returns a new taint value that has a constraint that indiciates that it's
	 * the result of unary op opcode on taint.
	 * 
	 * @param taint
	 * @param opcode
	 * @return
	 */
	public static Taint<Expression> registerUnaryOp(Taint<Expression> exp, int opcode) {

		if (exp == null)
			return null;
		if(!JPFInited) initJPF();
		
		Expression ret;
		switch (opcode) {
		case Opcodes.INEG:
		case Opcodes.LNEG:
			ret = ((IntegerExpression) exp.lbl )._neg();
			break;
		case Opcodes.FNEG:
		case Opcodes.DNEG:
			ret = ((RealExpression) exp.lbl)._neg();
			break;
		default:
			throw new IllegalStateException("Unimplemented opcode handler: " + Printer.OPCODES[opcode]);
		}
		if(ret == null)
		{
			throw new IllegalArgumentException("Returning null expression!");
		}
		//		v.expression = ((IntegerExpression)values.get(taint).expression).
		exp.lbl = ret;
		return exp;
	}

	public static void registerStringBooleanOp(TaintedBooleanWithObjTag res, String str1, Object str2, int op) {
		Expression t1 = null;
		Expression t2 = null;
		if (str1 != null)
			t1 = (Expression) str1.getPHOSPHOR_TAG();
		if(!(str2 instanceof String))
			return;
		if (str2 != null)
			t2 = (Expression) ((TaintedWithObjTag) str2).getPHOSPHOR_TAG();
		if (t1 == null && t2 == null)
			return;
		StringComparator strCmp;

		if(res.val) //result is true
		{
			switch (op) {
			case StringOpcodes.STR_EQUAL:
				strCmp = StringComparator.EQUALS;
				break;
			case StringOpcodes.STR_START:
				strCmp = StringComparator.STARTSWITH;
				break;
			default:
				throw new IllegalArgumentException("Unkown op: " + op);
			}
		}
		else	//result is false
		{
			switch (op) {
			case StringOpcodes.STR_EQUAL:
				strCmp = StringComparator.NOTEQUALS;
				break;
			case StringOpcodes.STR_START:
				strCmp = StringComparator.NOTSTARTSWITH;
				break;
			default:
				throw new IllegalArgumentException("Unkown op: " + op);
			}
		}
		Expression lVal, rVal;
		synchronized (lock) {
			lVal = (t2 == null) ? new StringConstant((String) str2) : t2;
			rVal = (t1 == null) ? new StringConstant(str1) : t1;
		}
		getCurPC().spc._addDet(strCmp, (StringExpression) rVal, (StringExpression) lVal);
	}

	public static void registerStringBooleanOp(TaintedBooleanWithObjTag res, String str1, int op) {
		Expression t1 = null;
		if (str1 != null)
			t1 = (Expression) ((TaintedWithObjTag) (Object) str1).getPHOSPHOR_TAG();

		if (t1 == null)
			return;
		StringComparator strCmp;
		if(res.val) //result is true
		{
			switch (op) {
			case StringOpcodes.STR_EMPTY:
				strCmp = StringComparator.EMPTY;
				break;
			default:
				throw new IllegalArgumentException();
			}
		}
		else	//result is false
		{
			switch (op) {
			case StringOpcodes.STR_EMPTY:
				strCmp = StringComparator.NOTEMPTY;
				break;
			default:
				throw new IllegalArgumentException();
			}			
		}
		getCurPC().spc._addDet(strCmp, (StringExpression) t1);
	}
	
	/**
	 * Returns a new taint value that has a constraint that indiciates that it's
	 * the result of binary op opcode on taint1, taint2.
	 * 
	 * @param taint1
	 * @param taint2
	 * @param opcode
	 * @return
	 */
	private static ExpressionTaint registerBinaryOp(Expression expr1, Expression expr2, int opcode) {
		if (expr1 == null && expr2 == null)
			return null;
		if(!JPFInited) initJPF();
		Expression ret = null;
		
		switch(opcode){
		case Opcodes.IADD:
		case Opcodes.LADD:
			ret = ((IntegerExpression) expr1)._plus((IntegerExpression) expr2);
			break;
		case Opcodes.FADD:
		case Opcodes.DADD:
			ret = ((RealExpression) expr1)._plus((RealExpression) expr2);
			break;
		case Opcodes.IMUL:
		case Opcodes.LMUL:
			ret = ((IntegerExpression) expr1)._mul((IntegerExpression) expr2);
			break;
		case Opcodes.FMUL:
		case Opcodes.DMUL:
			ret = ((RealExpression) expr1)._mul((RealExpression) expr2);
			break;
		case Opcodes.IDIV:
		case Opcodes.LDIV:
			ret = ((IntegerExpression) expr1)._div((IntegerExpression) expr2);
			break;
		case Opcodes.FDIV:
		case Opcodes.DDIV:
			ret = ((RealExpression) expr1)._div((RealExpression) expr2);
			break;
		case Opcodes.IREM:
		case Opcodes.LREM:
			ret = ((IntegerExpression) expr1)._rem((IntegerExpression) expr2);
			break;
		case Opcodes.DREM:
		case Opcodes.FREM:
			throw new IllegalArgumentException("haven't implemented drem/frem");
//			v.expression = ((RealExpression) lVal.expression)._(RealExpression) rVal.expression);
//			break;
		case Opcodes.ISUB:
		case Opcodes.LSUB:
			ret = ((IntegerExpression) expr1)._minus((IntegerExpression) expr2);
			break;
		case Opcodes.DSUB:
		case Opcodes.FSUB:
			ret = ((RealExpression) expr1)._minus((RealExpression) expr2);
			break;
		case Opcodes.IAND:
		case Opcodes.LAND:
			ret = ((IntegerExpression) expr1)._and((IntegerExpression) expr2);
			break;
		case Opcodes.IOR:
		case Opcodes.LOR:
			ret = ((IntegerExpression) expr1)._or((IntegerExpression) expr2);
			break;
		case Opcodes.ISHL:
		case Opcodes.LSHL:
			if(!IGNORE_SHIFTS)
				ret = ((IntegerExpression) expr1)._shiftL((IntegerExpression) expr2);
			else
				ret = new SymbolicInteger();
			break;
		case Opcodes.ISHR:
		case Opcodes.LSHR:
			if(!IGNORE_SHIFTS)
				ret = ((IntegerExpression) expr1)._shiftR((IntegerExpression) expr2);
			else
				ret = new SymbolicInteger();
			break;
		case Opcodes.IUSHR:
		case Opcodes.LUSHR:
			if(!IGNORE_SHIFTS)
				ret = ((IntegerExpression) expr1)._shiftUR((IntegerExpression) expr2);
			else
				ret = new SymbolicInteger();
			break;
		case Opcodes.IXOR:
		case Opcodes.LXOR:
			ret = ((IntegerExpression) expr1)._xor((IntegerExpression) expr2);
			break;
		case StringOpcodes.STR_CONCAT:
			ret = ((StringExpression) expr1)._concat(((StringExpression) expr2));
			break;
		case Opcodes.LCMP:
		case Opcodes.DCMPG:
		case Opcodes.DCMPL:
			ret = new SymbolicInteger(); //TODO support XCMP
			break;
		default:
			throw new IllegalArgumentException("Unimplemented binary opcode: " + Printer.OPCODES[opcode]);
		}
		return new ExpressionTaint(ret);
	}

	

	public static Expression registerStringOp(Expression rVal, int opcode) {
		if (rVal == null)
			return null;
		if (!JPFInited)
			initJPF();
		Expression ret;

		
		switch (opcode) {
		//TODO handle strcpy!!!
		case StringOpcodes.STR_CPY:
			ret = rVal;//StringExpression._valueOf((StringExpression) c.rVal.expression);
			break;
		case StringOpcodes.STR_TRIM:
			ret = ((StringExpression) rVal)._trim();
			break;
		case StringOpcodes.STR_UCASE:
			ret = new DerivedStringExpression(StringOperator.TOUPPERCASE, (StringExpression) rVal);
			break;
		case StringOpcodes.STR_LCASE:
			ret = new DerivedStringExpression(StringOperator.TOLOWERCASE, (StringExpression) rVal);
			break;
		case StringOpcodes.STR_TO_DOUBLE: //TODO support for str -> int/double
//			ret = ((StringExpression) rVal)._RvalueOf();
			ret = null;
			break;
		case StringOpcodes.STR_TO_LONG:
		case StringOpcodes.STR_TO_INT:
//			ret = ((StringExpression) rVal)._IvalueOf();
			ret = null; //This doesn't work with choco :(
			break;
		default:
			throw new IllegalArgumentException("got op " + opcode);
		}
		if(ret == null) throw new IllegalArgumentException("Null exp returned?");
		return ret;
	}
	
	public static Expression[] registerTaintOnArray(Object val, Object label) {
//		Expression[] ret = new Expression[0];
//
//		Expression length = addArrayLengthConstraint(ret);
//		
//		if (sun.misc.VM.isBooted())
//			arraysHash.put(val, length);
//		return ret;
		throw new UnsupportedOperationException();
	}
	public static Expression addArrayLengthConstraint(LazyArrayObjTags tags) {
		if (tags == null)
			return null;
		ExpressionTaint t = (ExpressionTaint) tags.lengthTaint;
		if(t != null)
			return t.lbl;
		return null;
	}
	

	public static void addReplaceAllConstraint(String returnedString, String fromString, String toString, String origString, int opCode) {
		if (origString.getPHOSPHOR_TAG() == null) {
			return;
		}

		//			Constraint c = new Constraint();
		//			c.opcode = opCode;
		//			c.lVal = values.get(fromTaint);
		//			c.rVal = values.get(toTaint);
		//			c.thirdVal = values.get(origTaint);
		//			v.constraints.add(c);
		StringExpression firstPart = (StringExpression) origString.getPHOSPHOR_TAG();
		StringExpression exp2 = (StringExpression) toString.getPHOSPHOR_TAG();
		StringExpression exp3 = (StringExpression)fromString.getPHOSPHOR_TAG();
		if (exp2 == null)
			exp2 = new StringConstant(toString);
		if (exp3 == null)
			exp3 = new StringConstant(fromString);

		switch (opCode) {
		case StringOpcodes.STR_REPLACE:
		case StringOpcodes.STR_REPLACEALL:
			returnedString.setPHOSPHOR_TAG(firstPart._replace(exp2, exp3));
			break;
		}

	}

	public static void addReplaceConstraint(String returnedString, CharSequence fromString, CharSequence toString, String origString)
	{
		String from, to;
		if (!(fromString instanceof String))
		{
			from = fromString.toString();
		}
		else
		{
			from = (String) fromString;
		}
		if (!(toString instanceof String))
		{
			to = toString.toString();
		}
		else
		{
			to = (String) toString;
		}
		addReplaceAllConstraint(returnedString, from, to, origString, StringOpcodes.STR_REPLACE);
	}
	
	public static void addSubstringConstraint(String returnedString, String origString, Expression taint1, int val1, Expression taint2, int val2)
	{
		Expression origTaint = taint1;
		if (origString != null)
			origTaint = (Expression) ((TaintedWithObjTag) ((Object)origString)).getPHOSPHOR_TAG();
		if (origTaint == null && taint1 == null && taint2 == null)
			return;
		StringExpression thirdVal = null;
		if(!JPFInited) initJPF();
		int ret = 0;
		Expression lVal, rVal;
			if (origTaint == null) {
				thirdVal = new StringConstant(origString);
			} else
				thirdVal = (StringExpression) origTaint;

			lVal = (taint1 != null) ? taint1 : null; 
			rVal = (taint2 != null) ? taint2 : null;
	Expression newExp;
		if (taint1 == null)
			if (taint2 == null)
				newExp = ((StringExpression) thirdVal)._subString(val2, val1);
			else
				newExp = ((StringExpression) thirdVal)._subString((IntegerExpression) rVal, val1);
		else if (taint2 == null)
			newExp = ((StringExpression) thirdVal)._subString(val2, (IntegerExpression) lVal);
		else
			newExp = ((StringExpression) thirdVal)._subString((IntegerExpression) rVal, (IntegerExpression) lVal);

		((TaintedWithObjTag) ((Object)returnedString)).setPHOSPHOR_TAG(newExp);

		if (newExp == null) {
			throw new IllegalArgumentException("Returning null expression!");
		}
	}

	public static void addSubstringConstraint(String returnedString, String origString, Expression lVal, int val) {

		Expression origExp = (Expression) origString.getPHOSPHOR_TAG();
		if (origExp == null && lVal == null)
			return;
		if (!JPFInited)
			initJPF();
		if (origExp == null)
			origExp = new StringConstant(origString);
		Expression newExp;
		if (lVal == null)
			newExp = ((StringExpression) origExp)._subString(val);
		else
			newExp = ((StringExpression) origExp)._subString((IntegerExpression) lVal);

		returnedString.setPHOSPHOR_TAG(newExp);
		if (newExp == null) {
			throw new IllegalArgumentException("Returning null expression!");
		}
	}

	public static void addSplitConstraint(String[] returnedStrings, String origString, String splitString)
	{
		throw new UnsupportedOperationException();
//		if(origString.getPHOSPHORTAG() == 0 && splitString.getPHOSPHORTAG() == 0)
//			return;
//		
//		int origTaint = origString.getPHOSPHORTAG();
//		if (origString.getPHOSPHORTAG() == 0) {
//			synchronized (lock) {
//				int ret = nextTaint();
//				origTaint = ret;
//				ConstrainedValue v = new ConstrainedValue();
//				v.taintID = ret;
//				v.expression = new StringConstant(origString);
//				values.add(v);
//			}
//		}
//		int splitTaint = splitString.getPHOSPHORTAG();
//
//		if (splitString.getPHOSPHORTAG() == 0) {
//			synchronized (lock) {
//				int ret = nextTaint();
//				splitTaint = ret;
//				ConstrainedValue v = new ConstrainedValue();
//				v.taintID = ret;
//				v.expression = new StringConstant(splitString);
//				values.add(v);
//			}
//
//		}
//		
//		for (int i = 0; i < returnedStrings.length; i++)
//		{
//			int newTaint = registerBinaryOpWithAffix(origTaint, splitTaint, i, StringOpcodes.STR_SPLIT);
//			returnedStrings[i].setINVIVO_PC_TAINT(newTaint);
//		}	
	}
	
	public static Taint<Expression> toReal(Taint<Expression> t)
	{
		if(t == null)
			return null;
		if(t.lbl instanceof IntegerConstant)
		{
			return new Taint<Expression>(new RealConstant(((IntegerConstant)t.lbl).value));	
		}
		else if(t.lbl instanceof SymbolicInteger)
		{
			throw new IllegalArgumentException("Got: " + t.lbl);
		}
		else
			throw new IllegalArgumentException("Got: " + t.lbl);
	}
}
