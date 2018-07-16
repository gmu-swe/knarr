package edu.gmu.swe.knarr.runtime;

import java.util.Locale;

import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.struct.LazyArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.LazyCharArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.TaintedBooleanWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedCharWithObjTag;
import za.ac.sun.cs.green.expr.BVVariable;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.StringConstant;
import za.ac.sun.cs.green.expr.StringVariable;

public class StringUtils {
	public static void setTaints(LazyCharArrayObjTags tags, Object tag) {
//		if (tags.val.length == 0)
//			return;
//		tags.taints = new Taint[tags.val.length];
//		for (int i = 0; i < tags.val.length; i++)
//			tags.taints[i] = (Taint) tag;
	}

//		if (inStringClass) {
//			System.out.println("instr... " + this.name + " enter");
//			if (this.name.equals("startsWith$$PHOSPHORTAGGED"))
//				handleStartsWith(mv, true);
//			else if (this.name.equals("toUpperCase"))
//				handleUpperLower(mv, STR_UCASE);
//			else if (this.name.equals("toLowerCase"))
//				handleUpperLower(mv, STR_LCASE);
//			else if (this.name.equals("concat"))
//				handleConcat(mv);
//			else if (this.name.equals("trim"))
//				handleTrim(mv);
//			else if (this.name.equals("length$$PHOSPHORTAGGED"))
//				handleLength(mv);
//			else if (this.name.equals("equals$$PHOSPHORTAGGED"))
//				handleEquals(mv);
//			else if (this.name.equals("isEmpty$$PHOSPHORTAGGED"))
//				handleEmpty(mv);
//			else if (this.name.equals("split"))
//				handleSplit(mv);
//			else if (this.name.equals("replaceAll"))
//				handleReplaceAll(mv);
//			else if (this.name.equals("replace"))
//				handleReplace(mv);
//			else if (this.name.equals("substring$$PHOSPHORTAGGED"))
//				handleSubstring(mv);
//			else if(this.name.equals("charAt$$PHOSPHORTAGGED"))
//				handleCharAt(mv);
//			 // TODO: make sustring not break jvm
//		}
	
	public static int stringName;
	
	private static StringVariable getFreshStringVar() {
		return new StringVariable("string_var_" + (stringName++));
	}

	public static void registerNewString(String s, LazyArrayObjTags srcTags, Object src, Taint offset_t, int offset, Taint len_t, int len) {
		if (srcTags != null && srcTags.taints != null) {
			StringVariable var = getFreshStringVar();
			Expression exp = var;
			char[] arr = s.toCharArray();
			for (int i = offset ; i < len ; i++) {
				Taint t = srcTags.taints[i];
				if (t == null) {
					exp = new Operation(Operator.CONCAT, exp, new IntConstant(arr[i]));
					s.valuePHOSPHOR_TAG.taints[i] = new ExpressionTaint(new BVVariable(var + "_" + i, 32));
				} else {
					exp = new Operation(Operator.CONCAT, exp, (Expression) t.lbl);
				}
			}
			
			s.PHOSPHOR_TAG = new ExpressionTaint(exp);
		}
	}
	
	public static void startsWith$$PHOSPHORTAGGED(TaintedBooleanWithObjTag ret, String s, String pref, Taint tStart, int start, TaintedBooleanWithObjTag ret2) {
		Expression tPref;
		if (s.PHOSPHOR_TAG != null && s.PHOSPHOR_TAG.lbl != null) {
			if (pref.PHOSPHOR_TAG != null && pref.PHOSPHOR_TAG.lbl != null)
				tPref = (Expression)pref.PHOSPHOR_TAG.lbl;
			else
				tPref = new StringConstant(pref);
			

			if (tStart != null)
				throw new UnsupportedOperationException();
			
			Expression tS = (Expression) s.PHOSPHOR_TAG.lbl;
			if (start > 0)
				throw new UnsupportedOperationException();
			
			ret.taint = new ExpressionTaint(new Operation(Operator.STARTSWITH, tPref, tS));
		}
	}
	
