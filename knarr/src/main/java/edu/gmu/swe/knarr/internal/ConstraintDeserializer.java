package edu.gmu.swe.knarr.internal;

import edu.gmu.swe.knarr.runtime.Coverage;
import edu.gmu.swe.knarr.runtime.StringUtils;
import za.ac.sun.cs.green.expr.*;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;

public class ConstraintDeserializer {

    private DataInputStream dis;
    public LinkedList<Expression> fromBytes(byte[] buf, int offset, int len) throws IOException {
        LinkedList<Expression> ret = new LinkedList<>();
        dis =  new DataInputStream(new ByteArrayInputStream(buf, offset, len));
        int nConstraints = dis.readInt();
        for (int i = 0; i < nConstraints; i++) {
            ret.add(readExpression());
        }
        return ret;
    }

    private Expression readExpression() throws IOException {
        byte type = dis.readByte();
        switch(type){
            case ConstraintSerializer.T_ARRAYVARIABLE:
                return readArrayVariable();
            case ConstraintSerializer.T_BINARYOPERATION:
                return readBinaryOperation();
            case ConstraintSerializer.T_BOOLCONSTANT:
                return readBooleanConstant();
            case ConstraintSerializer.T_BVCONSTANT:
                return readBVConstant();
            case ConstraintSerializer.T_BVVARIABLE:
                return readBVVariable();
            case ConstraintSerializer.T_INTVARIABLE:
                return readIntVariable();
            case ConstraintSerializer.T_FUNCTIONCALL:
                return readFunctionCall();
            case ConstraintSerializer.T_INTCONSTANT:
                return readIntConstant();
            case ConstraintSerializer.T_NARYOPERATION:
                return readNaryOperation();
            case ConstraintSerializer.T_REALCONSTANT:
                return readRealConstant();
            case ConstraintSerializer.T_REALVARIABLE:
                return readRealVariable();
            case ConstraintSerializer.T_STRINGCONSTANT:
                return readStringConstant();
            case ConstraintSerializer.T_STRINGVARIABLE:
                return readStringVariable();
            case ConstraintSerializer.T_UNARYOPERATION:
                return readUnaryOperation();
            default:
                throw new IOException("Invalid object type: " + type);
        }
    }

    private IntVariable readIntVariable() throws IOException {
        IntVariable ret = new IntVariable();
        readVariableBase(ret);
        ret.lowerBound = dis.readInt();
        ret.upperBound = dis.readInt();
        return ret;
    }

    private BVVariable readBVVariable() throws IOException {
        BVVariable ret = new BVVariable();
        readVariableBase(ret);
        ret.size = dis.readInt();
        return ret;
    }

    private UnaryOperation readUnaryOperation() throws IOException {
        UnaryOperation ret = new UnaryOperation();
        readOperationBase(ret);
        ret.operand = readExpression();
        return ret;
    }

    private StringVariable readStringVariable() throws IOException {
        StringVariable ret = new StringVariable();
        readVariableBase(ret);
        return ret;
    }

    private StringConstant readStringConstant() throws IOException {
        StringConstant ret = new StringConstant();
        readExpressionBase(ret);
        ret.value = dis.readUTF();
        return ret;
    }

    private RealVariable readRealVariable() throws IOException {
        RealVariable ret = new RealVariable();
        readVariableBase(ret);
        ret.lowerBound = dis.readDouble();
        ret.upperBound = dis.readDouble();
        return ret;
    }

    private RealConstant readRealConstant() throws IOException {
        RealConstant ret = new RealConstant();
        readExpressionBase(ret);
        ret.value = dis.readDouble();
        return ret;
    }

    private NaryOperation readNaryOperation() throws IOException {
        NaryOperation ret = new NaryOperation();
        readOperationBase(ret);
        ret.operands = new Expression[dis.readInt()];
        for(int i = 0; i < ret.operands.length; i++){
            ret.operands[i] = readExpression();
        }
        return ret;
    }

