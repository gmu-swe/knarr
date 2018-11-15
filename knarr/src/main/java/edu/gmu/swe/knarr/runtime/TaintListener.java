package edu.gmu.swe.knarr.runtime;

import java.lang.reflect.Array;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

import edu.columbia.cs.psl.phosphor.runtime.DerivedTaintListener;
import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.struct.ControlTaintTagStack;
import edu.columbia.cs.psl.phosphor.struct.LazyArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.LazyBooleanArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.LazyByteArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.LazyCharArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.LazyDoubleArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.LazyFloatArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.LazyIntArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.LazyLongArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.LazyShortArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.TaintedBooleanWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedByteWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedCharWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedDoubleWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedFloatWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedIntWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedLongWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedShortWithObjTag;
import za.ac.sun.cs.green.expr.ArrayVariable;
import za.ac.sun.cs.green.expr.BVConstant;
import za.ac.sun.cs.green.expr.BoolConstant;
import za.ac.sun.cs.green.expr.Constant;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.RealConstant;

public class TaintListener extends DerivedTaintListener {

	public static IdentityHashMap<Object, LinkedList<ArrayVariable>> arrayNames = new IdentityHashMap<>();

	// Do not tell the solver about the initial contents of concrete arrays larger than this
	public static int IGNORE_CONCRETE_ARRAY_INITIAL_CONTENTS = 1000;
	// Do not track constraints on arrays larger than this and ...
	public static int IGNORE_LARGE_ARRAY_SIZE = 20000;
	// ... on indexes larger than this
	public static int IGNORE_LARGE_ARRAY_INDEX = 500;

	public static ConcurrentLinkedQueue<LazyArrayObjTags> symbolizedArrays = new ConcurrentLinkedQueue<>();

	private LinkedList<ArrayVariable> getOrInitArray(Object arr) {
		LinkedList<ArrayVariable> ret = arrayNames.get(arr);
		if (ret != null)
			return ret;

		Class<?> t = arr.getClass().getComponentType().isPrimitive() ? arr.getClass().getComponentType() : Object.class;
		LinkedList<ArrayVariable> ll = new LinkedList<>();
		ArrayVariable var = new ArrayVariable("const_array_" + arrayNames.size(), t);
		ll.add(var);
		arrayNames.put(arr, ll);
		ret = ll;

		if (Array.getLength(arr) < IGNORE_CONCRETE_ARRAY_INITIAL_CONTENTS) {
			ArrayVariable arrVar = new ArrayVariable(var.getName() + "_" + ret.size(), var.getType());

			for (int i = 0 ; i < Array.getLength(arr) ; i++)
			{
				Operation select = new Operation(Operator.SELECT, arrVar, new BVConstant(i, 32));
				Constant val;
				switch(arr.getClass().getComponentType().getName()) {
					case "boolean":
						val = new BoolConstant(((boolean[])arr)[i]);
						break;
					case "byte":
						val = new BVConstant(((byte[])arr)[i], 32);
						break;
					case "char":
						val = new BVConstant(((char[])arr)[i], 32);
						break;
					case "short":
						val = new BVConstant(((short[])arr)[i], 32);
						break;
					case "int":
						val = new BVConstant(((int[])arr)[i], 32);
						break;
					case "long":
						val = new BVConstant(((long[])arr)[i], 64);
						break;
					case "float":
						val = new RealConstant(((float[])arr)[i]);
						break;
					case "double":
						val = new RealConstant(((double[])arr)[i]);
						break;
					default:
						throw new Error("Not supported");
				}
				PathUtils.getCurPC()._addDet(Operator.EQ, select, val);
			}
		}

		return ret;

	}

	private Expression getArrayVar(Object arr)
	{
		synchronized (arrayNames)
		{
			LinkedList<ArrayVariable> ret = getOrInitArray(arr);
			return new ArrayVariable(ret.getLast().getName() + "_" + ret.size(), ret.getLast().getType());
		}
	}
	
