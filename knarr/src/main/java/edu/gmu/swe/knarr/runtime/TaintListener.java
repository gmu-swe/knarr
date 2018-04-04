package edu.gmu.swe.knarr.runtime;

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
	
	private IdentityHashMap<Object, ArrayVariable> arrayNames = new IdentityHashMap<>();
	
	private ArrayVariable getArrayVar(Object arr)
	{
		synchronized (arrayNames)
		{
			ArrayVariable ret = arrayNames.get(arr);
			if (ret == null)
			{
				Class<?> t = arr.getClass().getComponentType().isPrimitive() ? arr.getClass().getComponentType() : Object.class;
				ret = new ArrayVariable("const_array_" + arrayNames.size(), t);
				arrayNames.put(arr, ret);
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
			ArrayVariable var = getArrayVar(b.getVal());
			BVConstant idxBV = new BVConstant(idx, 32);
			Operation store  = new Operation(Operator.STORE, var, idxBV, c);
			Operation select = new Operation(Operator.SELECT, store, (Expression) idxTaint.lbl);
//			Operation eq     = new Operation(Operator.EQ, (Expression)idxTaint.lbl, idxBV);
			// Luis:  This may be too strong, instead we should give the solver the whole array and then tell it that arr[sym] = val
			PathUtils.getCurPC()._addDet(Operator.EQ, (Expression)idxTaint.lbl, idxBV);
//			PathUtils.getCurPC()._addDet(Operator.EQ, select, c);
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

//	@Override
//	public Taint arraySet(LazyShortArrayObjTags a, Taint idxTaint, int idx, Taint t, short v) {
//		return super.arraySet(a, idxTaint, idx, t, v);
//	}
//
//	@Override
//	public Taint arraySet(LazyIntArrayObjTags a, Taint idxTaint, int idx, Taint t, int v) {
//		return super.arraySet(a, idxTaint, idx, t, v);
//	}
//
//	@Override
//	public Taint arraySet(LazyByteArrayObjTags a, Taint idxTaint, int idx, Taint t, byte v) {
//		return super.arraySet(a, idxTaint, idx, t, v);
//	}
//
//	@Override
//	public Taint arraySet(LazyBooleanArrayObjTags a, Taint idxTaint, int idx, Taint t, boolean v) {
//		return super.arraySet(a, idxTaint, idx, t, v);
//	}
//
//	@Override
//	public Taint arraySet(LazyCharArrayObjTags a, Taint idxTaint, int idx, Taint t, char v) {
//		return super.arraySet(a, idxTaint, idx, t, v);
//	}
//
//	@Override
//	public Taint arraySet(LazyFloatArrayObjTags a, Taint idxTaint, int idx, Taint t, float v) {
//		return super.arraySet(a, idxTaint, idx, t, v);
//	}
//
//	@Override
//	public Taint arraySet(LazyDoubleArrayObjTags a, Taint idxTaint, int idx, Taint t, double v) {
//		return super.arraySet(a, idxTaint, idx, t, v);
//	}
//
//	@Override
//	public Taint arraySet(LazyLongArrayObjTags a, Taint idxTaint, int idx, Taint t, long v) {
//		return super.arraySet(a, idxTaint, idx, t, v);
//	}
}
