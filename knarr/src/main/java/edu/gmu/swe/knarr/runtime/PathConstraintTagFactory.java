package edu.gmu.swe.knarr.runtime;

import edu.columbia.cs.psl.phosphor.Configuration;
import edu.columbia.cs.psl.phosphor.TaintUtils;
import edu.columbia.cs.psl.phosphor.instrumenter.LocalVariableManager;
import edu.columbia.cs.psl.phosphor.instrumenter.TaintAdapter;
import edu.columbia.cs.psl.phosphor.instrumenter.TaintPassingMV;
import edu.columbia.cs.psl.phosphor.instrumenter.TaintTagFactory;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Label;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.MethodVisitor;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Opcodes;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.tree.FrameNode;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.util.Printer;
import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.struct.*;

import java.util.HashMap;

import static edu.gmu.swe.knarr.runtime.RedirectMethodsTaintAdapter.MODEL_UTILS_TYPE;

public class PathConstraintTagFactory implements TaintTagFactory, Opcodes, StringOpcodes {
    private final static Type STRING_TYPE = Type.getType(String.class);
	@Override
	public void fieldOp(int opcode, String owner, String name, String desc, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV ta, boolean trackedLoad) {

	}
	@Override
	public Taint<?> getAutoTaint(String source) {
		return new Taint<String>(source);
	}
	@Override
	public void methodOp(int opcode, String owner, String name, String desc, boolean itfc, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV ta) {
	}

	@Override
	public boolean isInternalTaintingClass(String classname) {
		return classname.equals("edu/gmu/swe/knarr/runtime/Symbolicator");
	}

	@Override
	public void instrumentationStarting(String className) {

	}

	@Override
	public void instrumentationEnding(String className) {
	}

	public static boolean isRunning = true;
	@Override
	public boolean isIgnoredClass(String classname) {
		return !classname.equals(Type.getType(JunitAssert.class).getInternalName())
				&& ((classname.startsWith("edu/gmu/swe/knarr") && (isRunning || !classname.equals("edu/gmu/swe/knarr/runtime/ModelUtils")))
						|| classname.startsWith("gov/nasa/jpf/"));
	}

	@Override
	public void generateEmptyTaint(MethodVisitor mv) {
		mv.visitInsn(Opcodes.ACONST_NULL);
	}

	@Override
	public void generateEmptyTaintArray(Object[] array, int dimensions) {

	}

	private void unwrap(Type t, String oldDesc, MethodVisitor mv) {
		mv.visitInsn(DUP);
		mv.visitFieldInsn(GETFIELD, t.getInternalName(), "taint", Configuration.TAINT_TAG_DESC);
		mv.visitInsn(SWAP);
		mv.visitFieldInsn(GETFIELD, t.getInternalName(), "val", oldDesc);

	}

