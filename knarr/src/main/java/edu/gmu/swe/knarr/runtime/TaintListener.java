package edu.gmu.swe.knarr.runtime;

import java.lang.reflect.Array;
import java.util.IdentityHashMap;

import edu.columbia.cs.psl.phosphor.runtime.DerivedTaintListener;
import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.struct.*;
import za.ac.sun.cs.green.expr.ArrayVariable;
import za.ac.sun.cs.green.expr.BVConstant;
import za.ac.sun.cs.green.expr.BoolConstant;
import za.ac.sun.cs.green.expr.Constant;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;

public class TaintListener extends DerivedTaintListener {

	public static IdentityHashMap<Object, Expression> arrayNames = new IdentityHashMap<>();

	private Expression getArrayVar(Object arr)
	{
		synchronized (arrayNames)
		{
			Expression ret = arrayNames.get(arr);
			if (ret == null)
			{
				Class<?> t = arr.getClass().getComponentType().isPrimitive() ? arr.getClass().getComponentType() : Object.class;
				ArrayVariable var = new ArrayVariable("const_array_" + arrayNames.size(), t);
				arrayNames.put(arr, var);
				ret = var;

				Expression init = var;

				for (int i = 0 ; i < Array.getLength(arr) ; i++)
				{
					BVConstant idx = new BVConstant(i, 32);
					Expression val;
					switch(arr.getClass().getComponentType().getName())
					{
						case "boolean":
							val = new BoolConstant(((boolean[])arr)[i]);
							break;
						case "byte":
							val = new BVConstant(((byte[])arr)[i], 8);
							break;
						case "char":
							val = new BVConstant(((char[])arr)[i], 16);
							break;
						case "short":
							val = new BVConstant(((short[])arr)[i], 16);
							break;
						case "int":
							val = new BVConstant(((int[])arr)[i], 32);
							break;
						case "long":
							val = new BVConstant(((long[])arr)[i], 64);
							break;
						default:
							throw new Error("Not supported");
					}
					init = new Operation(Operator.STORE, init, idx, val);
				}
			}
			return ret;
		}
	}
	
	private <B extends LazyArrayObjTags> Taint genericReadArray(B b, Taint idxTaint, int idx, Constant c) {
		if(idxTaint != null && (b.taints != null && b.taints[idx] != null))
		{
			throw new Error("Not implemented symbolic index on symbolic array");
		}
		else if(b.taints != null && b.taints[idx] != null)
		{
			return b.taints[idx];
		}
		else if(idxTaint != null)
		{
			Expression var = getArrayVar(b.getVal());
			BVConstant idxBV = new BVConstant(idx, 32);
			
			Operation select = new Operation(Operator.SELECT, var, (Expression) idxTaint.lbl);
			PathUtils.getCurPC()._addDet(Operator.EQ, c, select);

			// Index is within the array bounds
			PathUtils.getCurPC()._addDet(Operator.LT, (Expression)idxTaint.lbl, new BVConstant(b.getLength(), 32));
			PathUtils.getCurPC()._addDet(Operator.GE, (Expression)idxTaint.lbl, new BVConstant(0, 32));
			return new ExpressionTaint(select);
		}
		
		return null;
	}
	
	@Override
	public TaintedBooleanWithObjTag arrayGet(LazyBooleanArrayObjTags b, Taint idxTaint, int idx, TaintedBooleanWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		ret.taint = genericReadArray(b, idxTaint, idx, new BoolConstant(ret.val));
		return ret;
	}
	
	@Override
	public TaintedByteWithObjTag arrayGet(LazyByteArrayObjTags b, Taint idxTaint, int idx, TaintedByteWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		ret.taint = genericReadArray(b, idxTaint, idx, new BVConstant(ret.val, 8));
		return ret;
	}
	
	@Override
	public TaintedCharWithObjTag arrayGet(LazyCharArrayObjTags b, Taint idxTaint, int idx, TaintedCharWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		ret.taint = genericReadArray(b, idxTaint, idx, new BVConstant(ret.val, 16));
		return ret;
	}
	
	@Override
	public TaintedShortWithObjTag arrayGet(LazyShortArrayObjTags b, Taint idxTaint, int idx, TaintedShortWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		ret.taint = genericReadArray(b, idxTaint, idx, new BVConstant(ret.val, 16));
		return ret;
	}
	
	@Override
	public TaintedIntWithObjTag arrayGet(LazyIntArrayObjTags b, Taint idxTaint, int idx, TaintedIntWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		ret.taint = genericReadArray(b, idxTaint, idx, new BVConstant(ret.val, 32));
		return ret;
	}
	
	@Override
	public TaintedLongWithObjTag arrayGet(LazyLongArrayObjTags b, Taint idxTaint, int idx, TaintedLongWithObjTag ret, ControlTaintTagStack ctrl) {
		ret.val = b.val[idx];
		ret.taint = genericReadArray(b, idxTaint, idx, new BVConstant(ret.val, 64));
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
		if(idxTaint != null || b.taints != null) {
			throw new Error("Not implemented");
		}

		return ret;
	}

	@Override
	public Taint arraySet(LazyShortArrayObjTags a, Taint idxTaint, int idx, Taint t, short v, ControlTaintTagStack ctrl) {
		return super.arraySet(a, null, idx, t, v, ctrl);
	}

	@Override
	public Taint arraySet(LazyIntArrayObjTags a, Taint idxTaint, int idx, Taint t, int v, ControlTaintTagStack ctrl) {
		return super.arraySet(a, null, idx, t, v, ctrl);
	}

	@Override
	public Taint arraySet(LazyByteArrayObjTags a, Taint idxTaint, int idx, Taint t, byte v, ControlTaintTagStack ctrl) {
		return super.arraySet(a, null, idx, t, v, ctrl);
	}

	@Override
	public Taint arraySet(LazyBooleanArrayObjTags a, Taint idxTaint, int idx, Taint t, boolean v, ControlTaintTagStack ctrl) {
		return super.arraySet(a, null, idx, t, v, ctrl);
	}

	@Override
	public Taint arraySet(LazyCharArrayObjTags a, Taint idxTaint, int idx, Taint t, char v, ControlTaintTagStack ctrl) {
		return super.arraySet(a, null, idx, t, v, ctrl);
	}

	@Override
	public Taint arraySet(LazyFloatArrayObjTags a, Taint idxTaint, int idx, Taint t, float v, ControlTaintTagStack ctrl) {
		return super.arraySet(a, null, idx, t, v, ctrl);
	}

	@Override
	public Taint arraySet(LazyDoubleArrayObjTags a, Taint idxTaint, int idx, Taint t, double v, ControlTaintTagStack ctrl) {
		return super.arraySet(a, null, idx, t, v, ctrl);
	}

	@Override
	public Taint arraySet(LazyLongArrayObjTags a, Taint idxTaint, int idx, Taint t, long v, ControlTaintTagStack ctrl) {
		return super.arraySet(a, null, idx, t, v, ctrl);
	}
}
