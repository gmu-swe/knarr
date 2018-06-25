package edu.gmu.swe.knarr.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import za.ac.sun.cs.green.expr.ArrayVariable;
import za.ac.sun.cs.green.expr.BVConstant;
import za.ac.sun.cs.green.expr.Constant;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Variable;
import za.ac.sun.cs.green.expr.Visitor;
import za.ac.sun.cs.green.expr.VisitorException;
import za.ac.sun.cs.green.expr.Operation.Operator;

@SuppressWarnings("unused")
public class Canonizer implements Serializable {
	private static final long serialVersionUID = -255868518342105487L;

	private transient final CanonicalForm[] forms;
	
	private TreeMap<String, HashSet<Expression>> constArrayInits = new TreeMap<>(new VariableNameComparator());
	private TreeMap<String, HashSet<Expression>> canonical = new TreeMap<>(new VariableNameComparator());
	private HashSet<Expression> notCanonical = new HashSet<>();

	private TreeSet<Variable> variables = new TreeSet<>(new VariableComparator());

	public Canonizer() {
		Class<?>[] cs = Canonizer.class.getDeclaredClasses();
		LinkedList<CanonicalForm> lst = new LinkedList<>();
		
		for (int i = 0; i < cs.length; i++) {
			Class<?> c = cs[i];
			
			if (c.getSuperclass() != CanonicalForm.class) {
				continue;
			}
			
			try {
				Constructor<?> cc = c.getDeclaredConstructor(Canonizer.class);
				if (c == ConstArrayInit.class)
					lst.addFirst((CanonicalForm) cc.newInstance(this));
				else
					lst.add((CanonicalForm) cc.newInstance(this));
			} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
				throw new Error(ex);
			}
		}
		
