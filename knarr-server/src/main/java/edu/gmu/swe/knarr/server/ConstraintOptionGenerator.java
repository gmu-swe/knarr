package edu.gmu.swe.knarr.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.IntNum;
import com.microsoft.z3.SeqExpr;

import za.ac.sun.cs.green.expr.Constant;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.IntVariable;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.StringConstant;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.StringVariable;
import za.ac.sun.cs.green.expr.Variable;
import za.ac.sun.cs.green.expr.Visitor;
import za.ac.sun.cs.green.expr.VisitorException;
import za.ac.sun.cs.green.service.z3.Z3JavaTranslator.Z3GreenBridge;

public class ConstraintOptionGenerator {

	class OperatorCollector extends Visitor {
		ArrayList<Operation> vars = new ArrayList<Operation>();

		public ArrayList<Operation> getOps() {
			return vars;
		}

		@Override
		public void postVisit(Operation operation) throws VisitorException {
			switch (((Operation) operation).getOperator()) {
			case EQ:
			case EQUALS:
			case STARTSWITH:
			case ENDSWITH:
			case CONTAINS:
			case NE:
			case LE:
			case GE:
			case GT:
			case LT:
				vars.add(operation);
			default:
				break;
			}
			super.postVisit(operation);
		}
	}

	Expression createExpr(Expr exp) {

		switch (exp.getFuncDecl().getArity()) {
		case 0:
			switch (exp.getFuncDecl().getDeclKind()) {
			case Z3_OP_ANUM:
				if(exp.isIntNum())
					return new IntConstant(((IntNum)exp).getInt());
				return new IntVariable(exp.getSExpr(), 0, 0);
			case Z3_OP_UNINTERPRETED:
				if(exp.isInt())
					return new IntVariable(exp.getSExpr(), 0, 0);
				else
					return new StringVariable(exp.getSExpr());
			case Z3_OP_INTERNAL:
				return new StringConstant(exp.getSExpr().substring(1, exp.getSExpr().length() - 1));
			default:
				throw new UnsupportedOperationException("Got: " + exp + " " + exp.getFuncDecl().getDeclKind());
			}
		case 1:
			Operator op;
			switch (exp.getFuncDecl().getDeclKind()) {
			case Z3_OP_NOT:
				op = Operator.NOT;
				break;
			case Z3_OP_SEQ_LENGTH:
				op = Operator.LENGTH;
				break;
			default:
				throw new UnsupportedOperationException("Got: " + exp + " " + exp.getFuncDecl().getDeclKind());
			}
			return new Operation(op, createExpr(exp.getArgs()[0]));
		case 2:
			switch (exp.getFuncDecl().getDeclKind()) {
			case Z3_OP_AND:
				op = Operator.AND;
				if (exp.getNumArgs() > 2) {
					Expression ret = new Operation(Operator.AND,createExpr(exp.getArgs()[0]),createExpr(exp.getArgs()[1]));
					for(int i = 2; i < exp.getNumArgs(); i++)
					{
						ret = new Operation(Operator.AND, ret, createExpr(exp.getArgs()[i]));
					}
					return ret;
				}
				if (exp.getNumArgs() == 1) {
					return createExpr(exp.getArgs()[0]);
				}

				break;
			case Z3_OP_GE:
				op = Operator.GE;
				break;
			case Z3_OP_GT:
				op = Operator.GT;
				break;
			case Z3_OP_LE:
				op = Operator.LE;
				break;
			case Z3_OP_LT:
				op = Operator.LT;
				break;
			case Z3_OP_EQ:
				if(exp.getArgs()[0] instanceof SeqExpr)
					op = Operator.EQUALS;
				else
					op = Operator.EQ;
				break;
			default:
				throw new UnsupportedOperationException("Got: " + exp);
			}
			if(exp.getNumArgs() != 2)
				throw new UnsupportedOperationException("Got: " + exp);
			return new Operation(op, createExpr(exp.getArgs()[0]), createExpr(exp.getArgs()[1]));
		case 3:
			switch (exp.getFuncDecl().getDeclKind()) {
			case Z3_OP_SEQ_EXTRACT:
				return new Operation(Operator.SUBSTRING, createExpr(exp.getArgs()[0]), createExpr(exp.getArgs()[1]), createExpr(exp.getArgs()[2]));
			default:
				throw new UnsupportedOperationException("Got: " + exp);

			}
		default:
			throw new UnsupportedOperationException("Got: " + exp);
		}
	}

