package edu.gmu.swe.knarr.runtime;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import edu.columbia.cs.psl.phosphor.struct.*;
import za.ac.sun.cs.green.expr.BVConstant;
import za.ac.sun.cs.green.expr.BVVariable;
import za.ac.sun.cs.green.expr.BinaryOperation;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntVariable;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type;
import edu.columbia.cs.psl.phosphor.runtime.Taint;
import za.ac.sun.cs.green.expr.RealVariable;
import za.ac.sun.cs.green.expr.StringConstant;
import za.ac.sun.cs.green.expr.StringVariable;
import za.ac.sun.cs.green.expr.UnaryOperation;

public class Symbolicator {
	static Socket serverConnection;
	static String SERVER_HOST = System.getProperty("SATServer", "127.0.0.1");
	static int SERVER_PORT = Integer.valueOf(System.getProperty("SATPort", "9090"));
	static InputSolution mySoln = null;
	public static final boolean DEBUG = Boolean.valueOf(System.getProperty("DEBUG", "false"));
	public static final String INTERNAL_NAME = "edu/columbia/cs/psl/knarr/runtime/Symbolicator";
	static {

		// System.setOut(new PrintStream(System.out)
		// {
		// @Override
		// public void println(Object x) {
		// // TODO Auto-generated method stub
		// new Exception().printStackTrace();
		// super.println(x);
		// }
		// @Override
		// public void println(String x) {
		// // TODO Auto-generated method stub
		// new Exception().printStackTrace();
		// super.println(x);
		// }
		// });
		// Try to get a solution
		// try {
		// ObjectOutputStream oos = new
		// ObjectOutputStream(getSocket().getOutputStream());
		// oos.writeObject("REGISTER");
		// ObjectInputStream ois = new
		// ObjectInputStream(getSocket().getInputStream());
		// mySoln = (InputSolution) ois.readObject();
		// if (DEBUG)
		// System.out.println("Received input set: " + mySoln.varMapping);
		// serverConnection.close();
		// serverConnection = null;
		// } catch (Exception ex) {
		// ex.printStackTrace();
		// }
	}

