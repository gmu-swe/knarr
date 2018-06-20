package edu.gmu.swe.knarr.server;

import java.io.Serializable;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import za.ac.sun.cs.green.expr.BVConstant;
import za.ac.sun.cs.green.expr.BVVariable;
import za.ac.sun.cs.green.expr.Constant;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Variable;
import za.ac.sun.cs.green.expr.Operation.Operator;

public class Canonizer implements Serializable {
	private static final long serialVersionUID = -255868518342105487L;

	private static final CanonicalForm[] forms;
	
	static {
		Class<?>[] cs = Canonizer.class.getDeclaredClasses();
		forms = new CanonicalForm[cs.length - 1];
		
		for (int i = 0, j = 0; i < cs.length; i++, j++) {
			Class<?> c = cs[i];
			
			if (c.equals(CanonicalForm.class)) {
				j--;
				continue;
			}
			
			try {
				forms[j] = (CanonicalForm) c.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
	}
	
	private HashMap<String, HashSet<Expression>> canonical = new HashMap<>();
	private HashSet<Expression> notCanonical = new HashSet<>();
	
	public void canonize(Expression exp) {
		// Expression has the form: ((((exp) AND exp) ...) AND  exp)
		// Extract each individual expression
		Expression e = exp;
		while (true)
		{
			Operation op = (Operation)e;
			if (op.getOperator() == Operator.AND)
			{
				SimpleEntry<String, Expression> ex = doCanonize((Operation)op.getOperand(1));
				if (ex != null) {
					HashSet<Expression> s = canonical.get(ex.getKey());
					if (s == null) {
						s = new HashSet<>();
						canonical.put(ex.getKey(), s);
					}
					s.add(ex.getValue());
				} else {
					notCanonical.add(op.getOperand(1));
				}
				e = op.getOperand(0);
			} else {
				SimpleEntry<String, Expression> ex = doCanonize(op);
				if (ex != null) {
					HashSet<Expression> s = canonical.get(ex.getKey());
					if (s == null) {
						s = new HashSet<>();
						canonical.put(ex.getKey(), s);
					}
					s.add(ex.getValue());
				} else {
					notCanonical.add(op.getOperand(1));
				}
				e = op.getOperand(0);
				break;
			}
		}
	}
	
	public HashMap<String, HashSet<Expression>> getCanonical() {
		return canonical;
	}
	
	public HashSet<Expression> getNotCanonical() {
		return notCanonical;
	}

	private static SimpleEntry<String, Expression> doCanonize(Expression exp) {
		
		for (CanonicalForm c : forms) {
			String varName = c.matches(exp);

			if (varName != null) {
				if (c.isCanonical(exp))
					return new SimpleEntry<String, Expression>(varName, c.canonize(exp));
			}
		}
		
		return null;
	}
	
	private static abstract class CanonicalForm {
		Expression pattern;
		
		public String matches(Expression exp) {
			return matches(pattern, exp, "");
		}
		
		private String matches(Expression pattern, Expression exp, String varName) {
			
			if (pattern == null) {
				return varName;
			} else if (pattern instanceof Operation) {
				if (! (exp instanceof Operation))
					return null;
				
				Operation p = (Operation) pattern;
				Operation e = (Operation) exp;
				
				if (p.getArity() != e.getArity())
					return null;
				
				if (p.getOperator() != null && e.getOperator() != p.getOperator())
					return null;
				
				for (int i = 0 ; i < p.getArity() ; i++) {
					String v = matches(p.getOperand(i), e.getOperand(i), varName);
					if (v == null)
						return null;
					else if (!v.equals(""))
						varName = v;
				}
				
				return varName;

			} else if (pattern instanceof Variable) {
				return (exp instanceof Variable ? ((Variable)exp).getName() : null);
			} else if (pattern instanceof Constant) {
				return (exp instanceof Constant ? varName : null);
			} else {
				throw new UnsupportedOperationException("Unknown expression in pattern");
			}
		}
		
		public boolean isCanonical(Expression exp) {
			return true;
		}
		
		public Expression canonize(Expression exp) {
			return exp;
		}
	}
	
	private static HashSet<Operator> commutative = new HashSet<>(Arrays.asList(
			Operator.ADD,
			Operator.MUL,
			Operator.BIT_AND,
			Operator.BIT_OR,
			Operator.BIT_XOR,
			Operator.EQ,
			Operator.EQUALS,
			Operator.NOTEQUALS
			));
	
	private static class NotOp extends CanonicalForm {
		private SimpleEntry<String, Expression> cache = null;

		NotOp() {
			pattern = new Operation(Operator.NOT, new Expression[] {null});
		}
		
		@Override
		public boolean isCanonical(Expression exp) {
			Operation op = (Operation) exp;
			cache = doCanonize(op.getOperand(0));
			
			return cache != null;
		}

		@Override
		public Expression canonize(Expression exp) {
			return cache.getValue();
		}
		
	}

	private static class OpVarConstant extends CanonicalForm {
		OpVarConstant() {
			pattern = new Operation(null, new BVVariable("", 0), new BVConstant(0, 0));
		}
	}
	
	private static class OpConstantVar extends OpVarConstant {
		OpConstantVar() {
			pattern = new Operation(null, new BVConstant(0, 0), new BVVariable("", 0));
		}

		@Override
		public Expression canonize(Expression exp) {
			Operation op = (Operation) exp;
			if (commutative.contains(op.getOperator()))
				return new Operation(op.getOperator(), op.getOperand(1), op.getOperand(0));
			else
				return exp;
		}
	}
	
	private static class OpOpVarConstantConstant extends CanonicalForm {
		OpOpVarConstantConstant() {
			pattern = new Operation(
					null,
					new Operation(null, new BVVariable("", 0), new BVConstant(0, 0)),
					new BVConstant(0, 0));
		}
	}
	
	private static class OpOpConstantVarConstant extends CanonicalForm {
		OpOpConstantVarConstant() {
			pattern = new Operation(null, new Operation(null, new BVConstant(0, 0), new BVVariable("", 0)), new BVConstant(0, 0));
		}

		@Override
		public Expression canonize(Expression exp) {
			Operation op = (Operation) exp;
			Operation op2 = (Operation) op.getOperand(0);
			if (commutative.contains(op2.getOperator()))
				return new Operation(
						op.getOperator(),
						new Operation(op2.getOperator(), op2.getOperand(1), op2.getOperand(0)),
						op.getOperand(1));
			else
				return exp;
		}
	}
	
	private static class OpOpOpVarConstantConstantConstant extends CanonicalForm {
		OpOpOpVarConstantConstantConstant() {
			pattern = new Operation(
					null,
					new Operation(
							null,
							new Operation(null, new BVVariable("", 0), new BVConstant(0, 0)),
							new BVConstant(0, 0)),
					new BVConstant(0, 0));
		}
	}
	
	private static class OpOpOpOpVarConstantConstantConstant extends CanonicalForm {
		OpOpOpOpVarConstantConstantConstant() {
			pattern = new Operation(
					null,
					new Operation(
							null,
							new Operation(null,
									new Operation(null, new BVVariable("", 0), new BVConstant(0, 0)),
									new BVConstant(0, 0)),
							new BVConstant(0, 0)),
					new BVConstant(0, 0));
		}
	}

}
