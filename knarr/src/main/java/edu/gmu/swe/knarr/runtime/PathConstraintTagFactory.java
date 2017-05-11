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
import edu.columbia.cs.psl.phosphor.struct.TaintedDoubleWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedFloatWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedIntWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedLongWithObjTag;

public class PathConstraintTagFactory implements TaintTagFactory, Opcodes, StringOpcodes {

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

	@Override
	public boolean isIgnoredClass(String classname) {
		return classname.startsWith("edu/gmu/swe/knarr") || classname.startsWith("gov/nasa/jpf/");
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
		case Opcodes.I2L:
		case Opcodes.I2F:
		case Opcodes.I2D:
			// if(ta.topCarriesTaint())
			// {
			// mv.visitInsn(SWAP);
			// mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME,
			// "toReal", "(" + Configuration.TAINT_TAG_DESC +
			// ")"+Configuration.TAINT_TAG_DESC, false);
			// mv.visitInsn(SWAP);
			// }
			// mv.visitInsn(opcode);
			// break;
		case Opcodes.L2I:
		case Opcodes.L2F:
		case Opcodes.L2D:
		case Opcodes.F2I:
		case Opcodes.F2L:
		case Opcodes.F2D:
		case Opcodes.D2I:
		case Opcodes.D2L:
		case Opcodes.D2F:
		case Opcodes.I2B:
		case Opcodes.I2C:
		case Opcodes.I2S:
			mv.visitInsn(opcode);
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
		default:
			throw new UnsupportedOperationException();
		}
		if (inStringClass) {
			System.out.println("instr... " + this.name + " " + Printer.OPCODES[opcode]);
			switch (opcode) {
			case RETURN:
			case IRETURN:
			case FRETURN:
			case ARETURN:
			case LRETURN:
			case DRETURN:
				if (this.name.equals("<init>"))
					handleConstructor(mv);
				else if (this.name.equals("toUpperCase"))
					handleUpperLower(mv, STR_UCASE);
				else if (this.name.equals("toLowerCase"))
					handleUpperLower(mv, STR_LCASE);
				else if (this.name.equals("concat"))
					handleConcat(mv);
				else if (this.name.equals("trim"))
					handleTrim(mv);
				else if (this.name.equals("length$$PHOSPHORTAGGED"))
					handleLength(mv);
				else if (this.name.equals("equals$$PHOSPHORTAGGED"))
					handleEquals(mv);
				else if (this.name.equals("isEmpty$$PHOSPHORTAGGED"))
					handleEmpty(mv);
				else if (this.name.equals("startsWith$$PHOSPHORTAGGED"))
					handleStartsWith(mv);
				else if (this.name.equals("split"))
					handleSplit(mv);
				else if (this.name.equals("replaceAll"))
					handleReplaceAll(mv);
				else if (this.name.equals("replace"))
					handleReplace(mv);
				else if (this.name.equals("substring$$PHOSPHORTAGGED"))
					handleSubstring(mv);
				else if(this.name.equals("charAt$$PHOSPHORTAGGED"))
					handleCharAt(mv);
				// TODO: make sustring not break jvm
				break;
			}
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

	@Override
	public void jumpOp(int opcode, int branchStarting, Label label, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV ta) {
		switch (opcode) {
		case Opcodes.IFEQ:
		case Opcodes.IFNE:
		case Opcodes.IFLT:
		case Opcodes.IFGE:
		case Opcodes.IFGT:
		case Opcodes.IFLE:
			// top is val, taint
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
			mv.visitIntInsn(SIPUSH, invertOpcode(opcode));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addConstraint", "(" + Configuration.TAINT_TAG_DESC + "I)V", false);
			FrameNode fn3 = ta.getCurrentFrameNode();

			mv.visitJumpInsn(GOTO, originalEnd);
			mv.visitLabel(willJump);
			// need frame
			ta.acceptFn(fn);
			// jump will be taken
			mv.visitIntInsn(SIPUSH, opcode);
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addConstraint", "(" + Configuration.TAINT_TAG_DESC + "I)V", false);
			mv.visitJumpInsn(GOTO, label);
			mv.visitLabel(untainted);
			// need frame
			ta.acceptFn(fn2);

			mv.visitInsn(POP);
			mv.visitJumpInsn(opcode, label);
			mv.visitLabel(originalEnd);

			// maybe its a problem when the next thing that happens is another
			// jump?
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
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addConstraint", "(" + Configuration.TAINT_TAG_DESC + Configuration.TAINT_TAG_DESC + "III)V", false);
			mv.visitJumpInsn(GOTO, label);
			mv.visitLabel(isFalse);
			ta.acceptFn(fn);
			// jump will not be taken
			mv.visitIntInsn(SIPUSH, invertOpcode(opcode));
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addConstraint", "(" + Configuration.TAINT_TAG_DESC + Configuration.TAINT_TAG_DESC + "III)V", false);

			lvs.freeTmpLV(tmp);
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
	}

	@Override
	public void iincOp(int var, int increment, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV ta) {

	}

	@Override
	public void intOp(int opcode, int arg, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV ta) {

	}

	@Override
	public void signalOp(int signal, Object option) {

	}

	boolean inStringClass = false;
	String name;
	Type[] args;

	@Override
	public void methodEntered(String owner, String name, String desc, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV ta) {
		inStringClass = "java/lang/String".equals(owner);
		// System.out.println(owner + "."+name+desc);

		this.name = name;
		this.args = Type.getArgumentTypes(desc);
	}

	@Override
	public void lineNumberVisited(int line) {

	}

	@Override
	public void typeOp(int opcode, String type, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV ta) {

	}

	@Override
	public void lookupSwitch(Label dflt, int[] keys, Label[] labels, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV taintPassingMV) {
		mv.visitInsn(SWAP);
		mv.visitInsn(POP);
		mv.visitLookupSwitchInsn(dflt, keys, labels);
		// TODO record constraints through lookupswitch
	}

	@Override
	public void tableSwitch(int min, int max, Label dflt, Label[] labels, MethodVisitor mv, LocalVariableManager lvs, TaintPassingMV taintPassingMV) {
		mv.visitInsn(SWAP);
		mv.visitInsn(POP);
		mv.visitTableSwitchInsn(min, max, dflt, labels);
		// TODO record constraints through tableswitch
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
		// TODO Auto-generated method stub

	}

	@Override
	public void insnIndexVisited(int offset) {
		// TODO Auto-generated method stub

	}

	private void handleSubstring(MethodVisitor mv) {
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitVarInsn(ILOAD, 2);
		if (args.length == 4) {
			mv.visitVarInsn(ALOAD, 1);
			mv.visitVarInsn(ILOAD, 4);
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addSubstringConstraint", "(Ljava/lang/String;Ljava/lang/String;" + Configuration.TAINT_TAG_DESC + "I" + Configuration.TAINT_TAG_DESC + "I)V", false);
		} else // TODO: Fix so initiation does not fail
		{
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addSubstringConstraint", "(Ljava/lang/String;Ljava/lang/String;" + Configuration.TAINT_TAG_DESC + "I)V", false);
		}
	}
	private void handleCharAt(MethodVisitor mv) {
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitVarInsn(ILOAD, 2);
		mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addCharAtConstraint", "(Ledu/columbia/cs/psl/phosphor/struct/TaintedCharWithObjTag;Ljava/lang/String;" + Configuration.TAINT_TAG_DESC + "I)V", false);
	}

	private void handleSplit(MethodVisitor mv) {
		// super.visitInsn(DUP);
		// super.visitVarInsn(ALOAD,0);
		// super.visitVarInsn(ALOAD,1);
		// super.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME,
		// "addSplitConstraint",
		// "([Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);
	}

	private void handleReplace(MethodVisitor mv) {
		if (!args[1].getInternalName().equals("C")) // Not char
		{
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitVarInsn(ALOAD, 2);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addReplaceConstraint", "(Ljava/lang/String;Ljava/lang/CharSequence;Ljava/lang/CharSequence;Ljava/lang/String;)V", false);
		}
	}

	private void handleReplaceAll(MethodVisitor mv) {
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, 1); // From
		mv.visitVarInsn(ALOAD, 2); // To
		mv.visitVarInsn(ALOAD, 0); // Orig
		mv.visitIntInsn(SIPUSH, STR_REPLACEALL);
		mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "addReplaceAllConstraint", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V", false);
	}

	private void handleEmpty(MethodVisitor mv) {
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitIntInsn(SIPUSH, STR_EMPTY);
		mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "registerStringBooleanOp", "(Ledu/columbia/cs/psl/phosphor/struct/TaintedBooleanWithObjTag;Ljava/lang/String;I)V", false);
	}

