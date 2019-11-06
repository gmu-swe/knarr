package edu.gmu.swe.knarr.runtime;

import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.struct.LazyCharArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.TaintedBooleanWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedIntWithObjTag;
import za.ac.sun.cs.green.expr.BoolConstant;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.StringVariable;

public class ModelUtils {

    public static TaintedIntWithObjTag digit$$PHOSPHORTAGGED(Taint cTaint, int codePoint, Taint rTaint, int radix, TaintedIntWithObjTag ret) {
        ret.val = Character.digit(codePoint, radix);
        return ret;
    }

    public static TaintedIntWithObjTag digit$$PHOSPHORTAGGED(Taint cTaint, char c, Taint rTaint, int radix, TaintedIntWithObjTag ret) {
        ret.val = Character.digit(c, radix);

         if (cTaint != null) {
             Operation bounds = new Operation(Operator.AND,
                     new Operation(Operator.GE, (Expression) cTaint.getSingleLabel(), new IntConstant('0')),
                     new Operation(Operator.LE, (Expression) cTaint.getSingleLabel(), new IntConstant('9')));
             PathUtils.getCurPC()._addDet(ret.val != -1 ? bounds : new Operation(Operator.NOT, bounds));

             ret.taint = cTaint;
         }

        return ret;
    }

    private static Expression generateStringConstraints(String receiver, String s) {
        Expression ret = null;
        if (receiver.length() >= s.length()) {
            ret = new BoolConstant(true);
            int i = 0;

            // Add constraints to match same length
            for (; i < s.length(); i++) {
                ret = new Operation(Operator.AND,
                        ret,
                        new Operation(Operator.EQ,
                                (Expression) receiver.valuePHOSPHOR_TAG.taints[i].getSingleLabel(),
                                new IntConstant(s.charAt(i))));
            }

            // Pad remaining length with spaces
            for (; i < receiver.length(); i++) {
                if (receiver.valuePHOSPHOR_TAG == null || receiver.valuePHOSPHOR_TAG.taints[i] == null)
                    continue;

                ret = new Operation(Operator.AND,
                        ret,
                        new Operation(Operator.EQ,
                                (Expression) receiver.valuePHOSPHOR_TAG.taints[i].getSingleLabel(),
                                new IntConstant(' ')));
            }
        } else {
            // TODO Handle strings compared against something longer
        }

        return ret;
    }

    public static TaintedBooleanWithObjTag equals$$PHOSPHORTAGGED(String receiver, Object o, TaintedBooleanWithObjTag ret) {

        if (receiver.valuePHOSPHOR_TAG != null && receiver.valuePHOSPHOR_TAG.taints != null) {
            if (o instanceof String) {
                String s = (String)o;

                Expression e = generateStringConstraints(receiver, s);
                if (e != null) {
                    LazyCharArrayObjTags tmp = receiver.valuePHOSPHOR_TAG;
                    receiver.valuePHOSPHOR_TAG = null;
                    ret.val = receiver.equals(o);
                    receiver.valuePHOSPHOR_TAG = tmp;

                    ret.taint = new ExpressionTaint(ret.val ? e : new Operation(Operator.NOT, e));
                } else {
                    ret.val = receiver.equals(o);
                }
            } else {
                ret.val = receiver.equals(o);
            }
        } else {
            ret.val = receiver.equals(o);
        }

        return ret;
    }

    public static String addSymbol$$PHOSPHORTAGGED(com.sun.org.apache.xerces.internal.util.SymbolTable table, LazyCharArrayObjTags buffTaints, char[] buff, Taint offsetTaint, int offset, Taint lengthTaint, int length) {
        String ret = table.addSymbol(buff, offset, length);

        if (buffTaints != null && buffTaints.taints != null) {
            // Copy length taints from offset
            ret.valuePHOSPHOR_TAG = new LazyCharArrayObjTags(length);
            ret.valuePHOSPHOR_TAG.taints = new Taint[length];

            try {
                System.arraycopy(buffTaints.taints, offset, ret.valuePHOSPHOR_TAG.taints, 0, length);
            } catch (RuntimeException e) {
                throw e;
            }

        }

        // Set the string taint as the concat of all those taints
        StringVariable var = StringUtils.getFreshStringVar();
        Expression exp = var;
        for (int i = 0 ; i < length ; i++) {
            Taint t = buffTaints.taints[offset + i];
            if (t == null) {
                exp = new Operation(Operator.CONCAT, exp, new IntConstant(buff[offset + i]));
            } else {
                exp = new Operation(Operator.CONCAT, exp, (Expression) t.getSingleLabel());
            }
        }

        ret.PHOSPHOR_TAG = new ExpressionTaint(exp);


        return ret;
    }
}