	public LinkedList<Z3GreenBridge> generateOptions(Z3GreenBridge data) {
		LinkedList<Z3GreenBridge> ret = new LinkedList<Z3GreenBridge>();
		OperatorCollector ctr = new OperatorCollector();
//		System.out.println("Working from: " + data.constraints_int);
		data.constraints = createExpr(data.constraints_int);
//		System.out.println("=> becomes " + data.constraints);
		for(BoolExpr b : data.domains)
		{
			if(data.metaConstraints == null)
				data.metaConstraints = createExpr(b);
			else
				data.metaConstraints = new Operation(Operator.AND,data.metaConstraints,createExpr(b));
		}
		try {
			data.constraints.accept(ctr);
		} catch (VisitorException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		System.out.println(data.constraints);
//		System.out.println(data.metaConstraints);

		// System.out.println();
		for(Expression e : combination(ctr.getOps(), data.constraints))
		{
			Z3GreenBridge newOption = new Z3GreenBridge();
			newOption.charAts = data.charAts;
			newOption.varNames = data.varNames;
			newOption.constraints = e;
			newOption.metaConstraints = data.metaConstraints;
			ret.add(newOption);
		}
		return ret;
		// return ret;
	}

	private Expression copyFirstN(int c, Expression exp) {
		if(c == 0)
			return null;
		if (exp instanceof Constant)
			return exp;
		if (exp instanceof Variable)
			return exp;
		if (exp instanceof Operation) {
			Expression[] newOperands = new Expression[((Operation) exp).getOperator().getArity()];
			for (int i = 0; i < newOperands.length; i++)
			{
				newOperands[i] = copyFirstN(c-1,((Operation) exp).getOperand(i));
			}
			return new Operation(((Operation) exp).getOperator(), newOperands);
		}
		return null;
	}

	public ArrayList<Expression> combination(ArrayList<Operation> operationsToFlip, Expression original) {
		ArrayList<Expression> ret = new ArrayList<Expression>();
		//https://github.com/hmkcode/Java/blob/master/java-combinations/Combination.java
		int N = operationsToFlip.size();
		for (int K = 1; K <= N; K++) {
			int combination[] = new int[K];
			int r = 0;
			int index = 0;
			while (r >= 0) {
				if (index <= (N + (r - K))) {
					combination[r] = index;
					if (r == K - 1) {
						HashSet<Operation> toFlip= new HashSet<Operation>();
						for(int i : combination)
							toFlip.add(operationsToFlip.get(i));
						ret.add(copyAndFlip(original, toFlip));
						index++;
					} else {
						index = combination[r] + 1;
						r++;
					}
				} else {
					r--;
					if (r > 0)
						index = combination[r] + 1;
					else
						index = combination[0] + 1;
				}
			}
		}
		return ret;
	}

	private Expression copyAndFlip(Expression exp, HashSet<Operation> operationsToFlip) {
		if (exp instanceof Constant)
			return exp;
		if (exp instanceof Variable)
			return exp;
		if (exp instanceof Operation) {
			Expression[] newOperands = new Expression[((Operation) exp).getOperator().getArity()];
			for (int i = 0; i < newOperands.length; i++)
				newOperands[i] = copyAndFlip(((Operation) exp).getOperand(i), operationsToFlip);
			if (!operationsToFlip.contains(exp)) {
				return new Operation(((Operation) exp).getOperator(), newOperands);
			}
			switch (((Operation) exp).getOperator()) {
			case EQ:
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
			case EQUALS:
			case STARTSWITH:
			case ENDSWITH:
			case CONTAINS:
				return new Operation(Operator.NOT, new Operation(((Operation) exp).getOperator(), newOperands));
			default:
				break;
			}
		}
		return null;
	}
}
