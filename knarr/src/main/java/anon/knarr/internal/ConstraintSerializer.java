package anon.knarr.internal;

import anon.knarr.runtime.Coverage;
import edu.columbia.cs.psl.phosphor.TaintUtils;
import anon.knarr.runtime.StringUtils;
import za.ac.sun.cs.green.expr.*;

import java.io.*;
import java.util.HashSet;
import java.util.LinkedList;

public class ConstraintSerializer {
    private final ByteArrayList buff = new ByteArrayList(100 * 1024);
    private HashSet<Integer> backReferences = new HashSet<>();

    public void write(Expression expr){
        write(expr, buff);
    }
    public void write(LinkedList<Expression> expressions){
        writeInt(expressions.size(), buff);
        for(Expression exp : expressions){
            write(exp, buff);
        }
        for (Expression exp : expressions) {
            clearMarks(exp);
        }
    }

    public void writeTo(OutputStream os) throws IOException {
        DataOutputStream dos = new DataOutputStream(os);
        dos.writeInt(backReferences.size());
        for(Integer i : backReferences){
            dos.writeInt(i);
        }
        dos.writeInt(size());
        dos.write(bytes(), 0, size());
        dos.flush();
    }
    private void clearMarks(Expression expr) {
        if (expr instanceof BinaryOperation) {
            clearMarks(((BinaryOperation) expr).left);
            clearMarks(((BinaryOperation) expr).right);
        } else if (expr instanceof FunctionCall) {
            Expression[] arguments = ((FunctionCall) expr).arguments;
            for (int i = 0; i < arguments.length; i++) {
                clearMarks(arguments[i]);
            }
        } else if (expr instanceof UnaryOperation) {
            clearMarks(((UnaryOperation) expr).operand);
        } else if (expr instanceof NaryOperation) {
            Expression[] operands = ((NaryOperation) expr).operands;
            for (int i = 0; i < operands.length; i++) {
                clearMarks(operands[i]);
            }
        }
        expr.serializationMark = 0;
    }

    public void write(Expression expr, ByteArrayList out) {
        if (expr instanceof ArrayVariable) {
            write((ArrayVariable) expr, out);
        } else if (expr instanceof BinaryOperation) {
            write((BinaryOperation) expr, out);
        } else if (expr instanceof BoolConstant) {
            write((BoolConstant) expr, out);
        } else if (expr instanceof BVConstant) {
            write((BVConstant) expr, out);
        } else if (expr instanceof BVVariable) {
            write((BVVariable) expr, out);
        } else if (expr instanceof FunctionCall) {
            write((FunctionCall) expr, out);
        } else if (expr instanceof IntConstant) {
            write((IntConstant) expr, out);
        } else if (expr instanceof IntVariable) {
            write((IntVariable) expr, out);
        } else if (expr instanceof NaryOperation) {
            write((NaryOperation) expr, out);
        } else if (expr instanceof RealConstant) {
            write((RealConstant) expr, out);
        } else if (expr instanceof RealVariable) {
            write((RealVariable) expr, out);
        } else if (expr instanceof StringConstant) {
            write((StringConstant) expr, out);
        } else if (expr instanceof UnaryOperation) {
            write((UnaryOperation) expr, out);
        } else if (expr instanceof StringVariable) {
            write((StringVariable) expr, out);
        } else {
            throw new IllegalArgumentException("Unsupported type: " + expr.getClass());
        }
    }

    public int size() {
        return buff.size();
    }

    public byte[] bytes() {
        return buff.getBytesUnsafe();
    }

    /**
     * Returns true if the object was previously written, in which case it will also write out
     * the reference to that object.
     *
     * Returns false if the object was not previously written.
     * @param expr
     * @return
     */
    private boolean handlePreviouslyWrittenObject(Expression expr, ByteArrayList out) {
        if(expr.serializationMark == 0){
            expr.serializationMark = out.size();
            return false;
        }
        this.backReferences.add(expr.serializationMark);
        writeTableReference(expr.serializationMark, out);
        return true;
    }