		forms = lst.toArray(new CanonicalForm[0]);
	}
	
	
	public void canonize(Expression exp) {
		// Expression has the form: ((((exp) AND exp) ...) AND  exp)
		// Extract each individual expression
		Expression e = exp;
		while (true)
		{
			Operation op = (Operation)e;
			Operation toCanon = (op.getOperator() == Operator.AND) ? ((Operation)op.getOperand(1)) : op;

			CanonizeReturn ret = doCanonize(toCanon);
			switch (ret.type) {
				case ARRAY_INIT:
					HashSet<Expression> s = constArrayInits.get(ret.varName);
					if (s == null) {
						s = new HashSet<>();
						constArrayInits.put(ret.varName, s);
					}
					s.add(ret.expr);
					break;
				case NOT_CAN:
					notCanonical.add(op.getOperand(1));
					break;
				case CAN_VAR:
					s = canonical.get(ret.varName);
					if (s == null) {
						s = new HashSet<>();
						canonical.put(ret.varName, s);
					}
					s.add(ret.expr);
					break;
				default:
					throw new UnsupportedOperationException();
			}
			
			if (toCanon != op)
				e = op.getOperand(0);
			else
				break;
		}
	}
	
	public Map<String, HashSet<Expression>> getConstArrayInits() {
		return constArrayInits;
	}

	public Map<String, HashSet<Expression>> getCanonical() {
		return canonical;
	}
	
	public HashSet<Expression> getNotCanonical() {
		return notCanonical;
	}
	
	public Set<Variable> getVariables() {
		return variables;
	}

	private CanonizeReturn doCanonize(Expression exp) {
		for (CanonicalForm c : forms) {
			String varName = c.matches(exp);

			if (varName != null) {
				if (c.isCanonical(exp))
					if (c instanceof ConstArrayInit) {
						if (!varName.startsWith("const_array"))
							throw new Error();
						return new CanonizeReturn(varName, c.canonize(exp), CanonizeReturn.Type.ARRAY_INIT);
					} else {
						if (!varName.startsWith("autoVar"))
							throw new Error();
						return new CanonizeReturn(varName, c.canonize(exp), CanonizeReturn.Type.CAN_VAR);
					}
			}
		}
		
		return new CanonizeReturn(null, exp, CanonizeReturn.Type.NOT_CAN);
	}
	
	private static class CanonizeReturn {
		final String varName;
		final Expression expr;
		final Type type;
		
		enum Type { NOT_CAN, CAN_VAR, ARRAY_INIT }

		public CanonizeReturn(String varName, Expression expr, Type type) {
			super();
			this.varName = varName;
			this.expr = expr;
			this.type = type;
		};
	}
	
	private static class VariableNameComparator implements Comparator<String>, Serializable {
		private static final long serialVersionUID = -2928217977905811172L;

		private static Pattern variable = Pattern.compile("autoVar_(\\d+)");
		private static Pattern array = Pattern.compile("const_array_(\\d+)_(\\d_)");

		@Override
		public int compare(String v1, String v2) {
			int ret;
			Matcher m1 = variable.matcher(v1);
			Matcher m2;

			if (m1.matches() && (m2 = variable.matcher(v2)).matches()) {
				Integer i1 = Integer.parseInt(m1.group(1));
				Integer i2 = Integer.parseInt(m2.group(1));
				return i1.compareTo(i2);
			} else if ((m1 = array.matcher(v1)).matches() && (m2 = array.matcher(v2)).matches()) {
				Integer i1 = Integer.parseInt(m1.group(1));
				Integer i2 = Integer.parseInt(m2.group(1));
				
				if (i1 != i2) {
					return i1.compareTo(i2);
				} else {
					i1 = Integer.parseInt(m1.group(2));
					i2 = Integer.parseInt(m2.group(2));
					
					return i1.compareTo(i2);
				}
			} else return v1.compareTo(v2);
		}
	}

	private static class VariableComparator implements Comparator<Variable>, Serializable {
		private static final long serialVersionUID = 4563019768128190019L;

		private VariableNameComparator comp = new VariableNameComparator();

		@Override
		public int compare(Variable v1, Variable v2) {
			return comp.compare(v1.getName(), v2.getName());
		}
	}

	private abstract class CanonicalForm {
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
				
//				if (p.getArity() != e.getArity())
//					return null;
				
				if (p.getOperator() != null && e.getOperator() != p.getOperator())
					return null;
				
				for (int i = 0 ; i < Math.min(p.getArity(), e.getArity()) ; i++) {
					String v = matches(p.getOperand(i), e.getOperand(i), varName);
					if (v == null)
						return null;
					else if (!v.equals(""))
						varName = v;
				}
				
				return varName;

			} else if (pattern instanceof AnyVariable) {
				if (! (exp instanceof Variable))
					return null;
				
				// Add variable to seen variables
				variables.add((Variable)exp);

				return ((Variable)exp).getName();
			} else if (pattern instanceof Variable) {
				if (! (exp instanceof Variable))
					return null;
				
				// Add variable to seen variables
				variables.add((Variable)exp);
				
				// Ignore variables with null name
				if (((Variable)pattern).getName() == null)
					return varName;
				
				Class<?> c = pattern.getClass();
				return c.isInstance(exp) ? ((Variable)exp).getName() : null;
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
		
		public String canonicalVarName(String name) {
			return name;
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
	
	private class NotOp extends CanonicalForm {
		private CanonizeReturn cache = null;

		NotOp() {
			pattern = new Operation(Operator.NOT, new Expression[] {null});
		}

		@Override
		public boolean isCanonical(Expression exp) {
			Operation op = (Operation) exp;
			cache = doCanonize(op.getOperand(0));
			
			return cache.type != CanonizeReturn.Type.NOT_CAN;
		}

		@Override
		public Expression canonize(Expression exp) {
			if (cache.type != CanonizeReturn.Type.NOT_CAN)
				return new Operation(Operator.NOT, cache.expr);
			else
				return exp;
		}
		
	}

	private class OpVarConstant extends CanonicalForm {
		OpVarConstant() {
			pattern = new Operation(null, new AnyVariable(), new BVConstant(0, 0));
		}
	}
	
	private class OpConstantVar extends OpVarConstant {
		OpConstantVar() {
			pattern = new Operation(null, new BVConstant(0, 0), new AnyVariable());
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
	
	private class OpOpVarConstantConstant extends CanonicalForm {
		OpOpVarConstantConstant() {
			pattern = new Operation(
					null,
					new Operation(null, new AnyVariable(), new BVConstant(0, 0)),
					new BVConstant(0, 0));
		}
	}
	
	private class OpOpConstantVarConstant extends CanonicalForm {
		OpOpConstantVarConstant() {
			pattern = new Operation(null, new Operation(null, new BVConstant(0, 0), new AnyVariable()), new BVConstant(0, 0));
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
	
	private class OpOpOpVarConstantConstantConstant extends CanonicalForm {
		OpOpOpVarConstantConstantConstant() {
			pattern = new Operation(
					null,
					new Operation(
							null,
							new Operation(null, new AnyVariable(), new BVConstant(0, 0)),
							new BVConstant(0, 0)),
					new BVConstant(0, 0));
		}
	}
	
	private class OpOpOpOpVarConstantConstantConstant extends CanonicalForm {
		OpOpOpOpVarConstantConstantConstant() {
			pattern = new Operation(
					null,
					new Operation(
							null,
							new Operation(null,
									new Operation(null, new AnyVariable(), new BVConstant(0, 0)),
									new BVConstant(0, 0)),
							new BVConstant(0, 0)),
					new BVConstant(0, 0));
		}
	}
	
	private class OpOpOpOpOpVarConstantConstantConstant extends CanonicalForm {
		OpOpOpOpOpVarConstantConstantConstant() {
			pattern = new Operation(
					null,
					new Operation(
							null,
							new Operation(null,
									new Operation(null,
											new Operation(null, new AnyVariable(), new BVConstant(0, 0)),
											new BVConstant(0, 0)),
									new BVConstant(0, 0)),
							new BVConstant(0, 0)),
					new BVConstant(0, 0));
		}
	}
	
	private class AnyVariable extends Variable {
		public AnyVariable() {
			super("");
		}

		@Override
		public void accept(Visitor visitor) throws VisitorException {
			throw new UnsupportedOperationException();
		}

		@Override
		public String toString() {
			throw new UnsupportedOperationException();
		}
		
		private void writeObject(ObjectOutputStream oos) throws IOException {
			throw new UnsupportedOperationException();
		}
		
		private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
			throw new UnsupportedOperationException();
		}
	}

	private class ConstArrayInit extends CanonicalForm {
		ConstArrayInit() {
			pattern = new Operation(
					Operator.EQ,
					new Operation(
							Operator.SELECT,
							new ArrayVariable("", null),
							new BVConstant(0, 0)),
					new BVConstant(0, 0));
		}
	}

	private class ConstArrayAccessOp extends CanonicalForm {
		ConstArrayAccessOp() {
			pattern = new Operation(
					null,
					new Operation(
							Operator.SELECT,
							new ArrayVariable(null, null),
							new AnyVariable()),
					new BVConstant(0, 0));
		}
	}

	private class ConstArrayAccessOp2 extends CanonicalForm {
		ConstArrayAccessOp2() {
			pattern = new Operation(
					null,
					new BVConstant(0, 0),
					new Operation(
							Operator.SELECT,
							new ArrayVariable(null, null),
							new AnyVariable()));
		}

		@Override
		public Expression canonize(Expression exp) {
			Operation op = (Operation)exp;
			
			if (commutative.contains(op.getOperator()))
				return new Operation(op.getOperator(), op.getOperand(1), op.getOperand(0));

			return exp;
		}
		
		
	}

	private class ConstArrayAccessComplexOp extends CanonicalForm {
		private CanonizeReturn cache;

		ConstArrayAccessComplexOp() {
			pattern = new Operation(
					null,
					new Operation(
							Operator.SELECT,
							new ArrayVariable(null, null),
							null),
					new BVConstant(0, 0));
		}
		
		@Override
		public String matches(Expression exp) {
			if (super.matches(exp) != null) {
				Operation op  = (Operation)exp;
				Operation op2 = (Operation)op.getOperand(0);
				
				cache = doCanonize(op2.getOperand(1));
				return cache.varName;
			}

			return null;
		}

		@Override
		public boolean isCanonical(Expression exp) {
			return cache.type != CanonizeReturn.Type.NOT_CAN;
		}
		
	}

	private class ConstArrayAccessComplexOp2 extends CanonicalForm {
		private CanonizeReturn cache;

		ConstArrayAccessComplexOp2() {
			pattern = new Operation(
					null,
					new BVConstant(0, 0),
					new Operation(
							Operator.SELECT,
							new ArrayVariable(null, null),
							null));
		}
		
		@Override
		public String matches(Expression exp) {
			if (super.matches(exp) != null) {
				Operation op  = (Operation)exp;
				Operation op2 = (Operation)op.getOperand(1);
				
				cache = doCanonize(op2.getOperand(1));
				return cache.varName;
			}

			return null;
		}

		@Override
		public boolean isCanonical(Expression exp) {
			return cache.type != CanonizeReturn.Type.NOT_CAN;
		}

		@Override
		public Expression canonize(Expression exp) {
			Operation op  = (Operation)exp;
			
			if (commutative.contains(op.getOperator()))
				return new Operation(op.getOperator(), op.getOperand(1), op.getOperand(0));
			else
				return exp;
		}
	}

	private class ConstArrayAccessComplexOpOp extends CanonicalForm {
		private CanonizeReturn cache;

		ConstArrayAccessComplexOpOp() {
			pattern = new Operation(
					null,
					new Operation(
							null,
							new Operation(
									Operator.SELECT,
									new ArrayVariable(null, null),
									null),
							new BVConstant(0, 0)),
						new BVConstant(0, 0));
		}
		
		@Override
		public String matches(Expression exp) {
			if (super.matches(exp) != null) {
				Operation op  = (Operation)exp;
				Operation op2 = (Operation)op.getOperand(0);
				Operation op3 = (Operation)op2.getOperand(0);

				cache = doCanonize(op3.getOperand(1));
				return cache.varName;
			}

			return null;
		}
		
		@Override
		public boolean isCanonical(Expression exp) {
			Operation op  = (Operation)exp;
			Operation op2 = (Operation)op.getOperand(0);
			Operation op3 = (Operation)op2.getOperand(0);
			
			CanonizeReturn ret = doCanonize(op3.getOperand(1));
			
			return ret.type != CanonizeReturn.Type.NOT_CAN;
		}
		
	}
}
