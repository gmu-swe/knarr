package edu.gmu.swe.knarr.runtime;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Locale;

import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.struct.LazyArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.LazyCharArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.TaintedBooleanWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedCharWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedIntWithObjTag;

import org.jgrapht.alg.util.Pair;
import za.ac.sun.cs.green.expr.BVVariable;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.StringConstant;
import za.ac.sun.cs.green.expr.StringVariable;

public class StringUtils {
	public static boolean enabled = true;
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

	public static void registerNewString(String s, LazyArrayObjTags srcTags, Object src, Taint offset_t, int offset, Taint len_t, int len) {
		if (enabled && srcTags != null && srcTags.taints != null) {
			StringVariable var = getFreshStringVar();
			Expression exp = var;
			char[] arr = s.toCharArray();
			// Offset is for src, not for s
			for (int i = 0 ; i < len ; i++) {
				Taint t = srcTags.taints[offset + i];
				if (t == null) {
					exp = new Operation(Operator.CONCAT, exp, new IntConstant(arr[i]));
					s.valuePHOSPHOR_TAG.taints[i] = new ExpressionTaint(new BVVariable(var + "_" + i, 32));
				} else {
					exp = new Operation(Operator.CONCAT, exp, (Expression) t.getSingleLabel());
				}
			}
			
			s.PHOSPHOR_TAG = new ExpressionTaint(exp);
		}
	}

	public static void contains$$PHOSPHORTAGGED(TaintedBooleanWithObjTag ret, String s, CharSequence c, TaintedBooleanWithObjTag ret2) {
		if (enabled && s.PHOSPHOR_TAG != null && s.PHOSPHOR_TAG.getSingleLabel() != null) {
			enabled = enabled;
        }
    }


    //(Ledu/columbia/cs/psl/phosphor/struct/LazyCharArrayObjTags; tags
	// [C   paramArrayOfChars
	// Ledu/columbia/cs/psl/phosphor/runtime/Taint;  t1
	// I   i1
	// Ledu/columbia/cs/psl/phosphor/runtime/Taint; t2
	// I  i2
	// Ljava/lang/String; s
	// Ledu/columbia/cs/psl/phosphor/runtime/Taint; t3
	// I i3
	// Ledu/columbia/cs/psl/phosphor/struct/TaintedIntWithObjTag;)
	//
	// Ledu/columbia/cs/psl/phosphor/struct/TaintedIntWithObjTag;
    public static void indexOf$$PHOSPHORTAGGED(TaintedIntWithObjTag ret, String str, LazyCharArrayObjTags tags, char[] c, Taint t1 , int i, Taint t2, int i2, String s, Taint t3, TaintedIntWithObjTag ret2) {
    	return;
	}



	public static void indexOf$$PHOSPHORTAGGED(TaintedIntWithObjTag ret, String str,  LazyCharArrayObjTags tags,  char[] paramArrayOfChar, Taint t1, int paramInt1, Taint t2,  int paramInt2, String paramString, Taint t3, int paramInt3, TaintedIntWithObjTag ret2) {
		return;
	}

	public static void indexOf$$PHOSPHORTAGGED(TaintedIntWithObjTag ret, LazyCharArrayObjTags tags, char[] paramArrayOfChar, Taint t1, int i1, Taint t2, int i2, String s, Taint t3, int i3, TaintedIntWithObjTag ret2) {
		return;
	}

	public static void indexOf$$PHOSPHORTAGGED(TaintedIntWithObjTag ret, String s,  char[] paramArrayOfChar, Taint t1, int paramInt1, Taint t2, int paramInt2,  String paramString,Taint t3, int paramInt3, TaintedIntWithObjTag ret2) {
		return;
	}

	public static void indexOf$$PHOSPHORTAGGED  (TaintedIntWithObjTag ret, String s,  LazyCharArrayObjTags tags, String str, char[] paramArrayOfChar, Taint t1, int paramInt1, Taint t2, int paramInt2,  String paramString,Taint t3, int paramInt3, TaintedIntWithObjTag ret2) {
		return;
	}


	public static void indexOf$$PHOSPHORTAGGED(LazyCharArrayObjTags paramLazyCharArrayObjTags, char[] paramArrayOfChar, Taint paramTaint1, int paramInt1, Taint paramTaint2, int paramInt2, String paramString, Taint paramTaint3, int paramInt3, TaintedIntWithObjTag paramTaintedIntWithObjTag) {
		return;
	}