    private void writeObjectHeader(byte headerType, ByteArrayList out) {
        out.add(headerType);
    }

    private void writeTableReference(int newKey, ByteArrayList out) {
        writeObjectHeader(T_TABLEREFERENCE, out);
        writeInt(newKey, out);
    }

    public void write(ArrayVariable v,  ByteArrayList out) {
        if (!handlePreviouslyWrittenObject(v, out)) {
            writeObjectHeader(T_ARRAYVARIABLE, out);
            writeVariableFields(v, out);
            String s = v.getType().getName();
            switch (s) {
                case "boolean":
                    out.add((byte) 1);
                    break;
                case "byte":
                    out.add((byte) 2);
                    break;
                case "short":
                case "char":
                    out.add((byte) 3);
                    break;
                case "int":
                    out.add((byte) 4);
                    break;
                case "long":
                    out.add((byte) 5);
                    break;
                default:
                    out.add((byte) 6);
            }
        }
    }

    public void write(BinaryOperation v,  ByteArrayList out) {
        if (!handlePreviouslyWrittenObject(v, out)) {
            writeObjectHeader(T_BINARYOPERATION, out);
            writeOperationFields(v, out);
            write(v.left, out);
            write(v.right, out);
        }
    }

    public void write(BoolConstant v,  ByteArrayList out) {
        if (!handlePreviouslyWrittenObject(v, out)) {
            writeObjectHeader(T_BOOLCONSTANT, out);
            writeExpressionFields(v, out);
            writeBoolean(v.value, out);
        }
    }

    public void write(BVConstant v,  ByteArrayList out) {
        if (!handlePreviouslyWrittenObject(v, out)) {
            writeObjectHeader(T_BVCONSTANT, out);
            writeExpressionFields(v, out);
            writeInt(v.size, out);
            writeLong(v.value, out);
        }
    }

    public void write(BVVariable v,  ByteArrayList out) {
        if (!handlePreviouslyWrittenObject(v, out)) {
            writeObjectHeader(T_BVVARIABLE, out);
            writeVariableFields(v, out);
            writeInt(v.size, out);
        }
    }

    public void write(FunctionCall v,  ByteArrayList out) {
        if (!handlePreviouslyWrittenObject(v, out)) {
            writeObjectHeader(T_FUNCTIONCALL, out);
            writeExpressionFields(v, out);
            writeUTF(v.getName(), out);
            Expression[] arguments = v.arguments;
            writeInt(arguments.length, out);
            for (int i = 0; i < arguments.length; i++) {
                write(arguments[i], out);
            }
        }
    }

    public void write(IntVariable v,  ByteArrayList out) {
        if (!handlePreviouslyWrittenObject(v, out)) {
            writeObjectHeader(T_INTVARIABLE, out);
            writeVariableFields(v,out);
            writeInt(v.lowerBound, out);
            writeInt(v.upperBound, out);
        }
    }

    public void write(IntConstant v,  ByteArrayList out) {
        if(!handlePreviouslyWrittenObject(v, out)) {
            writeObjectHeader(T_INTCONSTANT, out);
            writeExpressionFields(v, out);
            writeLong(v.value, out);
        }
    }

    public void write(NaryOperation v,  ByteArrayList out) {
        if(!handlePreviouslyWrittenObject(v, out)) {
            writeObjectHeader(T_NARYOPERATION, out);
            writeOperationFields(v, out);
            Expression[] operands = v.operands;
            writeInt(operands.length, out);
            for (int i = 0; i < operands.length; i++) {
                write(operands[i], out);
            }
        }
    }

    public void write(RealConstant v,  ByteArrayList out) {
        if(!handlePreviouslyWrittenObject(v, out)) {
            writeObjectHeader(T_REALCONSTANT, out);
            writeExpressionFields(v, out);
            writeDouble(v.value, out);
        }
    }

