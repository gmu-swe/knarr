package edu.gmu.swe.knarr.server;

import java.util.LinkedList;

import za.ac.sun.cs.green.expr.Constant;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.Variable;
import za.ac.sun.cs.green.expr.Visitor;
import za.ac.sun.cs.green.expr.VisitorException;

public class ConstraintOptionGenerator {

	class OperatorCollector extends Visitor {
		LinkedList<Operation> ops = new LinkedList<Operation>();

		@Override
		public void preVisit(Operation operation) throws VisitorException {
			switch (operation.getOperator()) {
			case EQ:
			case EQUALS:// string not equals too!!
			case NE:
			case LE:
			case GE:
			case GT:
			case LT:
				ops.add(operation);
				break;
			default:
				break;
			}
			super.preVisit(operation);
		}
		public LinkedList<Operation> getOps() {
			return ops;
		}
	}
	

	public LinkedList<Expression> generateOptions(Expression exp) {
		LinkedList<Expression> ret = new LinkedList<Expression>();
		OperatorCollector ctr = new OperatorCollector();
		try {
			exp.accept(ctr);
		} catch (VisitorException e) {
			e.printStackTrace();
		}

		for(Operation o : ctr.getOps())
		{
			ret.add(copyAndFlip(exp,o));
		}
		
		return ret;
	}

	private Expression copyAndFlip(Expression exp, Operation o) {
		if(exp instanceof Constant)
			return exp;
		if(exp instanceof Variable)
			return exp;
		if(exp instanceof Operation)
		{
			Expression[] newOperands = new Expression[((Operation) exp).getOperator().getArity()];
			for(int i = 0; i < newOperands.length; i++)
				newOperands[i] = copyAndFlip(((Operation) exp).getOperand(i), o);
			if(exp != o)
			{
				return new Operation(((Operation) exp).getOperator(), newOperands);
			}
			switch(((Operation) exp).getOperator())
			{
			case EQ:
				return new Operation(Operator.NE, newOperands);
			case EQUALS:// string not equals too!!
				return new Operation(Operator.NE, newOperands);
			case NE:
				return new Operation(Operator.EQ, newOperands);
			case LE:
				return new Operation(Operator.GT, newOperands);
			case GE:
				return new Operation(Operator.LT, newOperands);
			case GT:
				return new Operation(Operator.LE, newOperands);
			case LT:
				return new Operation(Operator.GE, newOperands);
			default:
				break;
			}
		}
		return null;
	}
}