	private Expression setArrayVar(Object arr, Expression idx, Expression val)
	{
		synchronized (arrayNames)
		{
			LinkedList<ArrayVariable> ret = getOrInitArray(arr);

			Class<?> t = arr.getClass().getComponentType().isPrimitive() ? arr.getClass().getComponentType() : Object.class;
			ArrayVariable var = ret.getLast();

			ArrayVariable oldVar = new ArrayVariable(var.getName() + "_" + ret.size(), var.getType());
			ArrayVariable newVar = new ArrayVariable(var.getName() + "_" + (ret.size() + 1), var.getType());
			ret.addLast(var);

			Operation store = new Operation(Operator.STORE, oldVar, idx, val);
			PathUtils.getCurPC()._addDet(Operator.EQ, store, newVar);
			return ret.getLast();
		}
	}
	
	
	private <B extends LazyArrayObjTags> Taint genericReadArray(B b, Taint idxTaint, int idx, Constant c) {

		boolean taintedArray = (b.taints != null && b.taints[idx] != null);
		boolean taintedIndex = (idxTaint != null);

        if (b.getLength() > IGNORE_LARGE_ARRAY_SIZE && idx > IGNORE_LARGE_ARRAY_INDEX)
			return null;

//		if (taintedIndex && !taintedArray) {
//			// Symbolic read of concrete array pos
//
//			// Make array position symbolic because it may be written in the future
//			if (b.taints == null)
//				b.taints = new Taint[b.getLength()];
//
//			Expression var = getArrayVar(b.getVal());
//			Operation select = new Operation(Operator.SELECT, var, (Expression) idxTaint.lbl);
//			Taint ret = new ExpressionTaint(select);
//			b.taints[idx] = ret;
//
//			PathUtils.getCurPC()._addDet(Operator.EQ, c, select);
//
//			// Index is within the array bounds
//			PathUtils.getCurPC()._addDet(Operator.LT, (Expression)idxTaint.lbl, new BVConstant(b.getLength(), 32));
//			PathUtils.getCurPC()._addDet(Operator.GE, (Expression)idxTaint.lbl, new BVConstant(0, 32));
//
//			symbolizedArrays.add(b);
//
//            // Return symbolic array read
//            return ret;
//		} else if (taintedArray && !taintedIndex) {
//			// Concrete read of symbolic array pos
//            // Return array symb
//            return b.taints[idx];
//		} else if (taintedArray && taintedIndex) {
//		    // Symbolic read of symbolic array pos
//			// Return array symb OR return symbolic array read
//			Expression var = getArrayVar(b.getVal());
//			Operation select = new Operation(Operator.SELECT, var, (Expression) idxTaint.lbl);
//			PathUtils.getCurPC()._addDet(Operator.EQ, (Expression) b.taints[idx].lbl, select);
//
//			// Index is within the array bounds
//			PathUtils.getCurPC()._addDet(Operator.LT, (Expression)idxTaint.lbl, new BVConstant(b.getLength(), 32));
//			PathUtils.getCurPC()._addDet(Operator.GE, (Expression)idxTaint.lbl, new BVConstant(0, 32));
//
//			return b.taints[idx];
//		} else if (!taintedArray && !taintedIndex) {
//			// Concrete read of concrete array pos
//		    return null;
//		}

		if(idxTaint != null)
		{
			Expression var = getArrayVar(b.getVal());
			BVConstant idxBV = new BVConstant(idx, 32);

			Operation select = new Operation(Operator.SELECT, var, (Expression) idxTaint.lbl);
			PathUtils.getCurPC()._addDet(Operator.EQ, c, select);

			// Index is within the array bounds
			PathUtils.getCurPC()._addDet(Operator.LT, (Expression)idxTaint.lbl, new BVConstant(b.getLength(), 32));
			PathUtils.getCurPC()._addDet(Operator.GE, (Expression)idxTaint.lbl, new BVConstant(0, 32));
			Taint ret = new ExpressionTaint(select);

			if (b.taints == null)
				b.taints = new Taint[b.getLength()];

//			b.taints[idx] = ret;
			b.taints[idx] = new ExpressionTaint(c);

			return ret;
		}
		else if(b.taints != null && b.taints[idx] != null)
		{
			return b.taints[idx];
		}

		return null;

//		throw new Error("Dead code");
	}
	
	private <B extends LazyArrayObjTags> Taint genericWriteArray(B b, Taint t, Taint idxTaint, int idx, Constant c) {
		
		boolean taintedArray = (b.taints != null && b.taints[idx] != null);
		boolean taintedIndex = (idxTaint != null);
		boolean taintedVal   = (t != null);

		if (b.getLength() > IGNORE_LARGE_ARRAY_SIZE && idx > IGNORE_LARGE_ARRAY_INDEX)
 			return null;
		
		if (!taintedArray && !taintedIndex && !taintedVal)
			return null;
//		else if (taintedVal && taintedIndex)
//			throw new UnsupportedOperationException("Not implemented symbolic index on symbolic array");

		if(taintedArray && !taintedIndex &&!taintedVal)
		{
			setArrayVar(b.getVal(), new BVConstant(idx, 32), c);
			return null;
		}
		else if(taintedIndex)
		{
			setArrayVar(b.getVal(), (Expression)idxTaint.lbl, taintedVal ? (Expression) t.lbl : c);

			// Index is within the array bounds
			PathUtils.getCurPC()._addDet(Operator.LT, (Expression)idxTaint.lbl, new BVConstant(b.getLength(), 32));
			PathUtils.getCurPC()._addDet(Operator.GE, (Expression)idxTaint.lbl, new BVConstant(0, 32));
			
			return null;
		}
		else if (taintedVal)
		{
			return t;
		}

		throw new UnsupportedOperationException("Dead code?");
	}
	
	@Override
	public TaintedBooleanWithObjTag arrayGet(LazyBooleanArrayObjTags b, Taint idxTaint, int idx, TaintedBooleanWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		ret.taint = genericReadArray(b, idxTaint, idx, new BoolConstant(ret.val));
//		if (idxTaint != null && idxTaint.toString().matches(PathUtils.interesting))
//			System.out.print("");
		return ret;
	}
	