	private void handleStartsWith(MethodVisitor mv) {
		if (args.length == 2) // Only handle starting at 0 as this moment.
		{
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitIntInsn(SIPUSH, STR_START);
			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "registerStringBooleanOp", "(Ledu/columbia/cs/psl/phosphor/struct/TaintedBooleanWithObjTag;Ljava/lang/String;Ljava/lang/Object;I)V", false);
		}
	}

	private void handleEquals(MethodVisitor mv) {
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitIntInsn(SIPUSH, STR_EQUAL);
		mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "registerStringBooleanOp", "(Ledu/columbia/cs/psl/phosphor/struct/TaintedBooleanWithObjTag;Ljava/lang/String;Ljava/lang/Object;I)V", false);
	}

	private void handleLength(MethodVisitor mv) {
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, 0);
		getTaintField(mv);
		mv.visitIntInsn(SIPUSH, STR_LEN);
		mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "registerUnaryToTaint", "(Ledu/columbia/cs/psl/phosphor/struct/TaintedPrimitiveWithObjTag;" + Configuration.TAINT_TAG_DESC + "I)V", false);
	}

	private void handleTrim(MethodVisitor mv) {
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, 0);
		getTaintField(mv);
		registerSingleStringOp(mv, STR_TRIM);
		putTaintField(mv);
	}

	private void handleUpperLower(MethodVisitor mv, int opcode) {
		if (args.length == 1) {
			mv.visitInsn(DUP);
			mv.visitVarInsn(ALOAD, 0);
			getTaintField(mv);
			registerSingleStringOp(mv, opcode);
			putTaintField(mv);
		}
	}

	private void handleConcat(MethodVisitor mv) {
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitIntInsn(SIPUSH, STR_CONCAT);
		mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "registerBinaryStringOp", "(Ljava/lang/String;Ljava/lang/String;I)" + Configuration.TAINT_TAG_DESC, false);
		putTaintField(mv);
	}

	/**
	 * Inserts code just before a constructor returns to initialize the taint to
	 * a fresh taint representing this value
	 */
	private void handleConstructor(MethodVisitor mv) {
		if (args.length == 1 && args[0].getInternalName().equals("java/lang/String")) {
			// Create a string that is a copy of the previous string. So copy
			// all constraints.
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, 1);
			getTaintField(mv);
			registerSingleStringOp(mv, STR_CPY);
			putTaintField(mv);
		}
	}

	private void registerSingleStringOp(MethodVisitor mv, int op) {
		mv.visitIntInsn(SIPUSH, op);
		mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "registerStringOp", "(" + Configuration.TAINT_TAG_DESC + "I)" + Configuration.TAINT_TAG_DESC, false);
	}

	private void getTaintField(MethodVisitor mv) {
		mv.visitFieldInsn(GETFIELD, "java/lang/String", TaintUtils.TAINT_FIELD, Configuration.TAINT_TAG_DESC);
	}

	private void putTaintField(MethodVisitor mv) {
		mv.visitFieldInsn(PUTFIELD, "java/lang/String", TaintUtils.TAINT_FIELD, Configuration.TAINT_TAG_DESC);
	}
}
