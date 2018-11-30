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
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import za.ac.sun.cs.green.expr.ArrayVariable;
import za.ac.sun.cs.green.expr.BVConstant;
import za.ac.sun.cs.green.expr.BVVariable;
import za.ac.sun.cs.green.expr.BoolConstant;
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
	
	private LinkedList<Expression> order = new LinkedList<>();

	// TODO make this static and run only once
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

	public Canonizer(Canonizer c) {
		this();

		synchronized (c) {
			for (Entry<String, HashSet<Expression>> entry : c.canonical.entrySet())
				canonical.put(entry.getKey(), new HashSet<>(entry.getValue()));

			for (Entry<String, HashSet<Expression>> entry : c.constArrayInits.entrySet())
				constArrayInits.put(entry.getKey(), new HashSet<>(entry.getValue()));

			notCanonical.addAll(c.notCanonical);
			variables.addAll(c.variables);
			order.addAll(c.order);
		}

	}
	
	
	public synchronized void canonize(Expression exp) {
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
					order.addFirst(ret.expr);
					break;
				case NOT_CAN:
					notCanonical.add(toCanon);
					order.addFirst(toCanon);
					break;
				case CAN_VAR:
					s = canonical.get(ret.varName);
					if (s == null) {
						s = new HashSet<>();
						canonical.put(ret.varName, s);
					}
					s.add(ret.expr);
					order.addFirst(ret.expr);
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
	
	public LinkedList<Expression> getOrder() {
		return order;
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
	
	public TreeSet<Variable> getVariables() {
		return variables;
	}
	
	public synchronized Expression getExpression() {
		Expression expr = new BoolConstant(true);

		for (HashSet<Expression> es : constArrayInits.values()) {
			for (Expression e : es)
				expr = new Operation(Operator.AND, e, expr);
		}

		for (HashSet<Expression> es : canonical.values()) {
			for (Expression e : es)
				expr = new Operation(Operator.AND, e, expr);
		}

		for (Expression e : notCanonical)
			expr = new Operation(Operator.AND, e, expr);
		
		return expr;
	}

	public synchronized Map<String, Expression> getExpressionMap() {
		HashMap<String, Expression> ret = new HashMap<>();
		
		for (HashSet<Expression> es : this.canonical.values())
			for (Expression e : es)
				ret.put(e.toString(), e);
		
		for (Expression e : this.notCanonical)
			ret.put(e.toString(), e);
		
		for (HashSet<Expression> es : this.constArrayInits.values())
			for (Expression e : es)
				ret.put(e.toString(), e);

		return ret;
	}


	private CanonizeReturn doCanonize(Expression exp) {
		for (CanonicalForm c : forms) {
			String varName = c.matches(exp);

			if (varName != null) {
				if (c.isCanonical(exp))
					if (c instanceof ConstArrayInit) {
//						if (!varName.startsWith("const_array"))
//							throw new Error();
						return new CanonizeReturn(varName, c.canonize(exp), CanonizeReturn.Type.ARRAY_INIT);
					} else {
//						if (!varName.startsWith("autoVar"))
//							throw new Error();
						return new CanonizeReturn(varName, c.canonize(exp), CanonizeReturn.Type.CAN_VAR);
					}
			}
		}
		
		return new CanonizeReturn(null, exp, CanonizeReturn.Type.NOT_CAN);
	}
	
	private static Expression anonymizeVars(Expression e) {
		
		if (e instanceof Variable)
			return AnyVariable.any;
		
		if (e instanceof Operation) {
			Operation op = (Operation) e;
			
			Expression[] operands = new Expression[op.getArity()];
			
			for (int i = 0 ; i < operands.length ; i++)
				operands[i] = anonymizeVars(op.getOperand(i));
			
			return new Operation(op.getOperator(), operands);
		}
		
		return e;
	}
	
	private static Expression rewriteVars(Expression e, Map<String, String> rewrites) {
		
		if (e instanceof Variable) {
			Variable v = (Variable) e;
			
			if (rewrites.containsKey(v.getName())) {
				Variable ret;
				
				if (v.getClass() == BVVariable.class)
					ret = new BVVariable(rewrites.get(v.getName()), ((BVVariable)v).getSize());
				else
					throw new UnsupportedOperationException(v.getClass().getName());
			}
		}
		
		if (e instanceof Operation) {
			Operation op = (Operation) e;
			
			Expression[] operands = new Expression[op.getArity()];
			
			for (int i = 0 ; i < operands.length ; i++)
				operands[i] = rewriteVars(op.getOperand(i), rewrites);
			
			return new Operation(op.getOperator(), operands);
		}
		
		return e;
	}
	
	public static Canonizer compare(Canonizer a, Canonizer b) {
		Canonizer common = new Canonizer();
		TreeMap<String, String> matchBtoA = new TreeMap<>();

		HashMap<String, String> aToB = new HashMap<>();
		HashMap<String, String> bToA = new HashMap<>();

		for (Entry<String, HashSet<Expression>> ea : a.getCanonical().entrySet()) {
			String maxB = null;
			HashSet<Expression> max = new HashSet<>();

			HashSet<Expression> aa = new HashSet<>();

			HashMap<Expression, Expression> aaToEa = new HashMap<>();
			
			
			for (Expression e : ea.getValue()) {
				Expression anonymous = anonymizeVars(e);
				aa.add(anonymous);
				aaToEa.put(anonymous, e);
			}

			for (Entry<String, HashSet<Expression>> eb : b.getCanonical().entrySet()) {

				if (matchBtoA.containsKey(eb.getKey()))
					continue;

				HashSet<Expression> s = new HashSet<>(aa);
				
				HashSet<Expression> bb = new HashSet<>();
				
				for (Expression e : eb.getValue())
					bb.add(anonymizeVars(e));

				s.retainAll(bb);

				int ms = max.size();
				int ss = s.size();
				
				if (s.size() > max.size()) {
					max.clear();
					for (Expression e : s)
						max.add(aaToEa.get(e));

					maxB = eb.getKey();
				}
			}
			
			if (maxB != null)  {
				// Only happens when a is bigger than b and we run out of a constraints
				matchBtoA.put(maxB, ea.getKey());
				common.getCanonical().put(ea.getKey(), max);

				aToB.put(ea.getKey(), maxB);
				bToA.put(maxB, ea.getKey());

				System.out.println(ea.getKey() + " -> " + maxB + " (" + max.size() + ")");

				continue;
			}
		}
		
		for (Variable v : a.getVariables()) {
			if (common.getCanonical().containsKey(v.getName()))
				common.getVariables().add(v);
		}
		
		// TODO something about the const array inits?
		common.getConstArrayInits().putAll(a.getConstArrayInits());

		{
			HashSet<Expression> aa = new HashSet<>();
			HashSet<Expression> bb = new HashSet<>();

			// Rewrite variables in notCanonical b according to previous match
			for (Expression e : b.getNotCanonical()) {
				bb.add(rewriteVars(e, bToA));
			}
			
			// Keep the matching constraints
			aa.addAll(a.getNotCanonical());
			aa.retainAll(bb);

			common.getNotCanonical().addAll(aa);
			
			System.out.println(common.getNotCanonical().size());

//			aa.removeAll(s);
//			bb.removeAll(s);
		}
		
		return common;
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

	private static class AnyVariable extends Variable {
		private static AnyVariable any = new AnyVariable();

		private AnyVariable() {
			super("");
			if (any != null)
				throw new Error();
		}
	
		@Override
		public void accept(Visitor visitor) throws VisitorException {
			throw new UnsupportedOperationException();
		}
	
		@Override
		public String toString() {
			return "*";
		}
		
		private void writeObject(ObjectOutputStream oos) throws IOException {
			throw new UnsupportedOperationException();
		}
		
		private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
			throw new UnsupportedOperationException();
		}
	}
	
	private static class Canonical extends Expression {

		@Override
		public void accept(Visitor visitor) throws VisitorException {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean equals(Object object) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String toString() {
			return "CAN";
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
			} else if (pattern instanceof Canonical) {
				CanonizeReturn cr = doCanonize(exp);
				
				switch (cr.type) {
					case CAN_VAR:
						return cr.varName;
					default:
						return null;
				}
				
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

			} else if (pattern == AnyVariable.any) {
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
			Operator.NE,
			Operator.EQUALS,
			Operator.NOTEQUALS
			));
	
	private class NotOp extends CanonicalForm {
		NotOp() {
			pattern = new Operation(Operator.NOT, new Canonical());
		}
	}

	private class OpVarConstant extends CanonicalForm {
		OpVarConstant() {
			pattern = new Operation(null, AnyVariable.any, new BVConstant(0, 0));
		}
	}

	private class OpConstantCanonical extends CanonicalForm {
		OpConstantCanonical() {
			pattern = new Operation(null, new BVConstant(0, 0), new Canonical());
		}

		@Override
		public Expression canonize(Expression exp) {
			Operation op = (Operation) exp;
			
			if (commutative.contains(op.getOperator()))
				return new Operation(op.getOperator(), op.getOperand(1), op.getOperand(0));
			else
				return op;
		}
	}

	private class OpCanonicalConstant extends CanonicalForm {
		OpCanonicalConstant() {
			pattern = new Operation(null, new Canonical(), new BVConstant(0, 0));
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

	private class ConstArrayAccessVar extends CanonicalForm {
		ConstArrayAccessVar() {
			pattern = new Operation(
					Operator.SELECT,
					new ArrayVariable(null, null),
					AnyVariable.any);
		}
	}

	private class ConstArrayAccessCan extends CanonicalForm {
		ConstArrayAccessCan() {
			pattern = new Operation(
					Operator.SELECT,
					new ArrayVariable(null, null),
					new Canonical());
		}
	}
}