    public void write(RealVariable v,  ByteArrayList out) {
        if(!handlePreviouslyWrittenObject(v, out)) {
            writeObjectHeader(T_REALVARIABLE, out);
            writeVariableFields(v, out);
            writeDouble(v.lowerBound, out);
            writeDouble(v.upperBound, out);
        }
    }

    public void write(StringConstant v,  ByteArrayList out) {
        if(!handlePreviouslyWrittenObject(v, out)) {
            writeObjectHeader(T_STRINGCONSTANT, out);
            writeExpressionFields(v, out);
            writeUTF(v.value, out);
        }
    }

    public void write(StringVariable v,  ByteArrayList out) {
        if(!handlePreviouslyWrittenObject(v, out)) {
            writeObjectHeader(T_STRINGVARIABLE, out);
            writeVariableFields(v, out);
        }
    }

    public void write(UnaryOperation v,  ByteArrayList out) {
        if(!handlePreviouslyWrittenObject(v, out)) {
            writeObjectHeader(T_UNARYOPERATION, out);
            writeOperationFields(v, out);
            write(v.operand, out);
        }
    }

    private void writeExpressionFields(Expression expr, ByteArrayList out) {
        if (expr.metadata == null) {
            out.add(METADATA_IS_NULL);
        } else if (expr.metadata instanceof HashSet) {
            out.add(METADATA_HASHSET_STRINGCOMPARISONS);
            HashSet<StringUtils.StringComparisonRecord> set = (HashSet) expr.metadata;
            writeInt(set.size(), out);
            for (StringUtils.StringComparisonRecord record : set) { //TODO iterator performance is probably bad...
                writeUTF(record.stringCompared, out);
                out.add((byte) record.comparisionType.ordinal());
            }
        } else if (expr.metadata instanceof Coverage.BranchData) {
            out.add(METADATA_BRANCH_DATA);
            Coverage.BranchData b = (Coverage.BranchData) expr.metadata;
            writeBoolean(b.taken, out);
            if (b.source == null) {
                writeBoolean(false, out);
            } else {
                writeBoolean(true, out);
                writeUTF(b.source, out);
            }
            writeInt(b.takenCode, out);
            writeInt(b.notTakenCode, out);
            writeInt(b.notTakenPath, out);
            writeBoolean(b.breaksLoop, out);
        } else if (expr.metadata instanceof Coverage.SwitchData) {
            Coverage.SwitchData b = (Coverage.SwitchData) expr.metadata;
            out.add(METADATA_SWITCH_DATA);
            writeBoolean(b.taken, out);
            if (b.source == null) {
                writeBoolean(false, out);
            } else {
                writeBoolean(true, out);
                writeUTF(b.source, out);
            }
            writeInt(b.switchID, out);
            writeInt(b.numArms, out);
            writeInt(b.arm, out);

        } else {
            throw new UnsupportedOperationException("Unexpected metadata type: " + expr.metadata);
        }
    }

    private void writeVariableFields(Variable variable, ByteArrayList out) {
        writeExpressionFields(variable, out);
        writeUTF(variable.name, out);
    }

    private void writeOperationFields(Operation operation, ByteArrayList out) {
        writeExpressionFields(operation, out);
        writeShort((short) operation.operator.ordinal(), out);
        switch (operation.operator) {
            case I2BV:
            case SIGN_EXT:
            case ZERO_EXT:
                writeInt(operation.immediate1, out);
                break;
            case EXTRACT:
                writeInt(operation.immediate1, out);
                writeInt(operation.immediate2, out);
                break;
        }
    }

    public void clear() {
        buff.reset();
        backReferences.clear();
    }

    public void clearBuffer() {
        buff.trim(100 * 1024);
    }