	public static void startsWith$$PHOSPHORTAGGED(TaintedBooleanWithObjTag ret, String s, String pref, TaintedBooleanWithObjTag ret2) {
		startsWith$$PHOSPHORTAGGED(ret, s, pref, null, 0, ret2);
	}
	

	public static void equals$$PHOSPHORTAGGED(TaintedBooleanWithObjTag ret, String s, Object o, TaintedBooleanWithObjTag ret2) {
		if (o != null && o instanceof String && s.PHOSPHOR_TAG != null && s.PHOSPHOR_TAG.lbl != null) {
			String s2 = (String)o;

			Expression tO;
			if (s2.PHOSPHOR_TAG != null && s2.PHOSPHOR_TAG.lbl != null)
				tO = (Expression)s2.PHOSPHOR_TAG.lbl;
			else
				tO = new StringConstant(s2);
			
			Expression tS = (Expression) s.PHOSPHOR_TAG.lbl;
			ret.taint = new ExpressionTaint(new Operation(Operator.EQUALS, tS, tO));
		}
	}
	
	
	public static void charAt$$PHOSPHORTAGGED(TaintedCharWithObjTag ret, String s, Taint tIndex, int index, TaintedCharWithObjTag ret2) {
		if (s.PHOSPHOR_TAG != null && s.PHOSPHOR_TAG.lbl != null) {
			Expression eIndex;
			if (tIndex != null && tIndex.lbl != null)
				eIndex = (Expression) tIndex.lbl;
			else
				eIndex = new IntConstant(index);
			
			
			Expression tS = (Expression) s.PHOSPHOR_TAG.lbl;
			
			if (s.valuePHOSPHOR_TAG.taints == null || s.valuePHOSPHOR_TAG.taints[index] == null) {
				throw new Error("Shouldn't happen");
			}

			Expression pos = (Expression) s.valuePHOSPHOR_TAG.taints[index].lbl;
			PathUtils.getCurPC()._addDet(
					Operator.EQ,
					new Operation(Operator.CHARAT, tS, eIndex),
					new Operation(Operator.CONCAT, new StringConstant(""), pos));

			ret.taint = new ExpressionTaint(pos);
		}
	}
	
	
	public static void toLowerCase(String ret, String s, Locale l) {
		changeCase(ret, s, false);
	}
	
	public static void toLowerCase(String ret, String s) {
		changeCase(ret, s, false);
	}
	
	public static void toUpperCase(String ret, String s, Locale l) {
		changeCase(ret, s, true);
	}
	
	public static void toUpperCase(String ret, String s) {
		changeCase(ret, s, true);
	}
	
	private static void changeCase(String ret, String s, boolean toUpper) {
		if (s.PHOSPHOR_TAG != null && s.PHOSPHOR_TAG.lbl != null) {
			
			Expression newExp = new StringConstant("");
			Taint newTaints[] = new Taint[s.length()];

			IntConstant distance = new IntConstant('a' - 'A');
			IntConstant start = new IntConstant(toUpper ? 'a' : 'A');
			IntConstant end = new IntConstant(toUpper ? 'z' : 'Z');
			
			for (int i = 0 ; i < s.length() ; i++) {
				Expression e;
				if (s.valuePHOSPHOR_TAG.taints == null || s.valuePHOSPHOR_TAG.taints[i] == null) {
					Taint tag = s.PHOSPHOR_TAG;
					s.PHOSPHOR_TAG = null;
					e = new IntConstant(s.charAt(i));
					s.PHOSPHOR_TAG = tag;
				} else {
					e = (Expression) s.valuePHOSPHOR_TAG.taints[i].lbl;
				}
				
				e = new Operation(Operator.ITE,
						new Operation(Operator.AND,
							new Operation(Operator.GE, e, start),
							new Operation(Operator.LE, e, end)),
						new Operation(toUpper ? Operator.SUB : Operator.ADD, e, distance),
						e);
				
				newTaints[i] = new ExpressionTaint(e);
				newExp = new Operation(Operator.CONCAT, newExp, e);
			}
			
			ret.PHOSPHOR_TAG = new ExpressionTaint(newExp);
			ret.valuePHOSPHOR_TAG.taints = newTaints;
		}
	}
	
}
