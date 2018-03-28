package edu.gmu.swe.knarr.runtime;

import edu.columbia.cs.psl.phosphor.runtime.DerivedTaintListener;
import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.struct.*;
import za.ac.sun.cs.green.expr.Expression;

public class TaintListener extends DerivedTaintListener {
	@Override
	public Taint arrayGet(LazyArrayObjTags b, Taint idxTaint, int idx, TaintedPrimitiveWithObjTag ret) {
		if(idxTaint != null){
			System.out.println("Using symbolic array index: " + idxTaint + " the taint of the value: " + ret.taint);
		}
		return ret.taint;
	}

	@Override
	public Taint arraySet(LazyShortArrayObjTags a, Taint idxTaint, int idx, Taint t, short v) {
		return super.arraySet(a, idxTaint, idx, t, v);
	}

	@Override
	public Taint arraySet(LazyIntArrayObjTags a, Taint idxTaint, int idx, Taint t, int v) {
		return super.arraySet(a, idxTaint, idx, t, v);
	}

	@Override
	public Taint arraySet(LazyByteArrayObjTags a, Taint idxTaint, int idx, Taint t, byte v) {
		return super.arraySet(a, idxTaint, idx, t, v);
	}

	@Override
	public Taint arraySet(LazyBooleanArrayObjTags a, Taint idxTaint, int idx, Taint t, boolean v) {
		return super.arraySet(a, idxTaint, idx, t, v);
	}

	@Override
	public Taint arraySet(LazyCharArrayObjTags a, Taint idxTaint, int idx, Taint t, char v) {
		return super.arraySet(a, idxTaint, idx, t, v);
	}

	@Override
	public Taint arraySet(LazyFloatArrayObjTags a, Taint idxTaint, int idx, Taint t, float v) {
		return super.arraySet(a, idxTaint, idx, t, v);
	}

	@Override
	public Taint arraySet(LazyDoubleArrayObjTags a, Taint idxTaint, int idx, Taint t, double v) {
		return super.arraySet(a, idxTaint, idx, t, v);
	}

	@Override
	public Taint arraySet(LazyLongArrayObjTags a, Taint idxTaint, int idx, Taint t, long v) {
		return super.arraySet(a, idxTaint, idx, t, v);
	}
}
