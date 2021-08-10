package edu.gmu.swe.knarr.internal;

import edu.gmu.swe.knarr.runtime.Coverage;
import edu.gmu.swe.knarr.runtime.StringUtils;
import za.ac.sun.cs.green.expr.*;

import java.util.HashSet;

public class ConstraintSerializer {
    private static final Integer NOT_REPEATED = new Integer(0);
    private final ByteArrayList buff = new ByteArrayList(100 * 1024);
    //private final IdentityHashMap<Object, Integer> allWrittenObjects = new IdentityHashMap<>();
    //private final ArrayList<Object> objectTable = new ArrayList<>();

    public void write(Expression expr) {
        if (expr instanceof ArrayVariable) {
            write((ArrayVariable) expr);
        } else if (expr instanceof BinaryOperation) {
            write((BinaryOperation) expr);
        } else if(expr instanceof BoolConstant){
            write((BoolConstant) expr);
        } else if(expr instanceof BVConstant){
            write((BVConstant) expr);
        } else if(expr instanceof BVVariable){
            write((BVVariable) expr);
        } else if(expr instanceof FunctionCall){
            write((FunctionCall) expr);
        } else if(expr instanceof IntConstant){
            write((IntConstant) expr);
        } else if(expr instanceof IntVariable){
            write((IntVariable) expr);
        } else if(expr instanceof NaryOperation){
            write((NaryOperation) expr);
        } else if(expr instanceof RealConstant){
            write((RealConstant) expr);
        } else if(expr instanceof RealVariable){
            write((RealVariable) expr);
        } else if(expr instanceof StringConstant){
            write((StringConstant) expr);
        } else if(expr instanceof UnaryOperation){
            write((UnaryOperation) expr);
        } else if(expr instanceof StringVariable){
            write((StringVariable) expr);
        } else{
            throw new IllegalArgumentException("Unsupported type: " + expr.getClass());
        }
    }

