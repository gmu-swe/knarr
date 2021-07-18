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
import za.ac.sun.cs.green.expr.*;
import za.ac.sun.cs.green.expr.Operation.Operator;

public class TaintListener extends DerivedTaintListener {

	public static IdentityHashMap<Object, LinkedList<ArrayVariable>> arrayNames = new IdentityHashMap<>();

	// Do not tell the solver about the initial contents of concrete arrays larger than this
	public static int IGNORE_CONCRETE_ARRAY_INITIAL_CONTENTS = 1000;
	// Do not track constraints on arrays larger than this and ...
	public static int IGNORE_LARGE_ARRAY_SIZE = 20000;
	// ... on indexes larger than this
	public static int IGNORE_LARGE_ARRAY_INDEX = 500;

	public static ConcurrentLinkedQueue<Taint[]> symbolizedArrays = new ConcurrentLinkedQueue<>();

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
				Operation select = new BinaryOperation(Operator.SELECT, arrVar, new BVConstant(i, 32));
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

			Operation store = new NaryOperation(Operator.STORE, oldVar, idx, val);
			PathUtils.getCurPC()._addDet(Operator.EQ, store, newVar);
			return newVar;
		}
	}

	//To make JITing better, put the fast path for ignoring a read or write into a separate method
	private boolean ignoredRead(LazyArrayObjTags b, Taint idxTaint, int idx){
		boolean taintedArray = (b.taints != null && b.taints[idx] != null);
		boolean taintedIndex = (idxTaint != null);
		if(!taintedArray && !taintedIndex)
			return true;
		return (b.getLength() > IGNORE_LARGE_ARRAY_SIZE && idx > IGNORE_LARGE_ARRAY_INDEX);
	}

	private boolean ignoredWrite(LazyArrayObjTags b, Taint idxTaint, int idx, Taint t) {
		boolean taintedArray = (b.taints != null && b.taints[idx] != null);
		boolean taintedIndex = (idxTaint != null);
		boolean taintedVal = (t != null);
		if (!taintedArray && !taintedIndex && !taintedVal) {
			// Everything concrete
			return true;
		}
		if (b.getLength() > IGNORE_LARGE_ARRAY_SIZE && idx > IGNORE_LARGE_ARRAY_INDEX)
			return true;
		return false;
	}

	private <B extends LazyArrayObjTags> Taint genericReadArray(B b, Taint idxTaint, int idx, Constant c) {

		boolean taintedArray = (b.taints != null && b.taints[idx] != null);
		boolean taintedIndex = (idxTaint != null);

        if (b.getLength() > IGNORE_LARGE_ARRAY_SIZE && idx > IGNORE_LARGE_ARRAY_INDEX)
			return null;

		if (taintedIndex && !taintedArray) {
			// Symbolic read of concrete array pos

			// Make array position symbolic because it may be written in the future
			if (b.taints == null)
				b.taints = new Taint[b.getLength()];

			Expression var = getArrayVar(b.getVal());
			Operation select = new BinaryOperation(Operator.SELECT, var, (Expression) idxTaint.getSingleLabel());
			Taint ret = new ExpressionTaint(select);
			b.taints[idx] = ret;

			PathUtils.getCurPC()._addDet(Operator.EQ, c, select);

			// Index is within the array bounds
			{
				Expression exp = new BinaryOperation(Operator.LT, (Expression) idxTaint.getSingleLabel(), new BVConstant(b.getLength(), 32));
				PathUtils.getCurPC()._addDet(Operator.LT, (Expression) idxTaint.getSingleLabel(), new BVConstant(b.getLength(), 32));
				PathUtils.getCurPC()._addDet(Operator.GE, (Expression) idxTaint.getSingleLabel(), PathUtils.BV0_32);
			}

			symbolizedArrays.add(b.taints);

            // Return symbolic array read
            return ret;
		} else if (taintedArray && !taintedIndex) {
			// Concrete read of symbolic array pos
            // Return array symb
            return b.taints[idx];
		} else if (taintedArray && taintedIndex) {
		    // Symbolic read of symbolic array pos
			// Return array symb OR return symbolic array read
			Expression var = getArrayVar(b.getVal());
			Operation select = new BinaryOperation(Operator.SELECT, var, (Expression) idxTaint.getSingleLabel());
			PathUtils.getCurPC()._addDet(Operator.EQ, (Expression) b.taints[idx].getSingleLabel(), select);

			// Index is within the array bounds
			PathUtils.getCurPC()._addDet(Operator.LT, (Expression)idxTaint.getSingleLabel(), new BVConstant(b.getLength(), 32));
			PathUtils.getCurPC()._addDet(Operator.GE, (Expression)idxTaint.getSingleLabel(), PathUtils.BV0_32);

			return b.taints[idx];
		} else if (!taintedArray && !taintedIndex) {
			// Concrete read of concrete array pos
		    return null;
		}

//		if(idxTaint != null)
//		{
//			Expression var = getArrayVar(b.getVal());
//			BVConstant idxBV = new BVConstant(idx, 32);
//
//			Operation select = new BinaryOperation(Operator.SELECT, var, (Expression) idxTaint.getSingleLabel());
//			PathUtils.getCurPC()._addDet(Operator.EQ, c, select);
//
//			// Index is within the array bounds
//			PathUtils.getCurPC()._addDet(Operator.LT, (Expression)idxTaint.getSingleLabel(), new BVConstant(b.getLength(), 32));
//			PathUtils.getCurPC()._addDet(Operator.GE, (Expression)idxTaint.getSingleLabel(), new BVConstant(0, 32));
//			Taint ret = new ExpressionTaint(select);
//
//			if (b.taints == null)
//				b.taints = new Taint[b.getLength()];
//
////			b.taints[idx] = ret;
//			b.taints[idx] = new ExpressionTaint(c);
//
//			return ret;
//		}
//		else if(b.taints != null && b.taints[idx] != null)
//		{
//			return b.taints[idx];
//		}
//
//		return null;

		throw new Error("Dead code");
	}

	private <B extends LazyArrayObjTags> Taint genericWriteArray(B b, Taint t, Taint idxTaint, int idx, Constant c) {

		boolean taintedArray = (b.taints != null && b.taints[idx] != null);
		boolean taintedIndex = (idxTaint != null);
		boolean taintedVal   = (t != null);

		if (b.getLength() > IGNORE_LARGE_ARRAY_SIZE && idx > IGNORE_LARGE_ARRAY_INDEX)
			return null;

		if (!taintedArray && !taintedIndex && !taintedVal) {
			// Everything concrete
			return null;
		} else if(taintedArray && !taintedIndex &&!taintedVal) {
		    // Symbolic position overwritten by concrete value

			// New value of the array
			Expression newArray = setArrayVar(b.getVal(), new BVConstant(idx, 32), c);

//			return new ExpressionTaint(c);
			return null;
        } else if(!taintedArray && taintedIndex &&!taintedVal) {
		    // Symbolic index on concrete values

			// New value of the array
			Expression newArray = setArrayVar(b.getVal(), (Expression)idxTaint.getSingleLabel(), c);

			// Index is within the array bounds
			PathUtils.getCurPC()._addDet(Operator.LT, (Expression)idxTaint.getSingleLabel(), new BVConstant(b.getLength(), 32));
			PathUtils.getCurPC()._addDet(Operator.GE, (Expression)idxTaint.getSingleLabel(), new BVConstant(0, 32));

			// Array position becomes new value
//			return new ExpressionTaint(c);
			return null;
        } else if(!taintedArray && !taintedIndex && taintedVal) {
			// Concrete index on concrete array and symb new value
			// Array position becomes symbolic new value
			return t;
        } else if(!taintedArray && taintedIndex && taintedVal) {
		    // Concrete array pos with symb index and val
			// Make array pos symbolic with new val

			// Index is within the array bounds
			PathUtils.getCurPC()._addDet(Operator.LT, (Expression)idxTaint.getSingleLabel(), new BVConstant(b.getLength(), 32));
			PathUtils.getCurPC()._addDet(Operator.GE, (Expression)idxTaint.getSingleLabel(), new BVConstant(0, 32));

			return t;
		} else if(taintedArray && !taintedIndex && taintedVal) {
			// Symb array pos and symb value with concrete index

			// New array with the change
			Expression newArray = setArrayVar(b.getVal(), new BVConstant(idx, 32), (Expression) t.getSingleLabel());

			// Array position becomes symbolic new value
			return t;
		} else if(taintedArray && taintedIndex && !taintedVal) {
		    // Symbolic array pos being overwritten by concrete value with symbolic index

			// New array with the change
			Expression newArray = setArrayVar(b.getVal(), (Expression)idxTaint.getSingleLabel(), c);

			// Index is within the array bounds
			PathUtils.getCurPC()._addDet(Operator.LT, (Expression)idxTaint.getSingleLabel(), new BVConstant(b.getLength(), 32));
			PathUtils.getCurPC()._addDet(Operator.GE, (Expression)idxTaint.getSingleLabel(), new BVConstant(0, 32));

//			return new ExpressionTaint(c);
			return null;
		} else if(taintedArray && taintedIndex && taintedVal) {
		    // Symbolic array position overwritten by another symbolic value with a symbolic index

			// New array with the change
			Expression newArray = setArrayVar(b.getVal(), (Expression)idxTaint.getSingleLabel(), (Expression) t.getSingleLabel());

			// Index is within the array bounds
			PathUtils.getCurPC()._addDet(Operator.LT, (Expression)idxTaint.getSingleLabel(), new BVConstant(b.getLength(), 32));
			PathUtils.getCurPC()._addDet(Operator.GE, (Expression)idxTaint.getSingleLabel(), new BVConstant(0, 32));

			// Array position becomes symbolic new value
			return t;
		}
//		else if(taintedIndex)
//		{
//			Expression newArray = setArrayVar(b.getVal(), (Expression)idxTaint.getSingleLabel(), taintedVal ? (Expression) t.getSingleLabel() : c);
//
//			// Index is within the array bounds
//			PathUtils.getCurPC()._addDet(Operator.LT, (Expression)idxTaint.getSingleLabel(), new BVConstant(b.getLength(), 32));
//			PathUtils.getCurPC()._addDet(Operator.GE, (Expression)idxTaint.getSingleLabel(), new BVConstant(0, 32));
//
////			return new ExpressionTaint(new BinaryOperation(Operator.SELECT, newArray, new BVConstant(idx, 32)));
//			return new ExpressionTaint(c);
//		}
//		else if (taintedVal)
//		{
//			return t;
//		}

		throw new Error("Dead code");
	}

	@Override
	public TaintedBooleanWithObjTag arrayGet(LazyBooleanArrayObjTags b, Taint idxTaint, int idx, TaintedBooleanWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		if(ignoredRead(b, idxTaint, idx))
		    ret.taint = null;
		else
			ret.taint = genericReadArray(b, idxTaint, idx, new BoolConstant(ret.val));
//		if (idxTaint != null && idxTaint.toString().matches(PathUtils.interesting))
//			System.out.print("");
		return ret;
	}

	@Override
	public TaintedByteWithObjTag arrayGet(LazyByteArrayObjTags b, Taint idxTaint, int idx, TaintedByteWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		if(ignoredRead(b, idxTaint, idx))
			ret.taint = null;
		else
			ret.taint = genericReadArray(b, idxTaint, idx, new BVConstant(ret.val, 32));
//		if (idxTaint != null && idxTaint.toString().matches(PathUtils.interesting))
//			System.out.print("");
		return ret;
	}

	@Override
	public TaintedCharWithObjTag arrayGet(LazyCharArrayObjTags b, Taint idxTaint, int idx, TaintedCharWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		if(ignoredRead(b, idxTaint, idx))
			ret.taint = null;
		else
			ret.taint = genericReadArray(b, idxTaint, idx, new BVConstant(ret.val, 32));
//		if (idxTaint != null && idxTaint.toString().matches(PathUtils.interesting))
//			System.out.print("");
		return ret;
	}

	@Override
	public TaintedShortWithObjTag arrayGet(LazyShortArrayObjTags b, Taint idxTaint, int idx, TaintedShortWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		if(ignoredRead(b, idxTaint, idx))
			ret.taint = null;
		else
			ret.taint = genericReadArray(b, idxTaint, idx, new BVConstant(ret.val, 32));
//		if (idxTaint != null && idxTaint.toString().matches(PathUtils.interesting))
//			System.out.print("");
		return ret;
	}

	@Override
	public TaintedIntWithObjTag arrayGet(LazyIntArrayObjTags b, Taint idxTaint, int idx, TaintedIntWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		if(ignoredRead(b, idxTaint, idx))
			ret.taint = null;
		else
			ret.taint = genericReadArray(b, idxTaint, idx, new BVConstant(ret.val, 32));
//		if (idxTaint != null && idxTaint.toString().matches(PathUtils.interesting))
//			System.out.print("");
		return ret;
	}

	@Override
	public TaintedLongWithObjTag arrayGet(LazyLongArrayObjTags b, Taint idxTaint, int idx, TaintedLongWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		if(ignoredRead(b, idxTaint, idx))
			ret.taint = null;
		else
			ret.taint = genericReadArray(b, idxTaint, idx, new BVConstant(ret.val, 64));
//		if (idxTaint != null && idxTaint.toString().matches(PathUtils.interesting))
//			System.out.print("");
		return ret;
	}

	@Override
	public TaintedFloatWithObjTag arrayGet(LazyFloatArrayObjTags b, Taint idxTaint, int idx, TaintedFloatWithObjTag ret, ControlTaintTagStack ctrl) {
		// Read array
		ret.val = b.val[idx];
		if(ignoredRead(b, idxTaint, idx))
			ret.taint = null;
		else
			ret.taint = genericReadArray(b, idxTaint, idx, new RealConstant(ret.val));

		return ret;
	}

	@Override
	public TaintedDoubleWithObjTag arrayGet(LazyDoubleArrayObjTags b, Taint idxTaint, int idx, TaintedDoubleWithObjTag ret, ControlTaintTagStack ctrl) {
		// Read array
		ret.val = b.val[idx];

		// Adjust taint
		if(ignoredRead(b, idxTaint, idx))
			ret.taint = null;
		else
			ret.taint = genericReadArray(b, idxTaint, idx, new RealConstant(ret.val));

		return ret;
	}

	@Override
	public Taint arraySet(LazyShortArrayObjTags a, Taint idxTaint, int idx, Taint t, short v, ControlTaintTagStack ctrl) {
	    if(ignoredWrite(a, idxTaint, idx, t))
	    	return null;
		return genericWriteArray(a, t, idxTaint, idx, new BVConstant(v, 32));
	}

	@Override
	public Taint arraySet(LazyIntArrayObjTags a, Taint idxTaint, int idx, Taint t, int v, ControlTaintTagStack ctrl) {
		if(ignoredWrite(a, idxTaint, idx, t))
			return null;
		return genericWriteArray(a, t, idxTaint, idx, new BVConstant(v, 32));
	}

	@Override
	public Taint arraySet(LazyByteArrayObjTags a, Taint idxTaint, int idx, Taint t, byte v, ControlTaintTagStack ctrl) {
		if(ignoredWrite(a, idxTaint, idx, t))
			return null;
		return genericWriteArray(a, t, idxTaint, idx, new BVConstant(v, 32));
	}

	@Override
	public Taint arraySet(LazyBooleanArrayObjTags a, Taint idxTaint, int idx, Taint t, boolean v, ControlTaintTagStack ctrl) {
		if(ignoredWrite(a, idxTaint, idx, t))
			return null;
		return genericWriteArray(a, t, idxTaint, idx, new BoolConstant(v));
	}

	@Override
	public Taint arraySet(LazyCharArrayObjTags a, Taint idxTaint, int idx, Taint t, char v, ControlTaintTagStack ctrl) {
		if(ignoredWrite(a, idxTaint, idx, t))
			return null;
		return genericWriteArray(a, t, idxTaint, idx, new BVConstant(v, 32));
	}

	@Override
	public Taint arraySet(LazyFloatArrayObjTags a, Taint idxTaint, int idx, Taint t, float v, ControlTaintTagStack ctrl) {
		if(ignoredWrite(a, idxTaint, idx, t))
			return null;
		return genericWriteArray(a, t, idxTaint, idx, new RealConstant(v));
	}

	@Override
	public Taint arraySet(LazyDoubleArrayObjTags a, Taint idxTaint, int idx, Taint t, double v, ControlTaintTagStack ctrl) {
		if(ignoredWrite(a, idxTaint, idx, t))
			return null;
		return genericWriteArray(a, t, idxTaint, idx, new RealConstant(v));
	}

	@Override
	public Taint arraySet(LazyLongArrayObjTags a, Taint idxTaint, int idx, Taint t, long v, ControlTaintTagStack ctrl) {
		if(ignoredWrite(a, idxTaint, idx, t))
			return null;
		return genericWriteArray(a, t, idxTaint, idx, new BVConstant(v, 64));
	}
}
