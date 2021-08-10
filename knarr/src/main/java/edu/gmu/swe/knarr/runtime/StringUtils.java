package edu.gmu.swe.knarr.runtime;

import java.io.*;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Objects;

import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.struct.*;

import edu.gmu.swe.knarr.internal.ConstraintSerializer;
import za.ac.sun.cs.green.expr.*;
import za.ac.sun.cs.green.expr.Operation.Operator;

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


	public static LinkedList<String> symbolizedStrings = new LinkedList<>();

	/**
	 * Determines whether the specified character (Unicode code point)
	 * is in the <a href="#BMP">Basic Multilingual Plane (BMP)</a>.
	 * Such code points can be represented using a single {@code char}.
	 *
	 * @param  codePoint the character (Unicode code point) to be tested
	 * @return {@code true} if the specified code point is between
	 *         Character.MIN_VALUE and Character.MAX_VALUE inclusive;
	 *         {@code false} otherwise.
	 * @since  1.7
	 */
	private static boolean isBmpCodePoint(int codePoint) {
		return codePoint >>> 16 == 0;
		// Optimized form of:
		//     codePoint >= MIN_VALUE && codePoint <= MAX_VALUE
		// We consistently use logical shift (>>>) to facilitate
		// additional runtime optimizations.
	}


	// Reuse common constants
	private static BVConstant BV10 = new BVConstant(10, 32);
	private static BVConstant BV3FFF = new BVConstant(0x3FFF, 32);
	private static BVConstant CHAR_MIN_HIGH_SURR = new BVConstant(Character.MIN_HIGH_SURROGATE, 32);
	private static BVConstant CHAR_MIN_LOW_SURR  = new BVConstant(Character.MIN_LOW_SURROGATE, 32);
	private static BVConstant CHAR_MIN_SUPP_CP   = new BVConstant(Character.MIN_SUPPLEMENTARY_CODE_POINT, 32);
	private static BVConstant MAX_CHAR           = new BVConstant(Character.MAX_VALUE, 32);
	private static IntConstant MAX_CHAR_INT;

	private static Expression extractFirstCharFromCodepoint(Expression codePoint) {
		// ((codePoint >>> 10) + (MIN_HIGH_SURROGATE - (MIN_SUPPLEMENTARY_CODE_POINT >>> 10)));
		return new BinaryOperation(
				Operator.ADD,
				new BinaryOperation(Operator.SHIFTUR, codePoint, BV10),
				new BinaryOperation(
						Operator.SUB,
						CHAR_MIN_HIGH_SURR,
						new BinaryOperation(Operator.SHIFTUR, CHAR_MIN_SUPP_CP, BV10)
				)
		);
	}

	private static Expression extractSecondCharFromCodepoint(Expression codePoint) {
		// ((codePoint & 0x3ff) + MIN_LOW_SURROGATE);
		return new BinaryOperation(
				Operator.ADD,
				new BinaryOperation(Operator.BIT_AND, codePoint, BV3FFF),
				CHAR_MIN_LOW_SURR
		);
	}

	public static void registerNewString(String s, LazyArrayObjTags srcTags, Object src, Taint offset_t, int offset, Taint len_t, int len) {
		if (enabled && srcTags != null && srcTags.taints != null) {
			StringVariable var = getFreshStringVar();
			Expression exp = var;
			char[] arr = s.toCharArray();
			// Offset is for src, not for s


			//LazyIntArrayObjTags intSrcTags = (LazyIntArrayObjTags) s.valuePHOSPHOR_TAG.taints;
			Taint[] taintCopy = new Taint[s.length()];




			for (int i = 0 , j = 0 ; i < s.length() ; i++ , j++) {
				// i iterates over the string, may be incremented twice during each loop iteration
				// j iterates over the taints
				Taint t = null;
				if(s.valuePHOSPHOR_TAG != null && s.valuePHOSPHOR_TAG.taints != null)
					t = s.valuePHOSPHOR_TAG.taints[j];
				if (!(srcTags instanceof LazyIntArrayObjTags) || isBmpCodePoint(((LazyIntArrayObjTags)(srcTags)).val[j])) {
					// Single char for this codepoint
					if (t == null) {
						exp = new BinaryOperation(Operator.CONCAT, exp, new IntConstant(arr[i]));
						taintCopy[i] = new ExpressionTaint(new BVVariable(var + "_" + i, 32));
						// Not sure if I should bound the new BVVariable within the Character range
					} else {
						Expression op = (Expression) t.getSingleLabel();
						if (op instanceof BinaryOperation) {
							BinaryOperation binop = (BinaryOperation) op;
							if (MAX_CHAR_INT == null)
								MAX_CHAR_INT = new IntConstant(Character.MAX_VALUE);
							if (binop.getOperator() == Operator.BIT_AND) {
								Expression right = binop.getOperand(1);
								if (MAX_CHAR_INT.equals(right) || MAX_CHAR.equals(right)) {
								    // No point in bounding to MAX_CHAR if its already bound
									exp = new BinaryOperation(Operator.CONCAT, exp, op);
									taintCopy[i] = new ExpressionTaint(binop);
									continue;
								}
							}
						}
						exp = new BinaryOperation(Operator.CONCAT, exp, (Expression) t.getSingleLabel());
						taintCopy[i] = new ExpressionTaint(
								new BinaryOperation(Operator.BIT_AND, (Expression) t.getSingleLabel(), MAX_CHAR));
					}
				} else {
					// Two chars for this codepoint
					if (t == null) {
						exp = new BinaryOperation(Operator.CONCAT, exp, new IntConstant(arr[i]));
						exp = new BinaryOperation(Operator.CONCAT, exp, new IntConstant(arr[i+1]));

						Expression newVariable = new BVVariable(var + "_" + i, 32);

						taintCopy[i]   = new ExpressionTaint(extractFirstCharFromCodepoint(newVariable));
						taintCopy[i+1] = new ExpressionTaint(extractSecondCharFromCodepoint(newVariable));
					} else {

						Expression existingConstraint = (Expression) t.getSingleLabel();

						exp = new BinaryOperation(Operator.CONCAT, exp, extractFirstCharFromCodepoint(existingConstraint));
						exp = new BinaryOperation(Operator.CONCAT, exp, extractSecondCharFromCodepoint(existingConstraint));

						taintCopy[i]   = new ExpressionTaint(extractFirstCharFromCodepoint(existingConstraint));
						taintCopy[i+1] = new ExpressionTaint(extractSecondCharFromCodepoint(existingConstraint));
					}

					// We've processed two chars for one codepoint, increment i again
					i += 1;
				}
			}
			s.valuePHOSPHOR_TAG.taints = taintCopy;
			s.PHOSPHOR_TAG = new ExpressionTaint(exp);
			symbolizedStrings.add(s);
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
					exp.metadata = new HashSet<StringComparisonRecord>();
				if (exp.metadata instanceof HashSet)
					((HashSet) exp.metadata).add(new StringComparisonRecord(StringComparisonType.INDEXOF, pref));
			}


//			if (tStart != null)
//				throw new UnsupportedOperationException();
//
			Expression tS = (Expression) s.PHOSPHOR_TAG.getSingleLabel();
//			if (start > 0)
//				throw new UnsupportedOperationException();

			ret.taint = new ExpressionTaint(new NaryOperation(Operator.INDEXOFSTRING, tS, tPref, new IntConstant(start)));
		}

		else if(enabled && s.PHOSPHOR_TAG == null) {
			if(s.valuePHOSPHOR_TAG != null && s.valuePHOSPHOR_TAG.taints != null) {
				for(int i = 0; i < s.valuePHOSPHOR_TAG.taints.length; i++) {
					if(s.valuePHOSPHOR_TAG.taints[i] != null) {
						Expression exp = (Expression) s.valuePHOSPHOR_TAG.taints[i].getSingleLabel();
						if (exp.metadata == null)
							exp.metadata = new HashSet<StringComparisonRecord>();
						if (exp.metadata instanceof HashSet)
							((HashSet) exp.metadata).add(new StringComparisonRecord(StringComparisonType.INDEXOF, pref));
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
					exp.metadata = new HashSet<StringComparisonRecord>();
				((HashSet) exp.metadata).add(new StringComparisonRecord(StringComparisonType.STARTSWITH, pref));
			}


			//Let's not throw exceptions here that will be swallowed by the app... but note that this is not supported!
			//if (tStart != null)
			//	throw new UnsupportedOperationException();

			Expression tS = (Expression) s.PHOSPHOR_TAG.getSingleLabel();
			//if (start > 0)
			//	throw new UnsupportedOperationException();

			ret.taint = new ExpressionTaint(new BinaryOperation(Operator.STARTSWITH, tPref, tS));
		}
	}

	public static void startsWith$$PHOSPHORTAGGED(TaintedBooleanWithObjTag ret, String s, String pref, TaintedBooleanWithObjTag ret2) {
		startsWith$$PHOSPHORTAGGED(ret, s, pref, null, 0, ret2);
	}

	public static void endsWith$$PHOSPHORTAGGED(TaintedBooleanWithObjTag ret, String s, String suffix, TaintedBooleanWithObjTag ret2) {
		Expression tPref;
		if (enabled && s.PHOSPHOR_TAG != null && s.PHOSPHOR_TAG.getSingleLabel() != null) {
			if (suffix.PHOSPHOR_TAG != null && suffix.PHOSPHOR_TAG.getSingleLabel() != null)
				tPref = (Expression)suffix.PHOSPHOR_TAG.getSingleLabel();
			else {
				tPref = new StringConstant(suffix);
				Expression exp = (Expression) s.PHOSPHOR_TAG.getSingleLabel();
				if (exp.metadata == null)
					exp.metadata = new HashSet<StringComparisonRecord>();
				((HashSet) exp.metadata).add(new StringComparisonRecord(StringComparisonType.ENDWITH, suffix));
			}



			Expression tS = (Expression) s.PHOSPHOR_TAG.getSingleLabel();

			ret.taint = new ExpressionTaint(new BinaryOperation(Operator.ENDSWITH, tPref, tS));
		}
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
			}

			Expression tS = (Expression) s1.PHOSPHOR_TAG.getSingleLabel();
			Expression exp = new BinaryOperation(Operator.EQUALS, tS, tO);
			if (exp.metadata == null)
				exp.metadata = new HashSet<StringComparisonRecord>();
			((HashSet) exp.metadata).add(new StringComparisonRecord(StringComparisonType.EQUALS, s2));
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
			}

			Expression tS = (Expression) s1.PHOSPHOR_TAG.getSingleLabel();
			Expression exp = new BinaryOperation(Operator.EQUALS, tS, tO);
			if (exp.metadata == null)
				exp.metadata = new HashSet<StringComparisonRecord>();
			((HashSet) exp.metadata).add(new StringComparisonRecord(StringComparisonType.EQUALS,s2));
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
					new BinaryOperation(Operator.CHARAT, tS, eIndex),
					new BinaryOperation(Operator.CONCAT, new StringConstant(""), pos));

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

				e = new NaryOperation(Operator.ITE,
						new BinaryOperation(Operator.AND,
							new BinaryOperation(Operator.GE, e, start),
							new BinaryOperation(Operator.LE, e, end)),
						new BinaryOperation(toUpper ? Operator.SUB : Operator.ADD, e, distance),
						e);

				newTaints[i] = new ExpressionTaint(e);
				newExp = new BinaryOperation(Operator.CONCAT, newExp, e);
			}

			ret.PHOSPHOR_TAG = new ExpressionTaint(newExp);
			ret.valuePHOSPHOR_TAG.taints = newTaints;
		}
	}

	public static void length$$PHOSPHORTAGGED(TaintedIntWithObjTag ret, String s, TaintedIntWithObjTag ret2) {
		if (enabled && s.PHOSPHOR_TAG != null && s.PHOSPHOR_TAG.getSingleLabel() != null) {
			Expression exp = new UnaryOperation(Operator.I2BV, 32, new UnaryOperation(Operator.LENGTH, (Expression) s.PHOSPHOR_TAG.getSingleLabel()));
			//if (exp.metadata == null)
			//	exp.metadata = new HashSet<Pair<String,String>>();
			//if (exp.metadata instanceof HashSet)
			//	((HashSet) exp.metadata).add(new Pair<>("LENGTH", Integer.toString(s.length())));
			ret.taint = new ExpressionTaint(exp);
		}
	}

	public static void isEmpty$$PHOSPHORTAGGED(TaintedBooleanWithObjTag ret, String s, TaintedBooleanWithObjTag ret2) {
		if (enabled && s.PHOSPHOR_TAG != null && s.PHOSPHOR_TAG.getSingleLabel() != null) {
			//Expression lengthExpr  = new UnaryOperation(Operator.LENGTH, (Expression) s.PHOSPHOR_TAG.getSingleLabel());
			Expression exp = new BinaryOperation(Operator.EQUALS, new StringConstant(""), (Expression) s.PHOSPHOR_TAG.getSingleLabel());
			if (exp.metadata == null)
				exp.metadata = new HashSet<StringComparisonRecord>();
			((HashSet) exp.metadata).add(new StringComparisonRecord(StringComparisonType.ISEMPTY, ""));
			ret.taint = new ExpressionTaint(exp);
		}
	}

	public static int stringName;

	public static StringVariable getFreshStringVar() {
		return new StringVariable("string_var_" + (stringName++));
	}

	public static class StringComparisonRecord implements Externalizable {
		public String stringCompared;
		public StringComparisonType comparisionType;

		public String getStringCompared() {
			return stringCompared;
		}

		public StringComparisonType getComparisionType() {
			return comparisionType;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			StringComparisonRecord that = (StringComparisonRecord) o;
			return Objects.equals(stringCompared, that.stringCompared) &&
					comparisionType == that.comparisionType;
		}

		@Override
		public String toString() {
			return "StringComparisonRecord{" +
					"stringCompared='" + stringCompared + '\'' +
					", comparisionType=" + comparisionType +
					'}';
		}

		@Override
		public int hashCode() {
			return Objects.hash(stringCompared, comparisionType);
		}


		public StringComparisonRecord(){

		}

		public StringComparisonRecord(StringComparisonType comparisionType, String stringCompared){
			this.comparisionType = comparisionType;
			this.stringCompared = stringCompared;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeUTF(stringCompared);
			out.writeInt(comparisionType.ordinal());
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			this.stringCompared = in.readUTF();
			this.comparisionType = StringComparisonType.values()[in.readInt()];
		}
	}
	public enum StringComparisonType{
		ISEMPTY,
		INDEXOF,
		EQUALS,
		STARTSWITH,
		ENDWITH,
	}
}