	public static Socket getSocket() {
		if (serverConnection != null)
			return serverConnection;
		try {
			serverConnection = new Socket(SERVER_HOST, SERVER_PORT);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return serverConnection;
	}

	private static final BVConstant FF_32 = new BVConstant(0xFF, 32);
	private static final BVConstant FFFF_32 = new BVConstant(0xFFFF, 32);
	private static final Expression FFFFFF00_32 = new UnaryOperation(Operator.BIT_NOT, new BVConstant(0x000000FF, 32));
	private static final Expression FFFF0000_32 = new UnaryOperation(Operator.BIT_NOT, new BVConstant(0x0000FFFF, 32));

	private static void collectArrayLenConstraints() {
		// for (Expression v : TaintUtils.arraysHash.values()) {
		// SymbolicInteger length = (SymbolicInteger) v.related;
		// // SymbolicInteger length = (SymbolicInteger) lengthVal.expression;
		// TaintUtils.getCurPC()._addDet(Comparator.GE, length, length._min);
		// }
		System.out.println("Warning - array length constraints disabled?");
	}
	
	private static int n = 0;

	public static ArrayList<SimpleEntry<String, Object>> dumpConstraints() {
		return dumpConstraints(null);
	}

	public static synchronized ArrayList<SimpleEntry<String, Object>> dumpConstraints(String name) {
		collectArrayLenConstraints();
			// if (DEBUG)
//			System.out.println("Constraints: " + PathUtils.getCurPC().constraints);
		if(PathUtils.getCurPC().constraints == null)
			return null;

		try (ObjectOutputStream oos = new ObjectOutputStream(getSocket().getOutputStream())) {
			ObjectInputStream ois = new ObjectInputStream(getSocket().getInputStream());

//			{
//				Expression exp = PathUtils.getCurPC().constraints;
//				long n = 0;
//				while (true) {
//					if (exp instanceof Operation) {
//						n++;
//						Operation op = (Operation) exp;
//						if (op.getOperator() == Operator.AND) {
//							Expression e0 = op.getOperand(0);
//							Expression e1 = op.getOperand(1);
//							exp = e0;
//						} else {
//							break;
//						}
//					}
//				}
//
//				oos.writeLong(n);
//			    exp = PathUtils.getCurPC().constraints;
//			    while (true) {
//					if (exp instanceof Operation) {
//						Operation op = (Operation) exp;
//						if (op.getOperator() == Operator.AND) {
//							Expression e0 = op.getOperand(0);
//							Expression e1 = op.getOperand(1);
//							oos.writeObject(e1);
//							exp = e0;
//						} else {
//							oos.writeObject(op);
//							break;
//						}
//					}
//				}
//			}
			oos.writeObject(PathUtils.getCurPC().constraints);

			// Solve constraints?
			oos.writeBoolean(true);

			// Dump constraints to file?
			oos.writeObject(name != null ? new File(name + ".dat") : null);

            // Coverage, if any
			Coverage.instance.thisCount = Coverage.count;
			oos.writeObject(Coverage.instance);

			n++;
			ArrayList<SimpleEntry<String, Object>> solution = (ArrayList<SimpleEntry<String,Object>>) ois.readObject();
//			System.out.println("Solution received: " + solution);
			byte[] array = new byte[solution.size()];
			int i = 0;
			boolean found = false;
			for (Entry<String, Object> e: solution) {
				if (!e.getKey().startsWith("autoVar_"))
					break;
				if (!found && e.getKey().equals(firstLabel))
					found = true;
				else if (!found)
					continue;
				Integer b = (Integer) e.getValue();
				if (b == null)
					break;

				array[i++] = b.byteValue();
			}
			System.out.println(new String(array, StandardCharsets.UTF_8));

			// Reset
			reset();

			oos.close();
			return solution;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (StackOverflowError e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void reset() {
		PathUtils.getCurPC().constraints = null;
		PathUtils.getCurPC().size = 0;
		serverConnection = null;
		firstLabel = null;
		TaintListener.arrayNames.clear();
		StringUtils.stringName = 0;
		PathUtils.usedLabels.clear();
		Coverage.instance.reset();
		autoLblr.set(0);
		Coverage.count = 0;

		for (Taint[] b : TaintListener.symbolizedArrays)
			for (int j = 0 ; j < b.length ; j++)
				b[j] = null;

		TaintListener.symbolizedArrays.clear();

		for (String s : StringUtils.symbolizedStrings) {
//			s.valuePHOSPHOR_TAG = null;
			s.valuePHOSPHOR_TAG.taints = null;
			s.valuePHOSPHOR_TAG.lengthTaint = null;
			s.PHOSPHOR_TAG = null;
		}

		StringUtils.symbolizedStrings.clear();
	}

	public static void solve() {

	}

	public static int symbolic(String label, int in) {
		return in;
	}

	public static short symbolic(String label, short in) {
		return in;
	}

	public static byte symbolic(String label, byte in) {
		return in;
	}

	public static boolean symbolic(String label, boolean in) {
		return in;
	}

	public static float symbolic(String label, float in) {
		return in;
	}

	public static long symbolic(String label, long in) {
		return in;
	}

	public static double symbolic(String label, double in) {
		return in;
	}

	public static char symbolic(String label, char in) {
		return in;
	}

	public static byte[] symbolic(String label, byte[] in) {
		return in;
	}

	public static String symbolic(String label, String in) {
		byte[] b = in.getBytes();
		in.valuePHOSPHOR_TAG.taints = new Taint[b.length];

		Expression t = new StringConstant("");
		
		for (int i = 0 ; i < b.length ; i++) {
			String name = label + "_c" + i;
			Expression c = new BVVariable(name, 32);
			in.valuePHOSPHOR_TAG.taints[i] = new ExpressionTaint(c);
			t = new BinaryOperation(Operator.CONCAT, t, c);
			PathUtils.getCurPC()._addDet(Operator.EQ, new BinaryOperation(Operator.BIT_AND, new BVVariable(name, 32), FFFFFF00_32), Operation.ZERO);
		}

		in.PHOSPHOR_TAG = new ExpressionTaint(t);

		return in;
	}

	public static int symbolic(int in) {
		return in;
	}

	public static int symbolic(int in, Taint tagToCopy) {
		return in;
	}

	public static boolean symbolic(boolean in, Taint tagToCopy){
		return in;
	}

	public static short symbolic(short in) {
		return in;
	}

	public static byte symbolic(byte in) {
		return in;
	}

	public static boolean symbolic(boolean in) {
		return in;
	}

	public static float symbolic(float in) {
		return in;
	}

	public static long symbolic(long in) {
		return in;
	}

	public static double symbolic(double in) {
		return in;
	}

	public static char symbolic(char in) {
		return in;
	}

	public static byte[] symbolic(byte[] in) {
		return in;
	}
	
	private static String firstLabel = null;

	public static String generateLabel() {
		String ret = "autoVar_" + autoLblr.getAndIncrement();
		
		if (firstLabel == null)
			firstLabel = ret;
		
		return ret;
	}

	public static int[] symbolic$$PHOSPHORTAGGED(String label, LazyIntArrayObjTags in_tags, int[] in) {
		// PathUtils.checkLabelAndInitJPF(label);
		// Expression exp = new
		// SymbolicInteger(label+"_length",0,Integer.MAX_VALUE);
		// symbolicLabels.put(exp, label);
		// in_tags.lengthTaint = new ExpressionTaint(exp);
		// exp.elements = new Expression[in.length];
		// for (int i = 0; i < in.length; i++) {
		// Expression tag = new SymbolicInteger(label + "_" + i, Byte.MIN_VALUE,
		// Byte.MAX_VALUE);
		// exp.elements[i] = tag;
		// }
		//
		// return in;
		throw new UnsupportedOperationException();
	}

	public static <T> T[] symbolic(T[] in) {
		return symbolic(generateLabel(), in);
	}

	@SuppressWarnings("unchecked")
	public static <T> T symbolic(T in) {
		return symbolic(generateLabel(), in);
	}

	public static TaintedIntWithObjTag symbolic$$PHOSPHORTAGGED(Taint<Expression> tag, int in, TaintedIntWithObjTag ret) {
		return symbolic$$PHOSPHORTAGGED(generateLabel(), tag, in, ret);
	}

	public static TaintedByteWithObjTag symbolic$$PHOSPHORTAGGED(Taint<Expression> tag, byte in, TaintedByteWithObjTag ret) {
		return symbolic$$PHOSPHORTAGGED(generateLabel(), tag, in, ret);
	}

	public static TaintedBooleanWithObjTag symbolic$$PHOSPHORTAGGED(Taint<Expression> tag, boolean in, TaintedBooleanWithObjTag ret) {
		return symbolic$$PHOSPHORTAGGED(generateLabel(), tag, in, ret);
	}

	public static TaintedCharWithObjTag symbolic$$PHOSPHORTAGGED(Taint<Expression> tag, char in, TaintedCharWithObjTag ret) {
		return symbolic$$PHOSPHORTAGGED(generateLabel(), tag, in, ret);
	}

	public static TaintedDoubleWithObjTag symbolic$$PHOSPHORTAGGED(Taint<Expression> tag, double in, TaintedDoubleWithObjTag ret) {
		return symbolic$$PHOSPHORTAGGED(generateLabel(), tag, in, ret);
	}

	public static TaintedFloatWithObjTag symbolic$$PHOSPHORTAGGED(Taint<Expression> tag, float in, TaintedFloatWithObjTag ret) {
		return symbolic$$PHOSPHORTAGGED(generateLabel(), tag, in, ret);
	}

	public static TaintedLongWithObjTag symbolic$$PHOSPHORTAGGED(Taint<Expression> tag, long in, TaintedLongWithObjTag ret) {
		return symbolic$$PHOSPHORTAGGED(generateLabel(), tag, in, ret);
	}

	public static TaintedShortWithObjTag symbolic$$PHOSPHORTAGGED(Taint<Expression> tag, short in, TaintedShortWithObjTag ret) {
		return symbolic$$PHOSPHORTAGGED(generateLabel(), tag, in, ret);
	}

	// public static byte[] symbolic$$PHOSPHORTAGGED(Expression in_tags_ignore,
	// byte[] in) {
	// return symbolic$$PHOSPHORTAGGED(generateLabel(), in_tags_ignore, in);
	// }
	//
	// public static char[] symbolic$$PHOSPHORTAGGED(Expression in_tags_ignore,
	// char[] in) {
	// return symbolic$$PHOSPHORTAGGED(generateLabel(), in_tags_ignore, in);
	// }

	// public static <T> T[] symbolic(String label, T[] in) {
	// PathUtils.checkLabelAndInitJPF(label);
	// PathUtils.registerTaintOnArray(in, label);
	// Expression exp = new
	// SymbolicInteger(label+"_length",0,Integer.MAX_VALUE); //for array length
	// ArrayHelper.setExpression(in, exp);
	// exp.elements = new Expression[in.length];
	// symbolicLabels.put(exp, label+"_length");
	// System.out.println("2");
	// for (int i = 0; i < in.length; i++) {
	// in[i] = symbolic(label + "_" + i, in[i]);
	// }
	// return in;
	// }

	public static Object getTaints(Object o) {
		if (o instanceof TaintedObjectWithObjTag) {
			if (o instanceof String)
				return ((String)o).valuePHOSPHOR_TAG;
            else
                return ((TaintedObjectWithObjTag)o).getPHOSPHOR_TAG();
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	public static <T> T symbolic(String label, T in) {
		if (in instanceof TaintedWithObjTag && ((TaintedWithObjTag) in).getPHOSPHOR_TAG() != null) {
			symbolicLabels.put((ExpressionTaint) ((TaintedWithObjTag) in).getPHOSPHOR_TAG(), label);
			return in;
		}
		if (in instanceof String) {
			if (mySoln != null && !mySoln.isUnconstrained)
				in = (T) mySoln.varMapping.get(label);
			// TaintedCharArrayWithObjTag z = symbolic$$PHOSPHORTAGGED(label,
			// null, str.toCharArray(), new TaintedCharArray());
			// str.valueINVIVO_PC_TAINTWithObjTag = z.taint;
			Expression taint = new StringVariable(label);
			((TaintedWithObjTag) in).setPHOSPHOR_TAG(taint);
			return in;
		}
		if (in instanceof TaintedWithObjTag) {
			PathUtils.checkLabelAndInitJPF(label);
			Expression taint = new BVVariable(label, 32);
			((TaintedWithObjTag) in).setPHOSPHOR_TAG(taint);
			return in;
		}
		throw new UnsupportedOperationException();
		// else if(in instanceof int[])
		// {
		// PathUtils.checkLabelAndInitJPF(label);
		// symbolic$$PHOSPHORTAGGED(label,null,(int[]) in);
		// // Expression exp = new SymbolicInteger(0, Integer.MAX_VALUE);
		// // exp.elements = new SymbolicInteger[((int[])in).length];
		// // ArrayHelper.setExpression(in, exp);
		// // symbolicLabels.put(exp, label);
		// return in;
		// }

	}

	static HashMap<ExpressionTaint, Object> symbolicLabels = new HashMap<ExpressionTaint, Object>();

	static ConcurrentHashMap<String, AtomicInteger> lblCounters = new ConcurrentHashMap<String, AtomicInteger>();

	public static final String symbolicString(String str, String lbl) {
//		if (!lblCounters.containsKey(lbl))
//			lblCounters.put(lbl, new AtomicInteger());
//		AtomicInteger i = lblCounters.get(lbl);
//		lbl = lbl + "_" + i.getAndIncrement();
//		PathUtils.checkLabelAndInitJPF(lbl);
		Expression ret = null;
		if (str == str.intern())
			str = new String(str);
		ret = new StringVariable(lbl);
		str.setPHOSPHOR_TAG(new ExpressionTaint(ret));
		return str;
	}

	public static final Expression generateExpression(String lbl, int sort) {
		if (!lblCounters.containsKey(lbl))
			lblCounters.put(lbl, new AtomicInteger());
		AtomicInteger i = lblCounters.get(lbl);
		lbl = lbl + "_" + i.getAndIncrement();
		PathUtils.checkLabelAndInitJPF(lbl);
		Expression ret = null;
		switch (sort) {
		case Type.OBJECT:
			ret = new IntVariable(lbl, 0, 1);
			break;
		case Type.ARRAY:
			throw new UnsupportedOperationException();
			// PathUtils.checkLabelAndInitJPF(lbl);
			// Expression exp = new
			// SymbolicInteger(lbl+"_length",0,Integer.MAX_VALUE); //for array
			// length
			// return exp;
			// ArrayHelper.setExpression(in, exp);
			// exp.elements = new Expression[in.length];
			// symbolicLabels.put(exp, label+"_length");
			// for (int i = 0; i < in.length; i++) {
			// in[i] = symbolic(label + "_" + i, in[i]);
			// }
		case Type.BOOLEAN:
			ret = new IntVariable(lbl, 0, 1);
			break;
		case Type.BYTE:
			ret = new BinaryOperation(Operator.BIT_AND, new BVVariable(lbl, 32), FF_32);
			break;
		case Type.CHAR:
			ret = new BinaryOperation(Operator.BIT_AND, new BVVariable(lbl, 32), FFFF_32);
			break;
		case Type.DOUBLE:
			ret = new RealVariable(lbl, Double.MIN_VALUE, Double.MAX_VALUE);
			break;
		case Type.FLOAT:
			ret = new RealVariable(lbl, Double.valueOf(Float.MIN_VALUE), Double.valueOf(Float.MAX_VALUE));
			break;
		case Type.INT:
			ret = new BVVariable(lbl, 32);
			break;
		case Type.LONG:
			ret = new BVVariable(lbl, 64);
			break;
		case Type.SHORT:
			ret = new BinaryOperation(Operator.BIT_AND, new BVVariable(lbl, 32), FFFF_32);
			break;
		default:
			throw new UnsupportedOperationException();
		}
		// symbolicLabels.put(ret, lbl);
		return ret;
	}

	static AtomicInteger autoLblr = new AtomicInteger();

	public static TaintedIntWithObjTag symbolic$$PHOSPHORTAGGED(String label, Taint<Expression> tag, int in, TaintedIntWithObjTag ret) {
		PathUtils.checkLabelAndInitJPF(label);
		ret.val = in;
		if (mySoln != null && !mySoln.isUnconstrained)
			ret.val = (Integer) mySoln.varMapping.get(label);
		ret.taint = new ExpressionTaint(new BVVariable((String) label, 32));
		symbolicLabels.put((ExpressionTaint) ret.taint, label);
		return ret;
	}

	public static TaintedIntWithObjTag symbolic$$PHOSPHORTAGGED(Taint<Expression> tag, int in, Taint<Expression> tagToCopy, TaintedIntWithObjTag ret) {
		//TODO we are purposely propagating taints even though the constraint will be wrong. Do something to stop Z3 from solving this since the result is meaningless.
		ret.val = in;
		ret.taint = tagToCopy;
		Expression exp = (Expression) ret.taint.getSingleLabel();
		exp = new UnaryOperation(Operator.INDEXOFSTRING, exp); //temp hack to make sure we never solve a constraint involving this
		ret.taint.setSingleLabel(exp);
		return ret;
	}

	public static TaintedBooleanWithObjTag symbolic$$PHOSPHORTAGGED(Taint<Expression> tag, boolean in, Taint<Expression> tagToCopy, TaintedBooleanWithObjTag ret) {
		//TODO we are purposely propagating taints even though the constraint will be wrong. Do something to stop Z3 from solving this since the result is meaningless.
		ret.val = in;
		ret.taint = tagToCopy;
		Expression exp = (Expression) ret.taint.getSingleLabel();
		exp = new UnaryOperation(Operator.INDEXOFSTRING, exp); //temp hack to make sure we never solve a constraint involving this
		ret.taint.setSingleLabel(exp);
		return ret;
	}

	public static TaintedByteWithObjTag symbolic$$PHOSPHORTAGGED(String label, Taint<Expression> tag, byte in, TaintedByteWithObjTag ret) {
		PathUtils.checkLabelAndInitJPF(label);
		ret.val = in;
		if (mySoln != null && !mySoln.isUnconstrained)
			ret.val = ((Integer) mySoln.varMapping.get(label)).byteValue();
		ret.taint = new ExpressionTaint(new BVVariable((String) label, 32));
        Expression pos = new BinaryOperation(Operator.EQ, new BinaryOperation(Operator.BIT_AND, new BVVariable((String) label, 32), FFFFFF00_32), Operation.ZERO);
		Expression neg = new BinaryOperation(Operator.EQ, new BinaryOperation(Operator.BIT_AND, new BVVariable((String) label, 32), FFFFFF00_32), FFFFFF00_32);
		PathUtils.getCurPC()._addDet(Operator.OR, pos, neg);
		symbolicLabels.put((ExpressionTaint) ret.taint, label);
		return ret;
	}

	public static TaintedBooleanWithObjTag symbolic$$PHOSPHORTAGGED(String label, Taint<Expression> tag, boolean in, TaintedBooleanWithObjTag ret) {
		PathUtils.checkLabelAndInitJPF(label);
		ret.val = in;
		if (mySoln != null && !mySoln.isUnconstrained)
			ret.val = ((Integer) mySoln.varMapping.get(label)).intValue() == 1;
		ret.taint = new ExpressionTaint(new IntVariable((String) label, 0, 1));
		symbolicLabels.put((ExpressionTaint) ret.taint, label);
		return ret;
	}

	public static TaintedCharWithObjTag symbolic$$PHOSPHORTAGGED(String label, Taint<Expression> tag, char in, TaintedCharWithObjTag ret) {
		PathUtils.checkLabelAndInitJPF(label);
		ret.val = in;
		if (mySoln != null && !mySoln.isUnconstrained)
			ret.val = (char) ((Integer) mySoln.varMapping.get(label)).intValue();
		ret.taint = new ExpressionTaint(new BVVariable((String) label, 32));
		PathUtils.getCurPC()._addDet(Operator.EQ, new BinaryOperation(Operator.BIT_AND, new BVVariable((String) label, 32), FFFF0000_32), Operation.ZERO);
		symbolicLabels.put((ExpressionTaint) ret.taint, label);
		return ret;
	}

	public static TaintedDoubleWithObjTag symbolic$$PHOSPHORTAGGED(String label, Taint<Expression> tag, double in, TaintedDoubleWithObjTag ret) {
		PathUtils.checkLabelAndInitJPF(label);
		ret.val = in;
		if (mySoln != null && !mySoln.isUnconstrained)
			ret.val = ((Double) mySoln.varMapping.get(label)).doubleValue();
		ret.taint = new ExpressionTaint(new RealVariable((String) label, Double.MIN_VALUE, Double.MAX_VALUE));
		symbolicLabels.put((ExpressionTaint) ret.taint, label);
		return ret;
	}

	public static TaintedFloatWithObjTag symbolic$$PHOSPHORTAGGED(String label, Taint<Expression> tag, float in, TaintedFloatWithObjTag ret) {
		PathUtils.checkLabelAndInitJPF(label);
		ret.val = in;
		if (mySoln != null && !mySoln.isUnconstrained)
			ret.val = ((Double) mySoln.varMapping.get(label)).floatValue();
		ret.taint = new ExpressionTaint(new RealVariable((String) label, Double.valueOf(Float.MIN_VALUE), Double.valueOf(Float.MAX_VALUE)));
		symbolicLabels.put((ExpressionTaint) ret.taint, label);
		return ret;
	}

	public static TaintedLongWithObjTag symbolic$$PHOSPHORTAGGED(String label, Taint<Expression> tag, long in, TaintedLongWithObjTag ret) {
		PathUtils.checkLabelAndInitJPF(label);
		ret.val = in;
		if (mySoln != null && !mySoln.isUnconstrained)
			ret.val = ((Long) mySoln.varMapping.get(label)).longValue();
		ret.taint = new ExpressionTaint(new BVVariable((String) label, 64));
		symbolicLabels.put((ExpressionTaint) ret.taint, label);
		return ret;
	}

	public static TaintedShortWithObjTag symbolic$$PHOSPHORTAGGED(String label, Taint<Expression> tag, short in, TaintedShortWithObjTag ret) {
		PathUtils.checkLabelAndInitJPF(label);
		ret.val = in;
		if (mySoln != null && !mySoln.isUnconstrained)
			ret.val = ((Short) mySoln.varMapping.get(label)).shortValue();
		ret.taint = new ExpressionTaint(new BVVariable((String) label, 32));
		PathUtils.getCurPC()._addDet(Operator.EQ, new BinaryOperation(Operator.BIT_AND, new BVVariable((String) label, 32), FFFF0000_32), Operation.ZERO);
		symbolicLabels.put((ExpressionTaint) ret.taint, label);
		return ret;
	}

	public static Expression getExpression(int in) {
		throw new UnsupportedOperationException("You must instrument your code before running it.");
	}

	public static Expression getExpression(long in) {
		throw new UnsupportedOperationException("You must instrument your code before running it.");
	}

	public static Expression getExpression(byte in) {
		throw new UnsupportedOperationException("You must instrument your code before running it.");
	}

	public static Expression getExpression(char in) {
		throw new UnsupportedOperationException("You must instrument your code before running it.");
	}

	public static Expression getExpression(double in) {
		throw new UnsupportedOperationException("You must instrument your code before running it.");
	}

	public static Expression getExpression(float in) {
		throw new UnsupportedOperationException("You must instrument your code before running it.");
	}

	public static Expression getExpression(boolean in) {
		throw new UnsupportedOperationException("You must instrument your code before running it.");
	}

	public static Expression getExpression(Object in) {
		throw new UnsupportedOperationException("You must instrument your code before running it.");
	}

	public static Expression getExpression$$PHOSPHORTAGGED(Taint<Expression> exp, int in) {
		if (exp != null)
			return exp.getSingleLabel();
		return null;
	}

	public static Expression getExpression$$PHOSPHORTAGGED(Taint<Expression> exp, float in) {
		if (exp != null)
			return exp.getSingleLabel();
		return null;
	}

	public static Expression getExpression$$PHOSPHORTAGGED(Taint<Expression> exp, double in) {
		if (exp != null)
			return exp.getSingleLabel();
		return null;
	}

	public static Expression getExpression$$PHOSPHORTAGGED(Taint<Expression> exp, long in) {
		if (exp != null)
			return exp.getSingleLabel();
		return null;
	}

	public static Expression getExpression$$PHOSPHORTAGGED(Taint<Expression> exp, boolean in) {
		if (exp != null)
			return exp.getSingleLabel();
		return null;
	}

	public static Expression getExpression$$PHOSPHORTAGGED(Taint<Expression> exp, byte in) {
		if (exp != null)
			return exp.getSingleLabel();
		return null;
	}

	public static Expression getExpression$$PHOSPHORTAGGED(Taint<Expression> exp, char in) {
		if (exp != null)
			return exp.getSingleLabel();
		return null;
	}

	public static Expression getExpression$$PHOSPHORTAGGED(Object obj) {
		if (obj instanceof TaintedWithObjTag)
			return (Expression) ((TaintedWithObjTag) obj).getPHOSPHOR_TAG();
		return null;
	}

	public static LazyByteArrayObjTags symbolic$$PHOSPHORTAGGED(LazyByteArrayObjTags t, byte[] b)
	{
      t.val = new byte[b.length];
      if(t.taints == null)
          t.taints = new Taint[b.length];
      for(int i =0 ; i < b.length; i++) {
          t.taints[i] = new ExpressionTaint(new BVVariable(generateLabel(), 8));
          t.val[i] = b[i];
      }
      return t;
	}
}
