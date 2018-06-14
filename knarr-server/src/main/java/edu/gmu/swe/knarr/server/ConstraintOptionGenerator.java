package edu.gmu.swe.knarr.server;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;

import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.ArraySort;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BitVecNum;
import com.microsoft.z3.BitVecSort;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.BoolSort;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl.Parameter;
import com.microsoft.z3.IntNum;
import com.microsoft.z3.RatNum;
import com.microsoft.z3.SeqExpr;
import com.microsoft.z3.Sort;
import com.microsoft.z3.Z3Exception;
import com.microsoft.z3.enumerations.Z3_decl_kind;

import za.ac.sun.cs.green.expr.ArrayVariable;
import za.ac.sun.cs.green.expr.BVConstant;
import za.ac.sun.cs.green.expr.BVVariable;
import za.ac.sun.cs.green.expr.BoolConstant;
import za.ac.sun.cs.green.expr.Constant;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.IntVariable;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.StringConstant;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.RealConstant;
import za.ac.sun.cs.green.expr.RealVariable;
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

		Z3_decl_kind t = exp.getFuncDecl().getDeclKind();
		
		// Multi-arity operations
		Operator op = null;
		switch (exp.getFuncDecl().getDeclKind()) {
			case Z3_OP_CONCAT:
				op = Operator.BIT_CONCAT;
				break;
			case Z3_OP_AND:
				op = Operator.AND;
				break;
			case Z3_OP_OR:
				op = Operator.OR;
				break;
			case Z3_OP_ADD:
			case Z3_OP_BADD:
				op = Operator.ADD;
				break;
			default:
				op = null;
				break;
		}
		
		if (op != null) {
			if (exp.getNumArgs() == 1)
				return createExpr(exp.getArgs()[0]);

			Operation ret = new Operation(op, createExpr(exp.getArgs()[0]), createExpr(exp.getArgs()[1]));
			for (int i = 2 ; i < exp.getNumArgs() ; i++)
				ret = new Operation(op, ret, createExpr(exp.getArgs()[i]));

			return ret;
		}
		
		int arity = exp.getFuncDecl().getArity();
		switch (arity) {
		case 0:
			switch (exp.getFuncDecl().getDeclKind()) {
			case Z3_OP_ANUM:
				if(exp.isIntNum())
					try {
					return new IntConstant(((IntNum)exp).getInt64());
					} catch (Z3Exception e) {
						// Maybe it's a negative long?  Try again with BigInteger
						// https://stackoverflow.com/questions/20383866/z3-modeling-java-twos-complement-overflow-and-underflow-in-z3-bit-vector-addit
						BigInteger b = ((IntNum)exp).getBigInteger().negate();
						if (b.bitLength() > 64)
							throw e;
						return new IntConstant(b.longValue());
					}
				if (exp.isRatNum()) {
					double num = (double) ((RatNum)exp).getNumerator().getInt64();
					double den = (double) ((RatNum)exp).getDenominator().getInt64();
					double val = num / den;
					return new RealConstant(val);
				}
				return new IntVariable(exp.getSExpr(), 0, 0);
			case Z3_OP_BNUM:
				Parameter p[] = exp.getFuncDecl().getParameters();
				int p1int = p[1].getInt();
				int size = ((BitVecNum)exp).getSortSize();
				long explong;
				try {
					explong = ((BitVecNum)exp).getLong();
				} catch (Z3Exception e) {
					if (size == 64) {
						// Maybe it's a negative long?  Try again with BigInteger
						// https://stackoverflow.com/questions/20383866/z3-modeling-java-twos-complement-overflow-and-underflow-in-z3-bit-vector-addit
						BigInteger val = ((BitVecNum)exp).getBigInteger();
						explong = val.longValue();
					} else throw e;
				}
				return new BVConstant(explong, p1int);
			case Z3_OP_UNINTERPRETED:
				if(exp.isInt())
					return new IntVariable(exp.getSExpr(), 0, 0);
				else if (exp.isBV())
					return new BVVariable(exp.getSExpr(), ((BitVecExpr)exp).getSortSize());
				else if (exp.isReal())
					return new RealVariable(exp.getSExpr());
				else if (exp instanceof SeqExpr)
					return new StringVariable(exp.getSExpr());
				else if (exp.isArray()) {
					ArrayExpr e = (ArrayExpr)exp;
					ArraySort s = (ArraySort)e.getSort();
					Sort r = s.getRange();
					if (r instanceof BitVecSort)
					{
						switch (((BitVecSort)r).getSize()) {
							case 8:
								return new ArrayVariable(exp.getSExpr(), java.lang.Byte.TYPE);
							case 32:
								return new ArrayVariable(exp.getSExpr(), java.lang.Integer.TYPE);
							case 64:
								return new ArrayVariable(exp.getSExpr(), java.lang.Long.TYPE);
							default:
								throw new UnsupportedOperationException();
						}
					} else if (r instanceof BoolSort)
					{
						return new ArrayVariable(exp.getSExpr(), java.lang.Boolean.TYPE);
					}
					throw new UnsupportedOperationException();
				}
				else
					throw new UnsupportedOperationException();
//					return new StringVariable(exp.getSExpr());
			case Z3_OP_INTERNAL:
				return new StringConstant(exp.getSExpr().substring(1, exp.getSExpr().length() - 1));
			case Z3_OP_FALSE:
				return new BoolConstant(false);
			case Z3_OP_TRUE:
				return new BoolConstant(true);
			case Z3_OP_SEQ_EMPTY:
				return new StringConstant("");
			default:
				throw new UnsupportedOperationException("Got: " + exp + " " + exp.getFuncDecl().getDeclKind());
			}
		case 1:
			switch (exp.getFuncDecl().getDeclKind()) {
			case Z3_OP_NOT:
				op = Operator.NOT;
				break;
			case Z3_OP_BNOT:
				op = Operator.BIT_NOT;
				break;
			case Z3_OP_SEQ_LENGTH:
				op = Operator.LENGTH;
				break;
			case Z3_OP_EXTRACT:
			{
				op = Operator.EXTRACT;
				Parameter[] p = exp.getFuncDecl().getParameters();
				return new Operation(op, p[0].getInt(), p[1].getInt(), createExpr(exp.getArgs()[0]));
			}
			case Z3_OP_INT2BV:
			{
				op = Operator.I2BV;
				Parameter[] p = exp.getFuncDecl().getParameters();
				return new Operation(op, p[0].getInt(), createExpr(exp.getArgs()[0]));
			}
			case Z3_OP_BV2INT:
				op = Operator.BV2I;
				break;
			case Z3_OP_SIGN_EXT:
				op = Operator.SIGN_EXT;
				Parameter[] p = exp.getFuncDecl().getParameters();
				return new Operation(op, p[0].getInt(), createExpr(exp.getArgs()[0]));
			case Z3_OP_ZERO_EXT:
				op = Operator.ZERO_EXT;
				p = exp.getFuncDecl().getParameters();
				return new Operation(op, p[0].getInt(), createExpr(exp.getArgs()[0]));
			case Z3_OP_TO_REAL:
				op = Operator.I2R;
				return new Operation(op, createExpr(exp.getArgs()[0]));
			case Z3_OP_UMINUS:
				op = Operator.NEG;
				return new Operation(op, createExpr(exp.getArgs()[0]));
			case Z3_OP_SEQ_UNIT:
				return new Operation(Operator.CONCAT, new StringConstant(""), createExpr(exp.getArgs()[0]));
			default:
				throw new UnsupportedOperationException("Got: " + exp + " " + exp.getFuncDecl().getDeclKind());
			}
			return new Operation(op, createExpr(exp.getArgs()[0]));
		case 2:
			switch (t) {
			case Z3_OP_BOR:
				op = Operator.BIT_OR;
				break;
			case Z3_OP_BAND:
				op = Operator.BIT_AND;
				break;
			case Z3_OP_GE:
			case Z3_OP_SGEQ:
				op = Operator.GE;
				break;
			case Z3_OP_GT:
			case Z3_OP_SGT:
				op = Operator.GT;
				break;
			case Z3_OP_LE:
			case Z3_OP_SLEQ:
				op = Operator.LE;
				break;
			case Z3_OP_LT:
			case Z3_OP_SLT:
				op = Operator.LT;
				break;
			case Z3_OP_EQ:
				if(exp.getArgs()[0] instanceof SeqExpr)
					op = Operator.EQUALS;
				else
					op = Operator.EQ;
				break;
			case Z3_OP_BSUB:
				op = Operator.SUB;
				break;
			case Z3_OP_MUL:
			case Z3_OP_BMUL:
				op = Operator.MUL;
				break;
			case Z3_OP_SELECT:
				op = Operator.SELECT;
				break;
			case Z3_OP_BSHL:
				op = Operator.SHIFTL;
				break;
			case Z3_OP_BASHR:
				op = Operator.SHIFTR;
				break;
			case Z3_OP_BLSHR:
				op = Operator.SHIFTUR;
				break;
			case Z3_OP_BXOR:
				op = Operator.BIT_XOR;
				break;
			case Z3_OP_DIV:
			case Z3_OP_BSDIV:
			case Z3_OP_BSDIV_I:
				op = Operator.DIV;
				break;
			case Z3_OP_SUB:
				op = Operator.SUB;
				break;
			case Z3_OP_BSMOD:
			case Z3_OP_BSMOD_I:
				op = Operator.MOD;
				break;
			case Z3_OP_SEQ_CONCAT:
				op = Operator.CONCAT;
				break;
			case Z3_OP_SEQ_PREFIX:
				op = Operator.STARTSWITH;
				break;
			default:
				throw new UnsupportedOperationException("Got: " + exp);
			}
			if(exp.getNumArgs() != 2)
				throw new UnsupportedOperationException("Got: " + exp);
			return new Operation(op, createExpr(exp.getArgs()[0]), createExpr(exp.getArgs()[1]));
		case 3:
			Expr[] e = exp.getArgs();
			switch (t) {
			case Z3_OP_SEQ_EXTRACT:
				return new Operation(Operator.SUBSTRING, createExpr(exp.getArgs()[0]), createExpr(exp.getArgs()[1]), createExpr(exp.getArgs()[2]));
			case Z3_OP_ITE:
				return new Operation(Operator.ITE, createExpr(e[0]), createExpr(e[1]), createExpr(e[2]));
			case Z3_OP_STORE:
				return new Operation(Operator.STORE, createExpr(e[0]), createExpr(e[1]), createExpr(e[2]));
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
//		for(Expression e : combination(ctr.getOps(), data.constraints))
//		{
			Z3GreenBridge newOption = new Z3GreenBridge();
			newOption.charAts = data.charAts;
			newOption.varNames = data.varNames;
//			newOption.constraints = e;
			newOption.constraints = data.constraints;
			newOption.metaConstraints = data.metaConstraints;
			ret.add(newOption);
//		}
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
