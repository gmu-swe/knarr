package edu.gmu.swe.knarr.runtime;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntVariable;
import za.ac.sun.cs.green.expr.RealVariable;
import za.ac.sun.cs.green.expr.StringVariable;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type;
import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.struct.LazyIntArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.TaintedBooleanWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedByteWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedCharWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedDoubleWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedFloatWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedIntWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedLongWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedShortWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedWithObjTag;


public class Symbolicator {
	static Socket serverConnection;
	static String SERVER_HOST = System.getProperty("SATServer", "127.0.0.1");
	static int SERVER_PORT = Integer.valueOf(System.getProperty("SATPort", "9090"));
	static InputSolution mySoln = null;
	public static final boolean DEBUG = Boolean.valueOf(System.getProperty("DEBUG", "false"));
	public static final String INTERNAL_NAME = "edu/columbia/cs/psl/knarr/runtime/Symbolicator";
	static {
		
//		System.setOut(new PrintStream(System.out)
//		{
//			@Override
//			public void println(Object x) {
//				// TODO Auto-generated method stub
//				new Exception().printStackTrace();
//				super.println(x);
//			}
//			@Override
//			public void println(String x) {
//				// TODO Auto-generated method stub
//				new Exception().printStackTrace();
//				super.println(x);
//			}
//		});
		//Try to get a solution
//		try {
//			ObjectOutputStream oos = new ObjectOutputStream(getSocket().getOutputStream());
//			oos.writeObject("REGISTER");
//			ObjectInputStream ois = new ObjectInputStream(getSocket().getInputStream());
//			mySoln = (InputSolution) ois.readObject();
//			if (DEBUG)
//				System.out.println("Received input set: " + mySoln.varMapping);
//			serverConnection.close();
//			serverConnection = null;
//		} catch (Exception ex) {
//			ex.printStackTrace();
//		}
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

	private static void collectArrayLenConstraints() {
//		for (Expression v : TaintUtils.arraysHash.values()) {
//			SymbolicInteger length = (SymbolicInteger) v.related;
////			SymbolicInteger length = (SymbolicInteger) lengthVal.expression;
//			TaintUtils.getCurPC()._addDet(Comparator.GE, length, length._min);
//		}
		System.out.println("Warning - array length constraints disabled?");
	}

	public static void dumpConstraints() {
		ObjectOutputStream oos;
		collectArrayLenConstraints();
//		try 
		{
//			if (DEBUG)
				System.out.println("Numeric constraints: " + ((PathConditionWrapper) PathUtils.getCurPC()).constraints);
//				System.out.println("String constraints: " + ((PathConditionWrapper) PathUtils.getCurPC()).spc);
			//			System.out.println(TaintUtils.getCurPC().spc);
//			oos = new ObjectOutputStream(getSocket().getOutputStream());
//			oos.writeObject(PathUtils.getCurPC());
			//			oos.close();
			//			ObjectInputStream ois = new ObjectInputStream(getSocket().getInputStream());
			//			HashMap<String, Serializable> solution = (HashMap<String, Serializable>) ois.readObject();
			//			System.out.println("Solution received: " + solution);
		} 
//		catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
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

	public static int symbolic(int in) {
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
	public static String generateLabel()
	{
		return "autoVar_"+autoLblr.getAndIncrement();
	}
	public static int[] symbolic$$PHOSPHORTAGGED(String label, LazyIntArrayObjTags in_tags, int[] in) {
//		PathUtils.checkLabelAndInitJPF(label);
//		Expression exp = new SymbolicInteger(label+"_length",0,Integer.MAX_VALUE);
//		symbolicLabels.put(exp, label);
//		in_tags.lengthTaint = new ExpressionTaint(exp);
//		exp.elements = new Expression[in.length];
//		for (int i = 0; i < in.length; i++) {
//			Expression tag = new SymbolicInteger(label + "_" + i, Byte.MIN_VALUE, Byte.MAX_VALUE);
//			exp.elements[i] = tag;
//		}
//		
//		return in;
		throw new UnsupportedOperationException();
	}


	public static <T> T[] symbolic(T[] in) {
		return symbolic(generateLabel(),in);
	}

	@SuppressWarnings("unchecked")
	public static <T> T symbolic(T in) {
		return symbolic(generateLabel(),in);
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
	
//	public static byte[] symbolic$$PHOSPHORTAGGED(Expression in_tags_ignore, byte[] in) {
//		return symbolic$$PHOSPHORTAGGED(generateLabel(), in_tags_ignore, in);
//	}
//
//	public static char[] symbolic$$PHOSPHORTAGGED(Expression in_tags_ignore, char[] in) {
//		return symbolic$$PHOSPHORTAGGED(generateLabel(), in_tags_ignore, in);
//	}

//	public static <T> T[] symbolic(String label, T[] in) {
//		PathUtils.checkLabelAndInitJPF(label);
//		PathUtils.registerTaintOnArray(in, label);
//		Expression exp = new SymbolicInteger(label+"_length",0,Integer.MAX_VALUE); //for array length
//		ArrayHelper.setExpression(in, exp);
//		exp.elements = new Expression[in.length];
//		symbolicLabels.put(exp, label+"_length");
//		System.out.println("2");
//		for (int i = 0; i < in.length; i++) {
//			in[i] = symbolic(label + "_" + i, in[i]);
//		}
//		return in;
//	}

	@SuppressWarnings("unchecked")
	public static <T> T symbolic(String label, T in) {
		if (in instanceof TaintedWithObjTag && ((TaintedWithObjTag) in).getPHOSPHOR_TAG() != null) {
			symbolicLabels.put((ExpressionTaint) ((TaintedWithObjTag) in).getPHOSPHOR_TAG(), label);
			return in;
		}
		if (in instanceof String) {
			if (mySoln != null && !mySoln.isUnconstrained)
				in = (T) mySoln.varMapping.get(label);
			//			TaintedCharArrayWithObjTag z = symbolic$$PHOSPHORTAGGED(label, null, str.toCharArray(), new TaintedCharArray());
			//			str.valueINVIVO_PC_TAINTWithObjTag = z.taint;
			Expression taint = new StringVariable(label);
			((TaintedWithObjTag) in).setPHOSPHOR_TAG(taint);
			return in;
		}
		if (in instanceof TaintedWithObjTag) {
			PathUtils.checkLabelAndInitJPF(label);
			Expression taint = new IntVariable(label,Integer.MIN_VALUE,Integer.MAX_VALUE);
			((TaintedWithObjTag) in).setPHOSPHOR_TAG(taint);
			return in;
		}
		throw new UnsupportedOperationException();
//		else if(in instanceof int[])
//		{
//			PathUtils.checkLabelAndInitJPF(label);
//			symbolic$$PHOSPHORTAGGED(label,null,(int[]) in);
////			Expression exp = new SymbolicInteger(0, Integer.MAX_VALUE);
////			exp.elements = new SymbolicInteger[((int[])in).length];
////			ArrayHelper.setExpression(in, exp);
////			symbolicLabels.put(exp, label);
//			return in;
//		}

	}

	static HashMap<ExpressionTaint, Object> symbolicLabels = new HashMap<ExpressionTaint, Object>();

	static ConcurrentHashMap<String, AtomicInteger> lblCounters = new ConcurrentHashMap<String, AtomicInteger>();
	
	public static final String symbolicString(String str, String lbl)
	{
		if(!lblCounters.containsKey(lbl))
			lblCounters.put(lbl, new AtomicInteger());
		AtomicInteger i = lblCounters.get(lbl);
		lbl = lbl + "_"+i.getAndIncrement();
		PathUtils.checkLabelAndInitJPF(lbl);
		Expression ret = null;
		if(str == str.intern())
			str = new String(str);
		ret = new StringVariable(lbl);
		str.setPHOSPHOR_TAG(new ExpressionTaint(ret));
		return str;
	}
	public static final Expression generateExpression(String lbl, int sort)
	{
		if(!lblCounters.containsKey(lbl))
			lblCounters.put(lbl, new AtomicInteger());
		AtomicInteger i = lblCounters.get(lbl);
		lbl = lbl + "_"+i.getAndIncrement();
		PathUtils.checkLabelAndInitJPF(lbl);
		Expression ret = null;
		switch(sort)
		{
		case Type.OBJECT:
			ret = new IntVariable(lbl, 0, 1);
			break;
		case Type.ARRAY:
			throw new UnsupportedOperationException();
//			PathUtils.checkLabelAndInitJPF(lbl);
//			Expression exp = new SymbolicInteger(lbl+"_length",0,Integer.MAX_VALUE); //for array length
//			return exp;
//			ArrayHelper.setExpression(in, exp);
//			exp.elements = new Expression[in.length];
//			symbolicLabels.put(exp, label+"_length");
//			for (int i = 0; i < in.length; i++) {
			//				in[i] = symbolic(label + "_" + i, in[i]);
			//			}
		case Type.BOOLEAN:
			ret = new IntVariable(lbl, 0, 1);
			break;
		case Type.BYTE:
			ret = new IntVariable(lbl, Integer.valueOf(Byte.MIN_VALUE), Integer.valueOf(Byte.MAX_VALUE));
			break;
		case Type.CHAR:
			ret = new IntVariable(lbl, Integer.valueOf(Character.MIN_VALUE), Integer.valueOf(Character.MAX_VALUE));
			break;
		case Type.DOUBLE:
			ret = new RealVariable(lbl, Double.MIN_VALUE, Double.MAX_VALUE);
			break;
		case Type.FLOAT:
			ret = new RealVariable(lbl, Double.valueOf(Float.MIN_VALUE), Double.valueOf(Float.MAX_VALUE));
			break;
		case Type.INT:
			ret = new IntVariable(lbl, Integer.MIN_VALUE, Integer.MAX_VALUE);
			break;
		case Type.LONG:
			ret = new IntVariable(lbl, Integer.MIN_VALUE, Integer.MAX_VALUE);
			break;
		case Type.SHORT:
			ret = new IntVariable(lbl, Integer.valueOf(Short.MIN_VALUE), Integer.valueOf(Short.MAX_VALUE));
			break;
		default:
			throw new UnsupportedOperationException();
		}
//		symbolicLabels.put(ret, lbl);
		return ret;
	}
	static AtomicInteger autoLblr = new AtomicInteger();
	public static TaintedIntWithObjTag symbolic$$PHOSPHORTAGGED(String label, Taint<Expression> tag, int in, TaintedIntWithObjTag ret) {
		PathUtils.checkLabelAndInitJPF(label);
		ret.val = in;
		if (mySoln != null && !mySoln.isUnconstrained)
			ret.val = (Integer) mySoln.varMapping.get(label);
		ret.taint = new ExpressionTaint(new IntVariable((String) label, Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(Integer.MAX_VALUE)));
		symbolicLabels.put((ExpressionTaint) ret.taint, label);
		return ret;
	}

	public static TaintedByteWithObjTag symbolic$$PHOSPHORTAGGED(String label, Taint<Expression> tag, byte in, TaintedByteWithObjTag ret) {
		PathUtils.checkLabelAndInitJPF(label);
		ret.val = in;
		if (mySoln != null && !mySoln.isUnconstrained)
			ret.val = ((Integer) mySoln.varMapping.get(label)).byteValue();
		ret.taint = new ExpressionTaint(new IntVariable((String) label, Integer.valueOf(Byte.MIN_VALUE), Integer.valueOf(Byte.MAX_VALUE)));
		symbolicLabels.put((ExpressionTaint) ret.taint, label);
		return ret;
	}

	public static TaintedBooleanWithObjTag symbolic$$PHOSPHORTAGGED(String label, Taint<Expression> tag, boolean in, TaintedBooleanWithObjTag ret) {
		PathUtils.checkLabelAndInitJPF(label);
		ret.val = in;
		if (mySoln != null && !mySoln.isUnconstrained)
			ret.val = ((Integer) mySoln.varMapping.get(label)).intValue() == 1;
		ret.taint = new ExpressionTaint(new IntVariable((String) label, 0,1));
		symbolicLabels.put((ExpressionTaint) ret.taint, label);
		return ret;
	}

	public static TaintedCharWithObjTag symbolic$$PHOSPHORTAGGED(String label, Taint<Expression> tag, char in, TaintedCharWithObjTag ret) {
		PathUtils.checkLabelAndInitJPF(label);
		ret.val = in;
		if (mySoln != null && !mySoln.isUnconstrained)
			ret.val = (char) ((Integer) mySoln.varMapping.get(label)).intValue();
		ret.taint = new ExpressionTaint(new IntVariable((String) label, Integer.valueOf(Character.MIN_VALUE), Integer.valueOf(Character.MAX_VALUE)));
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
		ret.taint = new ExpressionTaint(new IntVariable((String) label, Integer.valueOf(Integer.MIN_VALUE), Integer.valueOf(Integer.MAX_VALUE)));
		symbolicLabels.put((ExpressionTaint) ret.taint, label);
		return ret;
	}

	public static TaintedShortWithObjTag symbolic$$PHOSPHORTAGGED(String label, Taint<Expression> tag, short in, TaintedShortWithObjTag ret) {
		PathUtils.checkLabelAndInitJPF(label);
		ret.val = in;
		if (mySoln != null && !mySoln.isUnconstrained)
			ret.val = ((Short) mySoln.varMapping.get(label)).shortValue();
		ret.taint = new ExpressionTaint(new IntVariable((String) label, Integer.valueOf(Short.MIN_VALUE), Integer.valueOf(Short.MAX_VALUE)));
		symbolicLabels.put((ExpressionTaint) ret.taint, label);
		return ret;
	}
	
	public static Expression getExpression(int in)
	{
		throw new UnsupportedOperationException("You must instrument your code before running it.");
	}
	public static Expression getExpression(long in)
	{
		throw new UnsupportedOperationException("You must instrument your code before running it.");
	}
	public static Expression getExpression(byte in)
	{
		throw new UnsupportedOperationException("You must instrument your code before running it.");
	}
	public static Expression getExpression(char in)
	{
		throw new UnsupportedOperationException("You must instrument your code before running it.");
	}
	public static Expression getExpression(double in)
	{
		throw new UnsupportedOperationException("You must instrument your code before running it.");
	}
	public static Expression getExpression(float in)
	{
		throw new UnsupportedOperationException("You must instrument your code before running it.");
	}
	public static Expression getExpression(boolean in)
	{
		throw new UnsupportedOperationException("You must instrument your code before running it.");
	}
	public static Expression getExpression(Object in)
	{
		throw new UnsupportedOperationException("You must instrument your code before running it.");
	}
	public static Expression getExpression$$PHOSPHORTAGGED(Taint<Expression> exp, int in)
	{
		if (exp != null)
			return exp.lbl;
		return null;
	}

	public static Expression getExpression$$PHOSPHORTAGGED(Taint<Expression> exp, float in) {
		if (exp != null)
			return exp.lbl;
		return null;
	}

	public static Expression getExpression$$PHOSPHORTAGGED(Taint<Expression> exp, double in) {
		if (exp != null)
			return exp.lbl;
		return null;
	}

	public static Expression getExpression$$PHOSPHORTAGGED(Taint<Expression> exp, long in) {
		if (exp != null)
			return exp.lbl;
		return null;
	}

	public static Expression getExpression$$PHOSPHORTAGGED(Taint<Expression> exp, boolean in) {
		if (exp != null)
			return exp.lbl;
		return null;
	}

	public static Expression getExpression$$PHOSPHORTAGGED(Taint<Expression> exp, byte in) {
		if (exp != null)
			return exp.lbl;
		return null;
	}

	public static Expression getExpression$$PHOSPHORTAGGED(Taint<Expression> exp, char in) {
		if (exp != null)
			return exp.lbl;
		return null;
	}

	public static Expression getExpression$$PHOSPHORTAGGED(Object obj)
	{
		if(obj instanceof TaintedWithObjTag)
			return (Expression) ((TaintedWithObjTag) obj).getPHOSPHOR_TAG();
		return null;
	}
}