    //Object header info
    public static final byte T_TABLEREFERENCE = 0;
    public static final byte T_ARRAYVARIABLE = 1;
    public static final byte T_BINARYOPERATION = 2;
    public static final byte T_BOOLCONSTANT = 3;
    public static final byte T_BVCONSTANT = 4;
    public static final byte T_BVVARIABLE = 5;
    public static final byte T_FUNCTIONCALL = 6;
    public static final byte T_INTCONSTANT = 7;
    public static final byte T_INTVARIABLE = 8;
    public static final byte T_NARYOPERATION = 9;
    public static final byte T_REALCONSTANT = 10;
    public static final byte T_REALVARIABLE = 11;
    public static final byte T_STRINGCONSTANT = 12;
    public static final byte T_STRINGVARIABLE = 13;
    public static final byte T_UNARYOPERATION = 14;

    public static final byte METADATA_IS_NULL = 0;
    public static final byte METADATA_HASHSET_STRINGCOMPARISONS = 1;
    public static final byte METADATA_BRANCH_DATA = 2;
    public static final byte METADATA_SWITCH_DATA = 3;

    /* This code adapted from: Apache Harmony's DataOutpputStream, distributed under Apache License, Version 2.0
     *
     *  Licensed to the Apache Software Foundation (ASF) under one or more
     *  contributor license agreements.  See the NOTICE file distributed with
     *  this work for additional information regarding copyright ownership.
     *  The ASF licenses this file to You under the Apache License, Version 2.0
     *  (the "License"); you may not use this file except in compliance with
     *  the License.  You may obtain a copy of the License at
     *
     *     http://www.apache.org/licenses/LICENSE-2.0
     *
     *  Unless required by applicable law or agreed to in writing, software
     *  distributed under the License is distributed on an "AS IS" BASIS,
     *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     *  See the License for the specific language governing permissions and
     *  limitations under the License.
     */
    public void writeBoolean(boolean val, ByteArrayList out) {
        out.add(val ? (byte) 1 : (byte) 0);
    }

    public void writeDouble(double val, ByteArrayList out) {
        writeLong(Double.doubleToLongBits(val), out);
    }

    public void writeChar(char c, ByteArrayList out) {
        out.add((byte) (c >> 8));
        out.add((byte) c);
    }

    public void writeInt(int val, ByteArrayList out) {
        out.add((byte) (val >> 24));
        out.add((byte) (val >> 16));
        out.add((byte) (val >> 8));
        out.add((byte) (val));
    }

    private void writeLong(long val, ByteArrayList out) {
        out.add((byte) (val >> 56));
        out.add((byte) (val >> 48));
        out.add((byte) (val >> 40));
        out.add((byte) (val >> 32));
        out.add((byte) (val >> 24));
        out.add((byte) (val >> 16));
        out.add((byte) (val >> 8));
        out.add((byte) (val));
    }

    public void writeShort(short val, ByteArrayList out) {
        out.add((byte) (val >> 8));
        out.add((byte) val);
    }

    public void writeUTF(String str, ByteArrayList out) {
        long bytes = countUTFBytes(str);
        if (bytes > 65535)
            throw new IllegalArgumentException("UTF string too long: " + bytes);
        writeShort((short) bytes, out);
        int length = TaintUtils.stringLength(str);
        for (int i = 0; i < length; i++) {
            int charValue = TaintUtils.charAt(str, i);
            if (charValue > 0 && charValue <= 127) {
                out.add((byte) charValue);
            } else if (charValue <= 2047) {
                out.add((byte) (0xc0 | (0x1f & (charValue >> 6))));
                out.add((byte) (0x80 | (0x3f & charValue)));
            } else {
                out.add((byte) (0xe0 | (0x0f & (charValue >> 12))));
                out.add((byte) (0x80 | (0x3f & (charValue >> 6))));
                out.add((byte) (0x80 | (0x3f & charValue)));
            }
        }

    }

    private long countUTFBytes(String str) {
        int utfCount = 0, length = TaintUtils.stringLength(str);
        for (int i = 0; i < length; i++) {
            int charValue = TaintUtils.charAt(str, i);
            if (charValue > 0 && charValue <= 127) {
                utfCount++;
            } else if (charValue <= 2047) {
                utfCount += 2;
            } else {
                utfCount += 3;
            }
        }
        return utfCount;
    }
}