	@Override
	public void stackOp(int opcode, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV ta) {
		switch (opcode) {
		case Opcodes.FADD:
		case Opcodes.FREM:
		case Opcodes.FSUB:
		case Opcodes.FMUL:
		case Opcodes.FDIV:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			Type holder = Type.getType(TaintedFloatWithObjTag.class);
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, Printer.OPCODES[opcode], "(" + Configuration.TAINT_TAG_DESC + "F" + Configuration.TAINT_TAG_DESC + "F" + holder.getDescriptor() + ")" + holder.getDescriptor(), false);
			unwrap(holder, "F", mv);
			break;
		case Opcodes.DADD:
		case Opcodes.DSUB:
		case Opcodes.DMUL:
		case Opcodes.DDIV:
		case Opcodes.DREM:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = Type.getType(TaintedDoubleWithObjTag.class);
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, Printer.OPCODES[opcode], "(" + Configuration.TAINT_TAG_DESC + "D" + Configuration.TAINT_TAG_DESC + "D" + holder.getDescriptor() + ")" + holder.getDescriptor(), false);
			unwrap(holder, "D", mv);
			break;
		case Opcodes.LSHL:
		case Opcodes.LUSHR:
		case Opcodes.LSHR:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = Type.getType(TaintedLongWithObjTag.class);
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, Printer.OPCODES[opcode], "(" + Configuration.TAINT_TAG_DESC + "J" + Configuration.TAINT_TAG_DESC + "I" + holder.getDescriptor() + ")" + holder.getDescriptor(), false);
			unwrap(holder, "J", mv);
			break;
		case Opcodes.LSUB:
		case Opcodes.LMUL:
		case Opcodes.LADD:
		case Opcodes.LDIV:
		case Opcodes.LREM:
		case Opcodes.LAND:
		case Opcodes.LOR:
		case Opcodes.LXOR:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = Type.getType(TaintedLongWithObjTag.class);
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, Printer.OPCODES[opcode], "(" + Configuration.TAINT_TAG_DESC + "J" + Configuration.TAINT_TAG_DESC + "J" + holder.getDescriptor() + ")" + holder.getDescriptor(), false);
			unwrap(holder, "J", mv);
			break;
		case Opcodes.INEG:
		case Opcodes.FNEG:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			mv.visitInsn(opcode);
			mv.visitInsn(SWAP);
			mv.visitIntInsn(BIPUSH, opcode);
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "registerUnaryOp", "(" + Configuration.TAINT_TAG_DESC + "I)" + Configuration.TAINT_TAG_DESC, false);
			mv.visitInsn(SWAP);
			break;
		case Opcodes.LNEG:
		case Opcodes.DNEG:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			mv.visitInsn(opcode);
			// VV T
			mv.visitInsn(DUP2_X1);
			mv.visitInsn(POP2);
			// T VV
			mv.visitIntInsn(BIPUSH, opcode);
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "registerUnaryOp", "(" + Configuration.TAINT_TAG_DESC + "I)" + Configuration.TAINT_TAG_DESC, false);
			mv.visitInsn(DUP_X2);
			mv.visitInsn(POP);
			break;
		case Opcodes.I2B:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = TaintUtils.getContainerReturnType("B");
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "I2B", "(" + Configuration.TAINT_TAG_DESC + "I" + Type.getDescriptor(TaintedByteWithObjTag.class) + ")" + Type.getDescriptor(TaintedByteWithObjTag.class), false);
			unwrap(holder, "B", mv);
			ta.getAnalyzer().setTopOfStackTagged();
			break;
		case Opcodes.I2C:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = TaintUtils.getContainerReturnType("C");
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "I2C", "(" + Configuration.TAINT_TAG_DESC + "I" + Type.getDescriptor(TaintedCharWithObjTag.class) + ")" + Type.getDescriptor(TaintedCharWithObjTag.class), false);
			unwrap(holder, "C", mv);
			ta.getAnalyzer().setTopOfStackTagged();
			break;
		case Opcodes.I2S:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = TaintUtils.getContainerReturnType("S");
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "I2S", "(" + Configuration.TAINT_TAG_DESC + "I" + Type.getDescriptor(TaintedShortWithObjTag.class) + ")" + Type.getDescriptor(TaintedShortWithObjTag.class), false);
			unwrap(holder, "S", mv);
			ta.getAnalyzer().setTopOfStackTagged();
			break;
		case Opcodes.I2L:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = TaintUtils.getContainerReturnType("J");
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "I2L", "(" + Configuration.TAINT_TAG_DESC + "I" + Type.getDescriptor(TaintedLongWithObjTag.class) + ")" + Type.getDescriptor(TaintedLongWithObjTag.class), false);
			unwrap(holder, "J", mv);
			ta.getAnalyzer().setTopOfStackTagged();
			break;
		case Opcodes.I2F:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = TaintUtils.getContainerReturnType("F");
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "I2F", "(" + Configuration.TAINT_TAG_DESC + "I" + Type.getDescriptor(TaintedFloatWithObjTag.class) + ")" + Type.getDescriptor(TaintedFloatWithObjTag.class), false);
			unwrap(holder, "F", mv);
			ta.getAnalyzer().setTopOfStackTagged();
			break;
		case Opcodes.I2D:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = TaintUtils.getContainerReturnType("D");
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "I2D", "(" + Configuration.TAINT_TAG_DESC + "I" + Type.getDescriptor(TaintedDoubleWithObjTag.class) + ")" + Type.getDescriptor(TaintedDoubleWithObjTag.class), false);
			unwrap(holder, "D", mv);
			ta.getAnalyzer().setTopOfStackTagged();
			break;
		case Opcodes.F2I:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = TaintUtils.getContainerReturnType("I");
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "F2I", "(" + Configuration.TAINT_TAG_DESC + "F" + Type.getDescriptor(TaintedIntWithObjTag.class) + ")" + Type.getDescriptor(TaintedIntWithObjTag.class), false);
			unwrap(holder, "I", mv);
			ta.getAnalyzer().setTopOfStackTagged();
			break;
		case Opcodes.F2L:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = TaintUtils.getContainerReturnType("J");
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "F2L", "(" + Configuration.TAINT_TAG_DESC + "F" + Type.getDescriptor(TaintedLongWithObjTag.class) + ")" + Type.getDescriptor(TaintedLongWithObjTag.class), false);
			unwrap(holder, "J", mv);
			ta.getAnalyzer().setTopOfStackTagged();
			break;
		case Opcodes.F2D:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = TaintUtils.getContainerReturnType("D");
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "F2D", "(" + Configuration.TAINT_TAG_DESC + "F" + Type.getDescriptor(TaintedDoubleWithObjTag.class) + ")" + Type.getDescriptor(TaintedDoubleWithObjTag.class), false);
			unwrap(holder, "D", mv);
			ta.getAnalyzer().setTopOfStackTagged();
			break;
		case Opcodes.L2I:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = TaintUtils.getContainerReturnType("I");
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "L2I", "(" + Configuration.TAINT_TAG_DESC + "J" + Type.getDescriptor(TaintedIntWithObjTag.class) + ")" + Type.getDescriptor(TaintedIntWithObjTag.class), false);
			unwrap(holder, "I", mv);
			ta.getAnalyzer().setTopOfStackTagged();
			break;
		case Opcodes.L2F:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = TaintUtils.getContainerReturnType("F");
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "L2F", "(" + Configuration.TAINT_TAG_DESC + "J" + Type.getDescriptor(TaintedFloatWithObjTag.class) + ")" + Type.getDescriptor(TaintedFloatWithObjTag.class), false);
			unwrap(holder, "F", mv);
			ta.getAnalyzer().setTopOfStackTagged();
			break;
		case Opcodes.L2D:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = TaintUtils.getContainerReturnType("D");
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "L2D", "(" + Configuration.TAINT_TAG_DESC + "J" + Type.getDescriptor(TaintedDoubleWithObjTag.class) + ")" + Type.getDescriptor(TaintedDoubleWithObjTag.class), false);
			unwrap(holder, "D", mv);
			ta.getAnalyzer().setTopOfStackTagged();
			break;
		case Opcodes.D2I:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = TaintUtils.getContainerReturnType("I");
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "D2I", "(" + Configuration.TAINT_TAG_DESC + "D" + Type.getDescriptor(TaintedIntWithObjTag.class) + ")" + Type.getDescriptor(TaintedIntWithObjTag.class), false);
			unwrap(holder, "I", mv);
			ta.getAnalyzer().setTopOfStackTagged();
			break;
		case Opcodes.D2F:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = TaintUtils.getContainerReturnType("F");
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "D2F", "(" + Configuration.TAINT_TAG_DESC + "D" + Type.getDescriptor(TaintedFloatWithObjTag.class) + ")" + Type.getDescriptor(TaintedFloatWithObjTag.class), false);
			unwrap(holder, "F", mv);
			ta.getAnalyzer().setTopOfStackTagged();
			break;
		case Opcodes.D2L:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = TaintUtils.getContainerReturnType("J");
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "D2L", "(" + Configuration.TAINT_TAG_DESC + "D" + Type.getDescriptor(TaintedLongWithObjTag.class) + ")" + Type.getDescriptor(TaintedLongWithObjTag.class), false);
			unwrap(holder, "J", mv);
			ta.getAnalyzer().setTopOfStackTagged();
			break;
		case Opcodes.LCMP:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(Type.getType(TaintedIntWithObjTag.class)));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "LCMP", "(" + Configuration.TAINT_TAG_DESC + "J" + Configuration.TAINT_TAG_DESC + "J" + Type.getDescriptor(TaintedIntWithObjTag.class) + ")" + Type.getDescriptor(TaintedIntWithObjTag.class), false);
			ta.unwrapTaintedInt();
			break;
		case Opcodes.DCMPL:
		case Opcodes.DCMPG:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(Type.getType(TaintedIntWithObjTag.class)));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, Printer.OPCODES[opcode], "(" + Configuration.TAINT_TAG_DESC + "D" + Configuration.TAINT_TAG_DESC + "D" + Type.getDescriptor(TaintedIntWithObjTag.class) + ")" + Type.getDescriptor(TaintedIntWithObjTag.class), false);
			ta.unwrapTaintedInt();
			break;
		case Opcodes.FCMPG:
		case Opcodes.FCMPL:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(Type.getType(TaintedIntWithObjTag.class)));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, Printer.OPCODES[opcode], "(" + Configuration.TAINT_TAG_DESC + "F" + Configuration.TAINT_TAG_DESC + "F" + Type.getDescriptor(TaintedIntWithObjTag.class) + ")" + Type.getDescriptor(TaintedIntWithObjTag.class), false);
			ta.unwrapTaintedInt();
			break;
		case Opcodes.ARRAYLENGTH:
			Type arrType = TaintAdapter.getTypeForStackType(ta.getAnalyzer().stack.get(ta.getAnalyzer().stack.size() - 1));
			{
				boolean loaded = false;
				if (arrType.getElementType().getSort() != Type.OBJECT) {
					// TA A
					loaded = true;
					if (Configuration.MULTI_TAINTING && Configuration.IMPLICIT_TRACKING) {
						mv.visitInsn(SWAP);
						mv.visitInsn(POP);
						mv.visitInsn(DUP);
						mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintUtils.class), "getTaintObj", "(Ljava/lang/Object;)" + Configuration.TAINT_TAG_DESC, false);
						mv.visitInsn(SWAP);
					} else {
						mv.visitInsn(SWAP);
						mv.visitInsn(POP);
						mv.visitInsn(Configuration.NULL_TAINT_LOAD_OPCODE);
						if (Configuration.MULTI_TAINTING)
							mv.visitTypeInsn(CHECKCAST, Configuration.TAINT_TAG_INTERNAL_NAME);
						mv.visitInsn(SWAP);
					}
					// A
				}
				if (!loaded) {
					mv.visitInsn(DUP);
					if (Configuration.MULTI_TAINTING && Configuration.IMPLICIT_TRACKING) {
						mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintUtils.class), "getTaintObj", "(Ljava/lang/Object;)" + Configuration.TAINT_TAG_DESC, false);
					} else {
						mv.visitInsn(POP);
						mv.visitInsn(Configuration.NULL_TAINT_LOAD_OPCODE);
						if (Configuration.MULTI_TAINTING)
							mv.visitTypeInsn(CHECKCAST, Configuration.TAINT_TAG_INTERNAL_NAME);
					}
					mv.visitInsn(SWAP);
				}
				mv.visitInsn(opcode);

			}
			break;
		case Opcodes.IADD:
		case Opcodes.ISUB:
		case Opcodes.IMUL:
		case Opcodes.IDIV:
		case Opcodes.IREM:
		case Opcodes.ISHL:
		case Opcodes.ISHR:
		case Opcodes.IUSHR:
		case Opcodes.IOR:
		case Opcodes.IAND:
		case Opcodes.IXOR:
			if (!ta.topCarriesTaint()) {
				mv.visitInsn(opcode);
				break;
			}
			holder = TaintUtils.getContainerReturnType("I");
			mv.visitVarInsn(ALOAD, lvs.getPreAllocedReturnTypeVar(holder));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, Printer.OPCODES[opcode], "(" + Configuration.TAINT_TAG_DESC + "I" + Configuration.TAINT_TAG_DESC + "I" + holder.getDescriptor() + ")" + holder.getDescriptor(), false);
			unwrap(holder, "I", mv);
			break;
		case RETURN:
		case IRETURN:
		case FRETURN:
		case ARETURN:
		case LRETURN:
		case DRETURN:
			break;
		case LALOAD:
		case DALOAD:
		case IALOAD:
		case FALOAD:
		case BALOAD:
		case CALOAD:
		case SALOAD:
		case AALOAD:
			//Taint? Array Taint Index
            int tmp = lvs.getTmpLV();
            mv.visitVarInsn(ISTORE, tmp);

			//Taint? Array Taint
			mv.visitInsn(DUP2);

			//Taint? Array Taint Array Taint
			mv.visitVarInsn(ILOAD, tmp);
			//Taint? Array Taint Array Taint Index
			int arrayAccessID = Coverage.instance.getNewLocationId();
			mv.visitLdcInsn(arrayAccessID);
			getSourceInfo(mv, "array");
			mv.visitMethodInsn(INVOKESTATIC, MODEL_UTILS_TYPE.getInternalName(), "checkArrayAccess$$PHOSPHORTAGGED", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Object.class), Type.getType(Taint.class), Type.INT_TYPE, Type.INT_TYPE, STRING_TYPE), false);
			//Taint? Array Taint
			mv.visitInsn(POP);
			//Taint? Array Index
            mv.visitVarInsn(ILOAD, tmp);
			lvs.freeTmpLV(tmp);
			break;
		case AASTORE:
			//Array Taint Index Val
			mv.visitInsn(DUP2_X1);
			//Array Index Val Taint Index Val
			mv.visitInsn(POP2);
			//Array Index Val Taint
			mv.visitInsn(POP);
			//Array Index Val
			break;
		default:
			throw new UnsupportedOperationException(Printer.OPCODES[opcode]);
		}
	}

	static int invertOpcode(int opcode) {
		switch (opcode) {
		case Opcodes.IFEQ:
			return Opcodes.IFNE;
		case Opcodes.IFNE:
			return Opcodes.IFEQ;
		case Opcodes.IFLT:
			return Opcodes.IFGE;
		case Opcodes.IFGE:
			return Opcodes.IFLT;
		case Opcodes.IFGT:
			return Opcodes.IFLE;
		case Opcodes.IFLE:
			return Opcodes.IFGT;
		case Opcodes.IF_ICMPEQ:
			return Opcodes.IF_ICMPNE;
		case Opcodes.IF_ICMPNE:
			return Opcodes.IF_ICMPEQ;
		case Opcodes.IF_ICMPLT:
			return Opcodes.IF_ICMPGE;
		case Opcodes.IF_ICMPGE:
			return Opcodes.IF_ICMPLT;
		case Opcodes.IF_ICMPGT:
			return Opcodes.IF_ICMPLE;
		case Opcodes.IF_ICMPLE:
			return Opcodes.IF_ICMPGT;
		case Opcodes.IF_ACMPEQ:
			return Opcodes.IF_ACMPNE;
		case Opcodes.IF_ACMPNE:
			return IF_ACMPEQ;
		default:
			throw new IllegalArgumentException("Got: " + opcode);
		}
	}

	private HashMap<Label, Integer> labelToID = new HashMap<>();

	@Override
	public void jumpOp(int opcode, int branchStarting, Label label, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV ta) {
	    int takenID, notTakenID, loop;
		if (enableCov) {
			notTakenID = Coverage.instance.getNewLocationId();
			Integer aux = labelToID.get(label);
			if (aux == null) {
			    aux = Coverage.instance.getNewLocationId();
			    labelToID.put(label, aux);
			}
			takenID = aux;
			loop = (breaksLoop ? 1 : 0);
        } else {
			notTakenID = -1;
			takenID = -1;
			loop = 0;
		}
		switch (opcode) {
		case Opcodes.IFEQ:
		case Opcodes.IFNE:
		case Opcodes.IFLT:
		case Opcodes.IFGE:
		case Opcodes.IFGT:
		case Opcodes.IFLE:
			// top is val, taint
			boolean hasFrameAtEnd = ta.getAnalyzer().isFollowedByFrame;
			Label willJump = new Label();
			Label untainted = new Label();
			Label originalEnd = new Label();

			mv.visitInsn(SWAP);
			mv.visitInsn(DUP);
			mv.visitJumpInsn(IFNULL, untainted);
			FrameNode fn2 = ta.getCurrentFrameNode();
			mv.visitInsn(SWAP);
			mv.visitJumpInsn(opcode, willJump);
			FrameNode fn = ta.getCurrentFrameNode();
			// Jump will not be taken
//			mv.visitIntInsn(SIPUSH, invertOpcode(opcode));
			mv.visitIntInsn(SIPUSH, opcode);
			mv.visitLdcInsn(takenID);
			mv.visitLdcInsn(notTakenID);
			mv.visitLdcInsn(loop);
			mv.visitInsn(ICONST_0);
			getSourceInfo(mv);
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addConstraint", "(" + Configuration.TAINT_TAG_DESC + "IIIZZ" + STRING_TYPE.getDescriptor() + ")V", false);
			FrameNode fn3 = ta.getCurrentFrameNode();

			mv.visitJumpInsn(GOTO, originalEnd);
			mv.visitLabel(willJump);
			// need frame
			ta.acceptFn(fn);
			// jump will be taken
			mv.visitIntInsn(SIPUSH, opcode);
			mv.visitLdcInsn(takenID);
			mv.visitLdcInsn(notTakenID);
			mv.visitLdcInsn(loop);
			mv.visitInsn(ICONST_1);
			getSourceInfo(mv);
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addConstraint", "(" + Configuration.TAINT_TAG_DESC + "IIIZZ" + STRING_TYPE.getDescriptor() + ")V", false);
			mv.visitJumpInsn(GOTO, label);
			mv.visitLabel(untainted);
			// need frame
			ta.acceptFn(fn2);

			mv.visitInsn(POP);
			mv.visitJumpInsn(opcode, label);
			mv.visitLabel(originalEnd);

			// maybe its a problem when the next thing that happens is another
			// jump?
			if(!hasFrameAtEnd)
				ta.acceptFn(fn3);

			break;
		case Opcodes.IF_ICMPEQ:
		case Opcodes.IF_ICMPNE:
		case Opcodes.IF_ICMPLT:
		case Opcodes.IF_ICMPGE:
		case Opcodes.IF_ICMPGT:
		case Opcodes.IF_ICMPLE:
			// top is val, taint, val, taint
			int tmp = lvs.getTmpLV(Type.INT_TYPE);
			mv.visitVarInsn(ISTORE, tmp);
			// T1 V2 T2
			mv.visitInsn(SWAP);
			// V2 T1 T2

			mv.visitVarInsn(ILOAD, tmp);
			// V1 V2 T1 T2
			mv.visitInsn(DUP2);
			Label isFalse = new Label();
			mv.visitJumpInsn(invertOpcode(opcode), isFalse);
			fn = ta.getCurrentFrameNode();
			// Jump will be taken
			mv.visitIntInsn(SIPUSH, opcode);
			mv.visitLdcInsn(takenID);
			mv.visitLdcInsn(notTakenID);
			mv.visitLdcInsn(loop);
			mv.visitInsn(ICONST_1);
			getSourceInfo(mv);
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addConstraint", "(" + Configuration.TAINT_TAG_DESC + Configuration.TAINT_TAG_DESC + "IIIIIZZ" + STRING_TYPE.getDescriptor() + ")V", false);
			mv.visitJumpInsn(GOTO, label);
			mv.visitLabel(isFalse);
			ta.acceptFn(fn);
			// jump will not be taken
//			mv.visitIntInsn(SIPUSH, invertOpcode(opcode));
			mv.visitIntInsn(SIPUSH, opcode);
			mv.visitLdcInsn(takenID);
			mv.visitLdcInsn(notTakenID);
			mv.visitLdcInsn(loop);
			mv.visitInsn(ICONST_0);
			getSourceInfo(mv);
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addConstraint", "(" + Configuration.TAINT_TAG_DESC + Configuration.TAINT_TAG_DESC + "IIIIIZZ" + STRING_TYPE.getDescriptor() + ")V", false);

			lvs.freeTmpLV(tmp);
			break;
			case Opcodes.IF_ACMPEQ:
			case Opcodes.IF_ACMPNE:
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(TaintUtils.class), "ensureUnboxed", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
				mv.visitInsn(SWAP);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(TaintUtils.class), "ensureUnboxed", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
				mv.visitInsn(SWAP);
				mv.visitJumpInsn(opcode, label);
				break;
		// case Opcodes.IF_ACMPEQ:
		// case Opcodes.IF_ACMPNE:
		// Type typeOnStack = getTopOfStackType();
		//
		// tmp = lvs.getTmpLV(typeOnStack);
		// int taint1 = lvs.getTmpLV(Type.INT_TYPE);
		// typeOnStack = getTopOfStackType();
		//
		// //Top O1 T1? O2 T2?
		// if (typeOnStack.getSort() == Type.ARRAY &&
		// typeOnStack.getElementType().getSort() != Type.OBJECT &&
		// typeOnStack.getDimensions() == 1) {
		// //O1 T1
		// mv.visitVarInsn(ASTORE, tmp);
		// mv.visitMethodInsn(Opcodes.INVOKESTATIC, ArrayHelper.INTERNAL_NAME,
		// "getArrayNullnessExpression",
		// "(Ljava/lang/Object;)Ljava/lang/Object;", false);
		// mv.visitTypeInsn(CHECKCAST, Configuration.TAINT_TAG_INTERNAL_NAME);
		// mv.visitVarInsn(ASTORE, taint1);
		// //.
		// }
		// else
		// {
		// //O1
		// mv.visitInsn(DUP);
		// //O1 O1
		// mv.visitMethodInsn(INVOKESTATIC, TaintUtils.INTERNAL_NAME,
		// "getTaintObj", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
		// mv.visitTypeInsn(CHECKCAST, Configuration.TAINT_TAG_INTERNAL_NAME);
		// //T1 O1
		// mv.visitVarInsn(ASTORE, taint1);
		// mv.visitVarInsn(ASTORE, tmp);
		//
		// }
		// //O2 (t2?)
		// Type secondOnStack = getTopOfStackType();
		// if (secondOnStack.getSort() == Type.ARRAY &&
		// secondOnStack.getElementType().getSort() != Type.OBJECT &&
		// typeOnStack.getDimensions() == 1) {
		// //O2 T2
		// mv.visitInsn(SWAP);
		// mv.visitMethodInsn(Opcodes.INVOKESTATIC, ArrayHelper.INTERNAL_NAME,
		// "getArrayNullnessExpression",
		// "(Ljava/lang/Object;)Ljava/lang/Object;", false);
		// mv.visitTypeInsn(CHECKCAST, Configuration.TAINT_TAG_INTERNAL_NAME);
		// mv.visitInsn(SWAP);
		// }
		// else
		// {
		// mv.visitInsn(DUP);
		// //O2 O2
		// mv.visitMethodInsn(INVOKESTATIC, TaintUtils.INTERNAL_NAME,
		// "getTaintObj", "(Ljava/lang/Object;)Ljava/lang/Object;", false);;
		// mv.visitTypeInsn(CHECKCAST, Configuration.TAINT_TAG_INTERNAL_NAME);
		// //T2 O2
		// mv.visitInsn(SWAP);
		// //O2 T2
		// }
		// mv.visitVarInsn(ALOAD, taint1);
		// //T1 O2 T2
		// mv.visitInsn(SWAP);
		// //O2 T1 T2
		// mv.visitVarInsn(ALOAD, tmp);
		//
		// mv.visitInsn(DUP2); //o1 o2 o1 o2 t1 t2
		// lvs.freeTmpLV(tmp);
		// lvs.freeTmpLV(taint1);
		//
		//
		// Label isFalse = new Label();
		// mv.visitJumpInsn(invertOpcode(opcode), isFalse);
		// Object[] locals = removeLongsDoubleTopVal(analyzer.locals);
		// Object[] stack = removeLongsDoubleTopVal(analyzer.stack);
		// //Jump will be taken
		// mv.visitIntInsn(BIPUSH, opcode);
		// mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME,
		// "addConstraint", "(" + Configuration.TAINT_TAG_DESC +
		// Configuration.TAINT_TAG_DESC
		// + "Ljava/lang/Object;Ljava/lang/Object;I)V", false);
		// mv.visitJumpInsn(GOTO, label);
		// mv.visitLabel(isFalse);
		// mv.visitFrame(Opcodes.F_NEW, locals.length, locals, stack.length,
		// stack);
		//
		// mv.visitIntInsn(BIPUSH, invertOpcode(opcode));
		// mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME,
		// "addConstraint", "(" + Configuration.TAINT_TAG_DESC +
		// Configuration.TAINT_TAG_DESC
		// + "Ljava/lang/Object;Ljava/lang/Object;I)V", false);
		//
		// // //top is val (taint?), val (taint?)
		// // retrieveTopOfStackTaint();
		// // //Top is val taint val (taint?)
		// // tmp = lvs.getTmpLV(typeOnStack);
		// // mv.visitVarInsn(ASTORE, tmp);
		// // int taint1 = lvs.getTmpLV(Type.INT_TYPE);
		// // mv.visitVarInsn(ASTORE, taint1);
		// // // O2
		// // retrieveTopOfStackTaint(); //O2 T2
		// // mv.visitVarInsn(ALOAD, taint1); //O2 T1 T2
		// // mv.visitInsn(SWAP);
		// // mv.visitVarInsn(ALOAD, tmp); //O1 O2 T1 T2
		// // mv.visitInsn(DUP2); //o1 o2 o1 o2 t1 t2
		// //
		// // lvs.freeTmpLV(tmp);
		// // lvs.freeTmpLV(taint1);
		// //
		// // Label isFalse = new Label();
		// // mv.visitJumpInsn(invertOpcode(opcode), isFalse);
		// // Object[] locals = removeLongsDoubleTopVal(analyzer.locals);
		// // Object[] stack = removeLongsDoubleTopVal(analyzer.stack);
		// // //Jump will be taken
		// // mv.visitIntInsn(BIPUSH, opcode);
		// // mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME,
		// "addConstraint", "(" + Configuration.TAINT_TAG_DESC +
		// Configuration.TAINT_TAG_DESC
		// // + "Ljava/lang/Object;Ljava/lang/Object;I)V", false);
		// // mv.visitJumpInsn(GOTO, label);
		// // mv.visitLabel(isFalse);
		// // mv.visitFrame(Opcodes.F_NEW, locals.length, locals, stack.length,
		// stack);
		// //
		// // //jump will not be taken
		// // mv.visitIntInsn(BIPUSH, invertOpcode(opcode));
		// // mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME,
		// "addConstraint", "(" + Configuration.TAINT_TAG_DESC +
		// Configuration.TAINT_TAG_DESC
		// // + "Ljava/lang/Object;Ljava/lang/Object;I)V", false);
		// // typeOnStack = getTopOfStackType();
		// // if (typeOnStack.getSort() == Type.ARRAY &&
		// typeOnStack.getElementType().getSort() != Type.OBJECT &&
		// typeOnStack.getDimensions() == 1) {
		// // mv.visitInsn(SWAP);
		// // mv.visitInsn(POP);
		// // }
		// // //O1 O2 (t2?)
		// // Type secondOnStack = getStackTypeAtOffset(1);
		// // if (secondOnStack.getSort() == Type.ARRAY &&
		// secondOnStack.getElementType().getSort() != Type.OBJECT &&
		// typeOnStack.getDimensions() == 1) {
		// // //O1 O2 T2
		// // mv.visitInsn(DUP2_X1);
		// // mv.visitInsn(POP2);
		// // mv.visitInsn(POP);
		// // }
		// // mv.visitJumpInsn(opcode, label);
		// break;
		case Opcodes.GOTO:
			// we don't care about goto
			mv.visitJumpInsn(opcode, label);
			break;
		// case Opcodes.IFNULL:
		// case Opcodes.IFNONNULL:
		//
		// typeOnStack = getTopOfStackType();
		// if(typeOnStack.getSort() == Type.ARRAY &&
		// typeOnStack.getElementType().getSort() != Type.OBJECT)
		// {
		// mv.visitInsn(SWAP);
		// mv.visitInsn(POP);
		// }
		//
		// mv.visitJumpInsn(opcode, label);
		//
		// break;
		default:
			if (ta.topCarriesTaint())
				throw new UnsupportedOperationException();
			mv.visitJumpInsn(opcode, label);
			// throw new IllegalArgumentException();
		}

		breaksLoop = false;
	}

	@Override
	public void iincOp(int var, int increment, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV ta) {
		int shadowVar = 0;
		if (var < ta.lastArg && TaintUtils.getShadowTaintType(ta.paramTypes[var].getDescriptor()) != null) {
			//accessing an arg; remap it
			Type localType = ta.paramTypes[var];
			if (TaintUtils.getShadowTaintType(localType.getDescriptor()) != null)
				shadowVar = var - 1;
			else
				return;
		} else {
			shadowVar = lvs.varToShadowVar.get(var);
		}

		mv.visitVarInsn(ALOAD, shadowVar);
		mv.visitLdcInsn(increment);
		mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addIincConstraint", "(" + Configuration.TAINT_TAG_DESC + "I)V", false);
	}

	@Override
	public void intOp(int opcode, int arg, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV ta) {

	}

	private boolean breaksLoop = false;

	@Override
	public void signalOp(int signal, Object option) {
		if (signal == TaintUtils.LOOP_HEADER)
			breaksLoop = true;
	}

	boolean inStringClass = false;
	String name;
	String owner;
	Type[] args;
	int line;

	private boolean enableCov = false;

	@Override
	public void methodEntered(String owner, String name, String desc, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV ta) {
		inStringClass = "java/lang/String".equals(owner);

		this.name = name;
		this.args = Type.getArgumentTypes(desc);
		this.owner = owner;
		this.line = 0;

		this.enableCov = (Coverage.isCovEnabled(owner) && !owner.startsWith("za/ac/sun/cs/green"));

		if (enableCov) {
			labelToID.clear();

			Integer id = Coverage.instance.getNewLocationId();

			mv.visitFieldInsn(GETSTATIC, Coverage.INTERNAL_NAME, "instance", Coverage.DESCRIPTOR);
			mv.visitLdcInsn(id);
			mv.visitMethodInsn(INVOKEVIRTUAL, Coverage.INTERNAL_NAME, "set", "(I)V", false);

//			mv.visitFieldInsn(GETSTATIC, Coverage.INTERNAL_NAME, "instance", Coverage.DESCRIPTOR);
//			mv.visitFieldInsn(GETFIELD, Coverage.INTERNAL_NAME, "coverage", "[I");
//			mv.visitLdcInsn(id);
//			mv.visitIntInsn(BIPUSH, 32);
//			mv.visitInsn(IDIV);
//			mv.visitInsn(DUP2);
//			mv.visitInsn(IALOAD);
//			mv.visitInsn(ICONST_1);
//			mv.visitLdcInsn(id);
//			mv.visitIntInsn(BIPUSH, 32);
//			mv.visitInsn(IREM);
//			mv.visitInsn(ISHL);
//			mv.visitInsn(IOR);
//			mv.visitInsn(IASTORE);
		}
	}

	@Override
	public void lineNumberVisited(int line) {
	    this.line = line;
	}

	@Override
	public void typeOp(int opcode, String type, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV ta) {

	}

	@Override
	public void lookupSwitch(Label dflt, int[] keys, Label[] labels, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV ta) {
		//TODO performance: it would be great to create these arrays in clinit and store them as a static field.
		//Create an int[] with all of the keys
		mv.visitLdcInsn(keys.length);
		mv.visitIntInsn(NEWARRAY, T_INT);
		for (int j = 0; j < keys.length; j++) {
			mv.visitInsn(DUP);
			mv.visitLdcInsn(j);
			mv.visitLdcInsn(keys[j]);
			mv.visitInsn(IASTORE);
		}
		int lvWithKeys = lvs.getTmpLV(Type.getType("[I"));
		mv.visitVarInsn(ASTORE, lvWithKeys);


		FrameNode fn = ta.getCurrentFrameNode();

		// Generate fresh labels
		Label[] freshLabels = new Label[labels.length];

		for (int i = 0 ; i < freshLabels.length ; i++)
			freshLabels[i] = new Label();

		Label freshDflt = new Label();

		int dfltID = Coverage.instance.getNewLocationId();
		int switchID = Coverage.instance.getNewLocationId();


		// Duplicate value, needed for later
		mv.visitInsn(DUP);

		// Issue switch
		mv.visitLookupSwitchInsn(freshDflt, keys, freshLabels);


		// Each fresh label registers the value switched on and jumps to the original label
		for (int i = 0 ; i < freshLabels.length ; i++) {
			mv.visitLabel(freshLabels[i]);
			ta.acceptFn(fn);
			// taint, null, value
			mv.visitLdcInsn(keys[i]);
			// taint, null, value, switch target
			mv.visitLdcInsn(i);

			mv.visitVarInsn(ALOAD, lvWithKeys);
			mv.visitLdcInsn(switchID);
			getSourceInfo(mv);
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addSwitchConstraint", "(" + Configuration.TAINT_TAG_DESC + "III[II" + STRING_TYPE.getDescriptor() + ")V", false);
			mv.visitJumpInsn(GOTO, labels[i]);
		}

		// Default label is not equal to any of the above
		mv.visitLabel(freshDflt);
		ta.acceptFn(fn);

		// taint, null, value
		mv.visitInsn(ICONST_M1);
		// taint, null, value, switch target
		mv.visitLdcInsn(keys.length);

		mv.visitVarInsn(ALOAD, lvWithKeys);
		lvs.freeTmpLV(lvWithKeys);
		mv.visitLdcInsn(switchID);
		getSourceInfo(mv);
		mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addSwitchConstraint", "(" + Configuration.TAINT_TAG_DESC + "III[II" + STRING_TYPE.getDescriptor() + ")V", false);
		mv.visitJumpInsn(GOTO, dflt);
	}

	@Override
	public void tableSwitch(int min, int max, Label dflt, Label[] labels, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV ta) {
		//Create an int[] with all of the keys, will use later
		mv.visitLdcInsn(labels.length);
		mv.visitIntInsn(NEWARRAY, T_INT);
		for (int j = 0; j < labels.length; j++) {
			mv.visitInsn(DUP);
			mv.visitLdcInsn(j);
			mv.visitLdcInsn(min + j);
			mv.visitInsn(IASTORE);
		}
		int lvWithKeys = lvs.getTmpLV(Type.getType("[I"));
		mv.visitVarInsn(ASTORE, lvWithKeys);
		FrameNode fn = ta.getCurrentFrameNode();

		// Generate fresh labels
		Label[] freshLabels = new Label[labels.length];

		for (int i = 0 ; i < freshLabels.length ; i++)
			freshLabels[i] = new Label();

		Label freshDflt = new Label();

		// Generate coverage IDs

		int switchID = Coverage.instance.getNewLocationId();
		int dfltID = Coverage.instance.getNewLocationId();


		// Duplicate value, needed for later
		mv.visitInsn(DUP);

		// Issue switch
		mv.visitTableSwitchInsn(min, max, freshDflt, freshLabels);
		// Each fresh label registers the value switched on and jumps to the original label
		for (int i = 0 ; i < freshLabels.length ; i++) {
			mv.visitLabel(freshLabels[i]);
			ta.acceptFn(fn);
			// taint, value
			mv.visitLdcInsn(min + i);
			//taint, value, table target
			mv.visitLdcInsn(i);
			//taint ,value, table target, arm
            mv.visitVarInsn(ALOAD, lvWithKeys);

			// taint, value, tablet arget, arm, array of values
			mv.visitLdcInsn(switchID);
			// taint, value, tablet arget, arm, array of values, switch ID
			getSourceInfo(mv);
			// taint, value, tablet arget, arm, array of values, switch ID, string descriptor
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addSwitchConstraint", "(" + Configuration.TAINT_TAG_DESC + "III[II" + STRING_TYPE.getDescriptor() + ")V", false);
			mv.visitJumpInsn(GOTO, labels[i]);
		}

		// Default label is not equal to any of the above
		mv.visitLabel(freshDflt);
		ta.acceptFn(fn);

		// taint, null, value
		mv.visitInsn(ICONST_M1);
		// taint, null, value, switch target
		mv.visitLdcInsn(labels.length);
		mv.visitVarInsn(ALOAD, lvWithKeys);

		lvs.freeTmpLV(lvWithKeys);
		mv.visitLdcInsn(switchID);
		getSourceInfo(mv);
		mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addSwitchConstraint", "(" + Configuration.TAINT_TAG_DESC + "III[II" + STRING_TYPE.getDescriptor() + ")V", false);
		mv.visitJumpInsn(GOTO, dflt);


	}

	private void getSourceInfo(MethodVisitor mv) {
	    getSourceInfo(mv, null);
	}

	private void getSourceInfo(MethodVisitor mv, String pref) {
		if (this.owner.startsWith("java"))
		    mv.visitInsn(ACONST_NULL);
        else
        	mv.visitLdcInsn((pref != null ? pref : "") +
					(this.owner != null ? this.owner : "?")
					+ ":"
					+ (this.name != null ? this.name : "?")
					+ ":" + this.line);
	}

	@Override
	public void propogateTagNative(String className, int acc, String methodName, String newDesc, MethodVisitor mv) {
		int idx = 0;
		Type[] argTypes = Type.getArgumentTypes(newDesc);
		if ((acc & Opcodes.ACC_STATIC) == 0) {
			idx++;
		}
		for (Type t : argTypes) {
			if (t.getSort() == Type.ARRAY) {
				if (t.getElementType().getSort() != Type.OBJECT && t.getDimensions() == 1) {
					idx++;
				}
			} else if (t.getSort() != Type.OBJECT) {
				// switch(t.getSort())
				// {
				//
				// }
				// TODO
				// mv.visitVarInsn(Configuration.TAINT_LOAD_OPCODE, idx);
				// if (Configuration.MULTI_TAINTING) {
				// mv.visitMethodInsn(Opcodes.INVOKESTATIC,
				// Configuration.MULTI_TAINT_HANDLER_CLASS, "combineTags", "(" +
				// Configuration.TAINT_TAG_DESC + Configuration.TAINT_TAG_DESC +
				// ")" + Configuration.TAINT_TAG_DESC, false);
				// } else {
				// mv.visitInsn(Opcodes.IOR);
				// }
				idx++;
			}
			idx += t.getSize();
		}
	}

	@Override
	public void instrumentationStarting(int access, String methodName, String methodDesc) {

	}

	@Override
	public void insnIndexVisited(int offset) {
	}

	private void registerSingleStringOp(MethodVisitor mv, int op) {
		mv.visitVarInsn(ALOAD, 0);
		mv.visitIntInsn(SIPUSH, op);
		mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "registerStringOp", "(" + Configuration.TAINT_TAG_DESC + Type.getType(String.class).getDescriptor() + "I)" + Configuration.TAINT_TAG_DESC, false);
	}

	private void getTaintField(MethodVisitor mv) {
		mv.visitFieldInsn(GETFIELD, "java/lang/String", TaintUtils.TAINT_FIELD, Configuration.TAINT_TAG_DESC);
	}

	private void putTaintField(MethodVisitor mv) {
		mv.visitFieldInsn(PUTFIELD, "java/lang/String", TaintUtils.TAINT_FIELD, Configuration.TAINT_TAG_DESC);
	}
	@Override
	public void generateSetTag(MethodVisitor mv, String className) {
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitTypeInsn(Opcodes.CHECKCAST, Configuration.TAINT_TAG_INTERNAL_NAME);
		mv.visitFieldInsn(Opcodes.PUTFIELD, className, TaintUtils.TAINT_FIELD, Configuration.TAINT_TAG_DESC);
	}
}
