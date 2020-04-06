package edu.gmu.swe.knarr.runtime;

import java.io.Serializable;
import java.util.LinkedList;

import za.ac.sun.cs.green.expr.BinaryOperation;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.UnaryOperation;

public class PathConditionWrapper implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8116156330739369246L;
	public Expression constraints = null;
	public int size = 0;
	private int max = Integer.parseInt(System.getProperty("MAX_CONSTRAINTS","-1"));

	public PathConditionWrapper() {
	}

	public synchronized void _addDet(Operation op) {
		if (constraints == null)
			constraints = op;
		else {
			if (max < 0 || size < max) {
				size += 1;
				constraints = new BinaryOperation(Operator.AND, constraints, op);
			}
		}
	}

	public synchronized Expression _addDet(Operator op, Expression l, Expression r) {
		Operation ret = new BinaryOperation(op, l, r);
		if (constraints == null)
			constraints = ret;
		else {
			if (max < 0 || size < max) {
				size += 1;
				constraints = new BinaryOperation(Operator.AND, constraints, ret);
			}
		}

		return ret;
	}

	public synchronized Expression _addDet(Operator op, Expression t1) {
		Operation ret = new UnaryOperation(op, t1);
		if (constraints == null)
			constraints = ret;
		else {
			if (max < 0 || size < max) {
			    size += 1;
				constraints = new BinaryOperation(Operator.AND, constraints, ret);
            }
		}

		return ret;
	}

}