	public static void indexOf$$PHOSPHORTAGGED(TaintedIntWithObjTag ret, String s, String pref, int start, TaintedIntWithObjTag ret2) {

		Expression tPref;
		if (enabled && s.PHOSPHOR_TAG != null && s.PHOSPHOR_TAG.getSingleLabel() != null && pref != null) {
			if (pref.PHOSPHOR_TAG != null && pref.PHOSPHOR_TAG.getSingleLabel() != null)
				tPref = (Expression)pref.PHOSPHOR_TAG.getSingleLabel();
			else {
				tPref = new StringConstant(pref);
				Expression exp = (Expression) s.PHOSPHOR_TAG.getSingleLabel();
				if (exp.metadata == null)
					exp.metadata = new HashSet<Pair<String,String>>();
				if (exp.metadata instanceof HashSet)
					((HashSet) exp.metadata).add(new Pair<>("INDEXOF", pref));
					//((HashSet) exp.metadata).add(new Pair<>("EQUALS", pref));
			}


//			if (tStart != null)
//				throw new UnsupportedOperationException();
//
			Expression tS = (Expression) s.PHOSPHOR_TAG.getSingleLabel();
//			if (start > 0)
//				throw new UnsupportedOperationException();

			ret.taint = new ExpressionTaint(new Operation(Operator.LASTINDEXOFSTRING, tPref, tS));
		}

		else if(enabled && s.PHOSPHOR_TAG == null) {
			if(s.valuePHOSPHOR_TAG != null && s.valuePHOSPHOR_TAG.taints != null) {
				for(int i = 0; i < s.valuePHOSPHOR_TAG.taints.length; i++) {
					if(s.valuePHOSPHOR_TAG.taints[i] != null) {
						Expression exp = (Expression) s.valuePHOSPHOR_TAG.taints[i].getSingleLabel();
						if (exp.metadata == null)
							exp.metadata = new HashSet<String>();
						if (exp.metadata instanceof HashSet)
							((HashSet) exp.metadata).add(new Pair<>("INDEXOF", pref));
//							((HashSet) exp.metadata).add(new Pair<>("EQUALS", pref));
					}
				}
			}

		}
	}


	public static void indexOf$$PHOSPHORTAGGED(TaintedIntWithObjTag ret, String s, Taint t, int c, TaintedIntWithObjTag ret2) {
		indexOf$$PHOSPHORTAGGED(ret, s, String.valueOf((char)c), 0, ret2);
	}



	public static void indexOf$$PHOSPHORTAGGED(TaintedIntWithObjTag ret, String s, Taint t1, int c, Taint t2, int start, TaintedIntWithObjTag ret2) {
		indexOf$$PHOSPHORTAGGED(ret, s, String.valueOf((char)c), 0, ret2);
	}


	public static void indexOf$$PHOSPHORTAGGED(TaintedIntWithObjTag ret, String s, Taint tPref,  String pref, TaintedIntWithObjTag ret2) {
		indexOf$$PHOSPHORTAGGED(ret, s, pref, 0, ret2);
	}

	public static void indexOf$$PHOSPHORTAGGED(TaintedIntWithObjTag ret, String s, String pref, Taint tPref, TaintedIntWithObjTag ret2) {
		indexOf$$PHOSPHORTAGGED(ret, s, pref, 0, ret2);
	}

	public static void indexOf$$PHOSPHORTAGGED(TaintedIntWithObjTag ret, String s, String pref, TaintedIntWithObjTag ret2) {
		indexOf$$PHOSPHORTAGGED(ret, s, pref, 0, ret2);
	}

	public static void indexOf$$PHOSPHORTAGGED(TaintedIntWithObjTag ret, String s, String pref, Taint t, int index, TaintedIntWithObjTag ret2) {
		indexOf$$PHOSPHORTAGGED(ret, s, pref, 0, ret2);
	}
	public static void startsWith$$PHOSPHORTAGGED(TaintedBooleanWithObjTag ret, String s, String pref, Taint tStart, int start, TaintedBooleanWithObjTag ret2) {
		Expression tPref;
		if (enabled && s.PHOSPHOR_TAG != null && s.PHOSPHOR_TAG.getSingleLabel() != null) {
			if (pref.PHOSPHOR_TAG != null && pref.PHOSPHOR_TAG.getSingleLabel() != null)
				tPref = (Expression)pref.PHOSPHOR_TAG.getSingleLabel();
			else {
				tPref = new StringConstant(pref);
				Expression exp = (Expression) s.PHOSPHOR_TAG.getSingleLabel();
				if (exp.metadata == null)
					exp.metadata = new HashSet<Pair<String,String>>();
				if (exp.metadata instanceof HashSet)
					((HashSet) exp.metadata).add(new Pair<>("STARTSWITH",pref));
			}
			

			if (tStart != null)
				throw new UnsupportedOperationException();
			
			Expression tS = (Expression) s.PHOSPHOR_TAG.getSingleLabel();
			if (start > 0)
				throw new UnsupportedOperationException();
			
			ret.taint = new ExpressionTaint(new Operation(Operator.STARTSWITH, tPref, tS));
		}
	}
	
