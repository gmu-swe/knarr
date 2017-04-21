package edu.gmu.swe.knarr.runtime;

import java.io.Serializable;
import java.util.LinkedList;

import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;

public class PathConditionWrapper implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8116156330739369246L;
	public LinkedList<Operation> constraints = new LinkedList<Operation>();

	public PathConditionWrapper() {
	}

	public void _addDet(Operator op, Expression l, Expression r) {
		Operation ret = new Operation(op, l, r);
		constraints.add(ret);
	}

	public void _addDet(Operator op, Expression t1) {
		Operation ret = new Operation(op, t1);
		constraints.add(ret);
	}

}