    private IntConstant readIntConstant() throws IOException {
        IntConstant ret = new IntConstant();
        readExpressionBase(ret);
        ret.value = dis.readLong();
        return ret;
    }

    private FunctionCall readFunctionCall() throws IOException {
        FunctionCall ret = new FunctionCall();
        readExpressionBase(ret);
        ret.name = dis.readUTF();
        ret.arguments = new Expression[dis.readInt()];
        for(int i = 0; i < ret.arguments.length; i++){
            ret.arguments[i] = readExpression();
        }
        return ret;
    }

    private BVConstant readBVConstant() throws IOException {
        BVConstant ret = new BVConstant();
        readExpressionBase(ret);
        ret.size = dis.readInt();
        ret.value = dis.readLong();
        return ret;
    }

    private BoolConstant readBooleanConstant() throws IOException {
        BoolConstant ret = new BoolConstant();
        readExpressionBase(ret);
        ret.value = dis.readBoolean();
        return ret;
    }

    private BinaryOperation readBinaryOperation() throws IOException {
        BinaryOperation ret = new BinaryOperation();
        readOperationBase(ret);
        ret.left = readExpression();
        ret.right = readExpression();
        return ret;
    }

    private ArrayVariable readArrayVariable() throws IOException {
        ArrayVariable ret = new ArrayVariable();
        readVariableBase(ret);
        switch(dis.readByte()){
            case 1:
                ret.type = Boolean.TYPE;
                break;
            case 2:
                ret.type = Byte.TYPE;
                break;
            case 3:
                ret.type = Short.TYPE;
                break;
            case 4:
                ret.type = Integer.TYPE;
                break;
            case 5:
                ret.type = Long.TYPE;
                break;
            case 6:
                ret.type = String.class; //We don't support anything other than the types above, so it doesn't really matter what we put here
                break;

        }
        return ret;
    }

    private void readExpressionBase(Expression expr) throws IOException {
        switch (dis.readByte()) {
            case ConstraintSerializer.METADATA_IS_NULL:
                expr.metadata = null;
                break;
            case ConstraintSerializer.METADATA_HASHSET_STRINGCOMPARISONS:
                int nComparisons = dis.readInt();
                HashSet<StringUtils.StringComparisonRecord> recs = new HashSet<>();
                for(int i = 0; i < nComparisons; i++){
                    String str = dis.readUTF();
                    StringUtils.StringComparisonType typ = comparisonTypes[dis.readByte()];
                    recs.add(new StringUtils.StringComparisonRecord(typ, str));
                }
                expr.metadata = recs;
                break;
            case ConstraintSerializer.METADATA_BRANCH_DATA:
                Coverage.BranchData d = new Coverage.BranchData();
                d.taken = dis.readBoolean();
                if (dis.readBoolean()) {
                    d.source = dis.readUTF();
                }
                d.takenCode = dis.readInt();
                d.notTakenCode = dis.readInt();
                d.notTakenPath = dis.readInt();
                d.breaksLoop = dis.readBoolean();
                expr.metadata = d;
                break;
            case ConstraintSerializer.METADATA_SWITCH_DATA:
                Coverage.SwitchData s = new Coverage.SwitchData();
                s.taken = dis.readBoolean();
                if (dis.readBoolean()) {
                    s.source = dis.readUTF();
                }
                s.switchID = dis.readInt();
                s.numArms = dis.readInt();
                s.arm = dis.readInt();
                break;
        }

    }
    private void readVariableBase(Variable expr) throws IOException{
        readExpressionBase(expr);
        expr.name = dis.readUTF();
    }

    static final StringUtils.StringComparisonType[] comparisonTypes = StringUtils.StringComparisonType.values();
    static final Operation.Operator[] operators = Operation.Operator.values();
    private void readOperationBase(Operation expr) throws IOException {
        readExpressionBase(expr);
        expr.operator = operators[dis.readShort()];
    }
}