	@Override
	public TaintedByteWithObjTag arrayGet(LazyByteArrayObjTags b, Taint idxTaint, int idx, TaintedByteWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		ret.taint = genericReadArray(b, idxTaint, idx, new BVConstant(ret.val, 32));
//		if (idxTaint != null && idxTaint.toString().matches(PathUtils.interesting))
//			System.out.print("");
		return ret;
	}
	
	@Override
	public TaintedCharWithObjTag arrayGet(LazyCharArrayObjTags b, Taint idxTaint, int idx, TaintedCharWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		ret.taint = genericReadArray(b, idxTaint, idx, new BVConstant(ret.val, 32));
//		if (idxTaint != null && idxTaint.toString().matches(PathUtils.interesting))
//			System.out.print("");
		return ret;
	}
	
	@Override
	public TaintedShortWithObjTag arrayGet(LazyShortArrayObjTags b, Taint idxTaint, int idx, TaintedShortWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		ret.taint = genericReadArray(b, idxTaint, idx, new BVConstant(ret.val, 32));
//		if (idxTaint != null && idxTaint.toString().matches(PathUtils.interesting))
//			System.out.print("");
		return ret;
	}
	
	@Override
	public TaintedIntWithObjTag arrayGet(LazyIntArrayObjTags b, Taint idxTaint, int idx, TaintedIntWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		ret.taint = genericReadArray(b, idxTaint, idx, new BVConstant(ret.val, 32));
//		if (idxTaint != null && idxTaint.toString().matches(PathUtils.interesting))
//			System.out.print("");
		return ret;
	}
	
	@Override
	public TaintedLongWithObjTag arrayGet(LazyLongArrayObjTags b, Taint idxTaint, int idx, TaintedLongWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		ret.taint = genericReadArray(b, idxTaint, idx, new BVConstant(ret.val, 64));
//		if (idxTaint != null && idxTaint.toString().matches(PathUtils.interesting))
//			System.out.print("");
		return ret;
	}
	
	@Override
	public TaintedFloatWithObjTag arrayGet(LazyFloatArrayObjTags b, Taint idxTaint, int idx, TaintedFloatWithObjTag ret, ControlTaintTagStack ctrl) {
		// Read array
		ret.val = b.val[idx];
		
		// Adjust taint
		if(idxTaint != null || b.taints != null) {
			throw new Error("Not implemented");
		}

		return ret;
	}
	
	@Override
	public TaintedDoubleWithObjTag arrayGet(LazyDoubleArrayObjTags b, Taint idxTaint, int idx, TaintedDoubleWithObjTag ret, ControlTaintTagStack ctrl) {
		// Read array
		ret.val = b.val[idx];
		
		// Adjust taint
		ret.taint = genericReadArray(b, idxTaint, idx, new RealConstant(ret.val));

		return ret;
	}

	@Override
	public Taint arraySet(LazyShortArrayObjTags a, Taint idxTaint, int idx, Taint t, short v, ControlTaintTagStack ctrl) {
		return genericWriteArray(a, t, idxTaint, idx, new BVConstant(v, 32));
	}

	@Override
	public Taint arraySet(LazyIntArrayObjTags a, Taint idxTaint, int idx, Taint t, int v, ControlTaintTagStack ctrl) {
		return genericWriteArray(a, t, idxTaint, idx, new BVConstant(v, 32));
	}

	@Override
	public Taint arraySet(LazyByteArrayObjTags a, Taint idxTaint, int idx, Taint t, byte v, ControlTaintTagStack ctrl) {
		return genericWriteArray(a, t, idxTaint, idx, new BVConstant(v, 32));
	}

	@Override
	public Taint arraySet(LazyBooleanArrayObjTags a, Taint idxTaint, int idx, Taint t, boolean v, ControlTaintTagStack ctrl) {
		return genericWriteArray(a, t, idxTaint, idx, new BoolConstant(v));
	}

	@Override
	public Taint arraySet(LazyCharArrayObjTags a, Taint idxTaint, int idx, Taint t, char v, ControlTaintTagStack ctrl) {
		return genericWriteArray(a, t, idxTaint, idx, new BVConstant(v, 32));
	}

	@Override
	public Taint arraySet(LazyFloatArrayObjTags a, Taint idxTaint, int idx, Taint t, float v, ControlTaintTagStack ctrl) {
		return genericWriteArray(a, t, idxTaint, idx, new RealConstant(v));
	}

	@Override
	public Taint arraySet(LazyDoubleArrayObjTags a, Taint idxTaint, int idx, Taint t, double v, ControlTaintTagStack ctrl) {
		return genericWriteArray(a, t, idxTaint, idx, new RealConstant(v));
	}

	@Override
	public Taint arraySet(LazyLongArrayObjTags a, Taint idxTaint, int idx, Taint t, long v, ControlTaintTagStack ctrl) {
		return genericWriteArray(a, t, idxTaint, idx, new BVConstant(v, 64));
	}
}
