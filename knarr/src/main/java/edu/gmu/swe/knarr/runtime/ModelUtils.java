package edu.gmu.swe.knarr.runtime;

import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.struct.LazyCharArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.TaintedBooleanWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedIntWithObjTag;
import za.ac.sun.cs.green.expr.*;
import za.ac.sun.cs.green.expr.Operation.Operator;

public class ModelUtils {

    public static TaintedIntWithObjTag digit$$PHOSPHORTAGGED(Taint cTaint, int codePoint, Taint rTaint, int radix, TaintedIntWithObjTag ret) {
        ret.val = Character.digit(codePoint, radix);
        return ret;
    }

    public static TaintedIntWithObjTag digit$$PHOSPHORTAGGED(Taint cTaint, char c, Taint rTaint, int radix, TaintedIntWithObjTag ret) {
        ret.val = Character.digit(c, radix);

         if (cTaint != null) {
             Operation bounds = new BinaryOperation(Operator.AND,
                     new BinaryOperation(Operator.GE, (Expression) cTaint.getSingleLabel(), IntConstant.ICONST_CHAR_0),
                     new BinaryOperation(Operator.LE, (Expression) cTaint.getSingleLabel(), IntConstant.ICONST_CHAR_9));
             PathUtils.getCurPC()._addDet(ret.val != -1 ? bounds : new UnaryOperation(Operator.NOT, bounds));

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
                ret = new BinaryOperation(Operator.AND,
                        ret,
                        new BinaryOperation(Operator.EQ,
                                (Expression) receiver.valuePHOSPHOR_TAG.taints[i].getSingleLabel(),
                                new IntConstant(s.charAt(i))));
            }

            // Pad remaining length with spaces
            for (; i < receiver.length(); i++) {
                if (receiver.valuePHOSPHOR_TAG == null || receiver.valuePHOSPHOR_TAG.taints[i] == null)
                    continue;

                ret = new BinaryOperation(Operator.AND,
                        ret,
                        new BinaryOperation(Operator.EQ,
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

                    ret.taint = new ExpressionTaint(ret.val ? e : new UnaryOperation(Operator.NOT, e));
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
//            ret.valuePHOSPHOR_TAG = new LazyCharArrayObjTags(length);
            if(ret.valuePHOSPHOR_TAG == null){
                ret.valuePHOSPHOR_TAG = new LazyCharArrayObjTags(ret.value);
            }
            ret.valuePHOSPHOR_TAG.taints = new Taint[length];

            try {
                System.arraycopy(buffTaints.taints, offset, ret.valuePHOSPHOR_TAG.taints, 0, length);
            } catch (RuntimeException e) {
                throw e;
            }

            // Set the string taint as the concat of all those taints
            StringVariable var = StringUtils.getFreshStringVar();
            Expression exp = var;
            for (int i = 0 ; i < length ; i++) {
                Taint t = buffTaints.taints[offset + i];
                if (t == null) {
                    exp = new BinaryOperation(Operator.CONCAT, exp, new IntConstant(buff[offset + i]));
                } else {
                    exp = new BinaryOperation(Operator.CONCAT, exp, (Expression) t.getSingleLabel());
                }
            }

            ret.PHOSPHOR_TAG = new ExpressionTaint(exp);
        }


        return ret;
    }

    public static void checkArrayAccess(Object arr, int idx, int id, String source) {
    }

    public static void checkArrayAccess$$PHOSPHORTAGGED(Object o, Taint idxTaint, int idx, int id, String source) {
        if (idxTaint != null) {
            Object[] arr = (Object[]) o;
            if (!(idxTaint.getSingleLabel() instanceof Operation))
                return;

            Operation op = (Operation) idxTaint.getSingleLabel();

            // Skip power-of-2 sized arrays with access sanitized through bit-wise and
            {
                if (op.getOperator().equals(Operator.BIT_AND)) {
                    if (op.getOperand(1) instanceof IntConstant) {
                        IntConstant c = (IntConstant) op.getOperand(1);
                        int max = Integer.MAX_VALUE & (int) c.getValueLong();
                        if  (max < arr.length)
                            return;
                    }
                }
            }

            // Skip array accesses sanitized with modulus operation
            {
                if (op.getOperator().equals(Operator.MOD)) {
                    if (op.getOperand(1) instanceof IntConstant) {
                        IntConstant c = (IntConstant) op.getOperand(1);
                        if  (c.getValueLong() <= (arr.length))
                            return;
                    }
                }
            }

            // Skip array accesses sanitized with right shift
            {
                if (op.getOperator().equals(Operator.SHIFTUR)) {
                    if (op.getOperand(1) instanceof IntConstant) {
                        IntConstant c = (IntConstant) op.getOperand(1);
                        if (c.getValueLong() > Integer.MAX_VALUE)
                            return;

                        int max = Integer.MAX_VALUE >>> (int) c.getValueLong();
                        if  (max < arr.length)
                            return;
                    }
                }
            }

            idxTaint = null;
        }
    }
}