    public int size(){
        return buff.size();
    }
    public byte[] bytes(){
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
    private boolean handlePreviouslyWrittenObject(Expression expr) {
        //Integer existing = allWrittenObjects.put(expr, NOT_REPEATED);
        //if(existing != null){
        //    //Already was in table
        //    int newKey;
        //    if(existing == NOT_REPEATED)
        //        newKey = objectTable.size();
        //    else
        //        newKey = existing;
        //    objectTable.add(expr);
        //    allWrittenObjects.put(expr, newKey);
        //    writeTableReference(newKey);
        //    return true;
        //}
        return false;
    }



    private void writeObjectHeader(byte headerType) {
        buff.add(headerType);
    }

    private void writeTableReference(int newKey) {
        writeObjectHeader(T_TABLEREFERENCE);
        writeInt(newKey);
    }

    public void write(ArrayVariable v) {
        writeObjectHeader(T_ARRAYVARIABLE);
        writeVariableFields(v);
        String s = v.getType().getName();
        switch (s) {
            case "boolean":
                buff.add((byte) 1);
                break;
            case "byte":
                buff.add((byte) 2);
                break;
            case "short":
            case "char":
                buff.add((byte) 3);
                break;
            case "int":
                buff.add((byte) 4);
                break;
            case "long":
                buff.add((byte) 5);
                break;
            default:
                buff.add((byte) 6);
        }
    }

    public void write(BinaryOperation v) {
        writeObjectHeader(T_BINARYOPERATION);
        writeOperationFields(v);
        write(v.left);
        write(v.right);
    }

    public void write(BoolConstant v) {
        writeObjectHeader(T_BOOLCONSTANT);
        writeExpressionFields(v);
        writeBoolean(v.value);
    }

    public void write(BVConstant v) {
        writeObjectHeader(T_BVCONSTANT);
        writeExpressionFields(v);
        writeInt(v.size);
        writeLong(v.value);
    }

    public void write(BVVariable v) {
        writeObjectHeader(T_BVVARIABLE);
        writeVariableFields(v);
        writeInt(v.size);
    }

    public void write(FunctionCall v) {
        writeObjectHeader(T_FUNCTIONCALL);
        writeExpressionFields(v);
        writeUTF(v.getName());
        Expression[] arguments = v.arguments;
        writeInt(arguments.length);
        for(int i = 0; i < arguments.length; i++){
            write(arguments[i]);
        }
    }

    public void write(IntVariable v){
        writeObjectHeader(T_INTVARIABLE);
        writeVariableFields(v);
        writeInt(v.lowerBound);
        writeInt(v.upperBound);
    }
    public void write(IntConstant v) {
        writeObjectHeader(T_INTCONSTANT);
        writeExpressionFields(v);
        writeLong(v.value);
    }

    public void write(NaryOperation v) {
        writeObjectHeader(T_NARYOPERATION);
        writeOperationFields(v);
        Expression[] operands = v.operands;
        writeInt(operands.length);
        for(int i = 0; i < operands.length; i++){
            write(operands[i]);
        }
    }

    public void write(RealConstant v) {
        writeObjectHeader(T_REALCONSTANT);
        writeExpressionFields(v);
        writeDouble(v.value);
    }

    public void write(RealVariable v) {
        writeObjectHeader(T_REALVARIABLE);
        writeVariableFields(v);
        writeDouble(v.lowerBound);
        writeDouble(v.upperBound);
    }

    public void write(StringConstant v) {
        writeObjectHeader(T_STRINGCONSTANT);
        writeExpressionFields(v);
        writeUTF(v.value);
    }

    public void write(StringVariable v){
        writeObjectHeader(T_STRINGVARIABLE);
        writeVariableFields(v);
    }
    public void write(UnaryOperation v) {
        writeObjectHeader(T_UNARYOPERATION);
        writeOperationFields(v);
        write(v.operand);
    }

    private void writeExpressionFields(Expression expr) {
        if(expr.metadata == null){
            buff.add(METADATA_IS_NULL);
        } else if(expr.metadata instanceof HashSet){
            buff.add(METADATA_HASHSET_STRINGCOMPARISONS);
            HashSet<StringUtils.StringComparisonRecord> set = (HashSet) expr.metadata;
            writeInt(set.size());
            for(StringUtils.StringComparisonRecord record : set){ //TODO iterator performance is probably bad...
                writeUTF(record.stringCompared);
                buff.add((byte) record.comparisionType.ordinal());
            }
        } else if(expr.metadata instanceof Coverage.BranchData){
            buff.add(METADATA_BRANCH_DATA);
            Coverage.BranchData b = (Coverage.BranchData) expr.metadata;
            writeBoolean(b.taken);
            if(b.source == null){
                writeBoolean(false);
            }else{
                writeBoolean(true);
                writeUTF(b.source);
            }
            writeInt(b.takenCode);
            writeInt(b.notTakenCode);
            writeInt(b.notTakenPath);
            writeBoolean(b.breaksLoop);
        } else if (expr.metadata instanceof Coverage.SwitchData) {
            Coverage.SwitchData b = (Coverage.SwitchData) expr.metadata;
            buff.add(METADATA_SWITCH_DATA);
            writeBoolean(b.taken);
            if (b.source == null) {
                writeBoolean(false);
            } else {
                writeBoolean(true);
                writeUTF(b.source);
            }
            writeInt(b.switchID);
            writeInt(b.numArms);
            writeInt(b.arm);

        }else{
            throw new UnsupportedOperationException("Unexpected metadata type: " + expr.metadata);
        }
    }

    private void writeVariableFields(Variable variable) {
        writeExpressionFields(variable);
        writeUTF(variable.name);
    }

    private void writeOperationFields(Operation operation) {
        writeExpressionFields(operation);
        writeShort((short) operation.operator.ordinal());
    }

    public void clear() {
        buff.reset();
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
    public void writeBoolean(boolean val) {
        buff.add(val ? (byte) 1 : (byte) 0);
    }

    public void writeDouble(double val){
        writeLong(Double.doubleToLongBits(val));
    }

    public void writeChar(char c) {
        buff.add((byte) (c >> 8));
        buff.add((byte) c);
    }

    public void writeInt(int val) {
        buff.add((byte) (val >> 24));
        buff.add((byte) (val >> 16));
        buff.add((byte) (val >> 8));
        buff.add((byte) (val));
    }

    private void writeLong(long val) {
        buff.add((byte) (val >> 56));
        buff.add((byte) (val >> 48));
        buff.add((byte) (val >> 40));
        buff.add((byte) (val >> 32));
        buff.add((byte) (val >> 24));
        buff.add((byte) (val >> 16));
        buff.add((byte) (val >> 8));
        buff.add((byte) (val));
    }

    public void writeShort(short val){
        buff.add((byte) (val >> 8));
        buff.add((byte) val);
    }

    public void writeUTF(String str) {
        long bytes = countUTFBytes(str);
        if(bytes > 65535)
            throw new IllegalArgumentException("UTF string too long: " + bytes);
        writeShort((short) bytes);
        int length = str.length();
        for (int i = 0; i < length; i++) {
            int charValue = str.charAt(i);
            if (charValue > 0 && charValue <= 127) {
                buff.add((byte) charValue);
            } else if (charValue <= 2047) {
                buff.add((byte) (0xc0 | (0x1f & (charValue >> 6))));
                buff.add((byte) (0x80 | (0x3f & charValue)));
            } else {
                buff.add((byte) (0xe0 | (0x0f & (charValue >> 12))));
                buff.add((byte) (0x80 | (0x3f & (charValue >> 6))));
                buff.add((byte) (0x80 | (0x3f & charValue)));
            }
        }

    }
    private long countUTFBytes(String str) {
        int utfCount = 0, length = str.length();
        for (int i = 0; i < length; i++) {
            int charValue = str.charAt(i);
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