	public static void startsWith$$PHOSPHORTAGGED(TaintedBooleanWithObjTag ret, String s, String pref, TaintedBooleanWithObjTag ret2) {
		startsWith$$PHOSPHORTAGGED(ret, s, pref, null, 0, ret2);
	}
	

	public static void equals$$PHOSPHORTAGGED(TaintedBooleanWithObjTag ret, String s, Object o, TaintedBooleanWithObjTag ret2) {
		registerNewString(s, s.valuePHOSPHOR_TAG, null, null, 0, null, s.length());
		if (o instanceof String)
			registerNewString((String)o, ((String)o).valuePHOSPHOR_TAG, null, null, 0, null, ((String)o).length());
		if (enabled && o != null && o instanceof String && s.PHOSPHOR_TAG != null && s.PHOSPHOR_TAG.getSingleLabel() != null) {
			String s1 = s;
			String s2 = (String)o;

			Expression tO;
			Serializable metadata = null;
			if (s2.PHOSPHOR_TAG != null && s2.PHOSPHOR_TAG.getSingleLabel() != null)
				tO = (Expression)s2.PHOSPHOR_TAG.getSingleLabel();
			else {
				tO = new StringConstant(s2);
				Expression exp = (Expression) s1.PHOSPHOR_TAG.getSingleLabel();
				if (exp.metadata == null)
				    exp.metadata = new HashSet<Pair<String,String>>();
				if (exp.metadata instanceof HashSet)
					((HashSet) exp.metadata).add(new Pair<>("EQUALS",s2));
			}
			
			Expression tS = (Expression) s1.PHOSPHOR_TAG.getSingleLabel();
			Expression exp = new Operation(Operator.EQUALS, tS, tO);
			ret.taint = new ExpressionTaint(exp);
		} else if (enabled && o != null && o instanceof String && ((String)o).PHOSPHOR_TAG != null && ((String)o).PHOSPHOR_TAG.getSingleLabel() != null) {
			String s1 = (String)o;
			String s2 = s;

			Expression tO;
			Serializable metadata = null;
			if (s2.PHOSPHOR_TAG != null && s2.PHOSPHOR_TAG.getSingleLabel() != null)
				tO = (Expression)s2.PHOSPHOR_TAG.getSingleLabel();
			else {
				tO = new StringConstant(s2);
				Expression exp = (Expression) s1.PHOSPHOR_TAG.getSingleLabel();
				if (exp.metadata == null)
					exp.metadata = new HashSet<Pair<String,String>>();
				if (exp.metadata instanceof HashSet)
					((HashSet) exp.metadata).add(new Pair<>("EQUALS",s2));
			}

			Expression tS = (Expression) s1.PHOSPHOR_TAG.getSingleLabel();
			Expression exp = new Operation(Operator.EQUALS, tS, tO);
			ret.taint = new ExpressionTaint(exp);
		}
	}
	
	
	public static void charAt$$PHOSPHORTAGGED(TaintedCharWithObjTag ret, String s, Taint tIndex, int index, TaintedCharWithObjTag ret2) {
		if (enabled && s.PHOSPHOR_TAG != null && s.PHOSPHOR_TAG.getSingleLabel() != null) {
			Expression eIndex;
			if (tIndex != null && tIndex.getSingleLabel() != null)
				eIndex = (Expression) tIndex.getSingleLabel();
			else
				eIndex = new IntConstant(index);
			
			
			Expression tS = (Expression) s.PHOSPHOR_TAG.getSingleLabel();
			
			if (s.valuePHOSPHOR_TAG.taints == null || s.valuePHOSPHOR_TAG.taints[index] == null) {
				throw new Error("Shouldn't happen");
			}

			Expression pos = (Expression) s.valuePHOSPHOR_TAG.taints[index].getSingleLabel();
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
		if (enabled && s.PHOSPHOR_TAG != null && s.PHOSPHOR_TAG.getSingleLabel() != null) {
			
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
					e = (Expression) s.valuePHOSPHOR_TAG.taints[i].getSingleLabel();
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
	
	public static void length$$PHOSPHORTAGGED(TaintedIntWithObjTag ret, String s, TaintedIntWithObjTag ret2) {
		if (enabled && s.PHOSPHOR_TAG != null && s.PHOSPHOR_TAG.getSingleLabel() != null)
			ret.taint = new ExpressionTaint(new Operation(Operator.LENGTH, (Expression) s.PHOSPHOR_TAG.getSingleLabel()));
	}
	
	public static int stringName;
	
	public static StringVariable getFreshStringVar() {
		return new StringVariable("string_var_" + (stringName++));
	}
	
}
