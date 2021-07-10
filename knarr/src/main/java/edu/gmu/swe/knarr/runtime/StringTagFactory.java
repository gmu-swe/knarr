package edu.gmu.swe.knarr.runtime;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import edu.columbia.cs.psl.phosphor.Configuration;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.ClassVisitor;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Label;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.MethodVisitor;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Opcodes;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type;
import edu.columbia.cs.psl.phosphor.struct.LazyArrayObjTags;
import edu.columbia.cs.psl.phosphor.struct.LazyCharArrayObjTags;

public class StringTagFactory extends ClassVisitor implements Opcodes {
	private static final Type lazyCharArrayType = Type.getType(LazyCharArrayObjTags.class);
	private static final Type stringUtilsType = Type.getType(StringUtils.class);
	private static final Type stringType = Type.getType(String.class);


	private boolean enabled = false;

	private static final String suffix = "$$redirected";
	public final static Set<String> redirectedMethods;

	static {
		HashSet<String> ms = new HashSet<>();
		String ss = System.getProperty("specialStrings");
		if (ss == null) {
		    // Don't add any String methods
		} else if ("".equals(ss)) {
			// Add all string methods
            ms.addAll(Arrays.asList(new String[] {
					"startsWith$$PHOSPHORTAGGED",
					"equals$$PHOSPHORTAGGED",
					"charAt$$PHOSPHORTAGGED",
					"toLowerCase",
					"toUpperCase",
					"length$$PHOSPHORTAGGED",
					"contains$$PHOSPHORTAGGED",
					"indexOf$$PHOSPHORTAGGED",
			}));
		} else {
		    // Add selected methods
			for (String s : ss.split(",")) {
				switch (s) {
					case "startsWith":
					case "endsWith":
					case "equals":
					case "charAt":
					case "length":
					case "isEmpty":
					case "contains":
					case "indexOf":
						ms.add(s + "$$PHOSPHORTAGGED");
						break;
					case "toLowerCase":
					case "toUpperCase":
						ms.add(s);
						break;
					default:
						throw new Error("Unknown String method:" + s);
				}
			}
		}

		redirectedMethods = Collections.unmodifiableSet(ms);
	}

	public StringTagFactory(ClassVisitor classVisitor, boolean skipFrames) {
		super(ASM6, classVisitor);
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);
		enabled = name.equals(stringType.getInternalName()) && !redirectedMethods.isEmpty();
	}

	@Override
	public void visitEnd() {
		if (enabled)
			super.visitField(ACC_PRIVATE, "valuePHOSPHOR_TAG_2", lazyCharArrayType.getDescriptor(), null, null);

		super.visitEnd();
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		if (enabled) {
			System.out.println("instr... " + name + " enter");
			MethodVisitor ret;
			if (name.equals("<init>"))
				ret = new ConstructorVisitor(
						super.visitMethod(access, name, descriptor, signature, exceptions),
						Type.getArgumentTypes(descriptor));

			else if (((access & Opcodes.ACC_STATIC) == 0) && redirectedMethods.contains(name) ) {
				System.out.println("redirecting..." + name + " " + descriptor + " " + signature);
				ret = redirectWithDisabledValue(access, name, descriptor, signature, exceptions);
			}
			else
				ret = super.visitMethod(access, name, descriptor, signature, exceptions);

			return new RedirectMethodVisitor(ret);
		}

		return super.visitMethod(access, name, descriptor, signature, exceptions);
	}

	private MethodVisitor redirectWithDisabledValue(int access, String name, String descriptor, String signature, String[] exceptions) {
		MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
		mv.visitCode();

		Label l0 = new Label();
		Label l1 = new Label();
		Label l2 = new Label();
		// try-finally to reset taints
		mv.visitTryCatchBlock(l0, l1, l2, null);

		// Hide taints
		mv.visitVarInsn(ALOAD, 0);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD, stringType.getInternalName(), "valuePHOSPHOR_TAG", lazyCharArrayType.getDescriptor());
		mv.visitFieldInsn(PUTFIELD, stringType.getInternalName(), "valuePHOSPHOR_TAG_2", lazyCharArrayType.getDescriptor());
		mv.visitVarInsn(ALOAD, 0);
		mv.visitInsn(ACONST_NULL);
		mv.visitFieldInsn(PUTFIELD, stringType.getInternalName(), "valuePHOSPHOR_TAG", lazyCharArrayType.getDescriptor());
		mv.visitLabel(l0);

		// Call original method
		Type args[] = Type.getArgumentTypes(descriptor);
		mv.visitIntInsn(ALOAD, 0);
		for (int i = 0 ; i < args.length ; i++)
			mv.visitIntInsn(args[i].getOpcode(ILOAD), i+1);
		mv.visitMethodInsn(INVOKESPECIAL, stringType.getInternalName(), name + suffix, descriptor, false);

		mv.visitLabel(l1);

		// Reset taints when no exception
		mv.visitVarInsn(ALOAD, 0);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD, stringType.getInternalName(), "valuePHOSPHOR_TAG_2", lazyCharArrayType.getDescriptor());
		mv.visitFieldInsn(PUTFIELD, stringType.getInternalName(), "valuePHOSPHOR_TAG", lazyCharArrayType.getDescriptor());
		mv.visitVarInsn(ALOAD, 0);
		mv.visitInsn(ACONST_NULL);
		mv.visitFieldInsn(PUTFIELD, stringType.getInternalName(), "valuePHOSPHOR_TAG_2", lazyCharArrayType.getDescriptor());

		// Call interceptor from StringUtils
		Type ret = Type.getReturnType(descriptor);
		mv.visitInsn(ret.getSize() == 1 ? DUP : DUP2);
		mv.visitIntInsn(ALOAD, 0);
		for (int i = 0 ; i < args.length ; i++)
			mv.visitIntInsn(args[i].getOpcode(ILOAD), i+1);
		Type utilArgs[] = new Type[args.length + 2];
		utilArgs[0] = ret;
		utilArgs[1] = stringType;
		System.arraycopy(args, 0, utilArgs, 2, args.length);
		mv.visitMethodInsn(INVOKESTATIC, stringUtilsType.getInternalName(), name, Type.getMethodDescriptor(Type.VOID_TYPE, utilArgs), false);

		// Return
		mv.visitInsn(Type.getReturnType(descriptor).getOpcode(IRETURN));

		// Reset taints when exception
		mv.visitLabel(l2);
		mv.visitFrame(Opcodes.F_NEW, 0, null, 1, new Object[] {"java/lang/Throwable"});

		mv.visitVarInsn(ALOAD, 0);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD, stringType.getInternalName(), "valuePHOSPHOR_TAG_2", lazyCharArrayType.getDescriptor());
		mv.visitFieldInsn(PUTFIELD, stringType.getInternalName(), "valuePHOSPHOR_TAG", lazyCharArrayType.getDescriptor());
		mv.visitVarInsn(ALOAD, 0);
		mv.visitInsn(ACONST_NULL);
		mv.visitFieldInsn(PUTFIELD, stringType.getInternalName(), "valuePHOSPHOR_TAG_2", lazyCharArrayType.getDescriptor());

		// TODO Call interceptor when exception?

		mv.visitInsn(ATHROW);
		mv.visitMaxs(0, 0);
		mv.visitEnd();

		return super.visitMethod(access, name + suffix, descriptor, signature, exceptions);
	}

//	/**
//	 * Inserts code just before a constructor returns to initialize the taint to
//	 * a fresh taint representing this value
//	 */
//	private void handleConstructor(MethodVisitor mv, Type args[]) {
//		if (args.length == 1 && args[0].getInternalName().equals("java/lang/String")) {
//			// Create a string that is a copy of the previous string. So copy
//			// all constraints.
//			mv.visitVarInsn(ALOAD, 0);
//			mv.visitVarInsn(ALOAD, 1);
//			getTaintField(mv);
//			registerSingleStringOp(mv, STR_CPY);
//			putTaintField(mv);
//		} else if (args.length == 7 && args[1].getSort() == Type.ARRAY && args[3] == Type.INT_TYPE && args[5] == Type.INT_TYPE) {
//			mv.visitVarInsn(ALOAD, 0);
//			mv.visitVarInsn(ALOAD, 0);
//			mv.visitVarInsn(ALOAD, 1);
//			mv.visitVarInsn(ALOAD, 2);
//			mv.visitVarInsn(ALOAD, 3);
//			mv.visitVarInsn(ILOAD, 4);
//			mv.visitVarInsn(ALOAD, 5);
//			mv.visitVarInsn(ILOAD, 6);
//			mv.visitMethodInsn(INVOKESTATIC, PathUtils.INTERNAL_NAME, "registerNewString", "(" +
//					Type.getType(String.class) +
//					Type.getType(LazyArrayObjTags.class) + Type.getType(Object.class).getDescriptor() +
//					Configuration.TAINT_TAG_DESC + "I" +
//					Configuration.TAINT_TAG_DESC + "I)" +
//					Configuration.TAINT_TAG_DESC, false);
//			putTaintField(mv);
//		}
//	}

	private static class ConstructorVisitor extends MethodVisitor {
		Type[] args;

		public ConstructorVisitor(MethodVisitor methodVisitor, Type[] args) {
			super(ASM6, methodVisitor);
			this.args = args;
		}

		@Override
		public void visitInsn(int opcode) {
			switch (opcode) {
				case RETURN:
				case IRETURN:
				case FRETURN:
				case ARETURN:
				case LRETURN:
				case DRETURN:
//					if (args.length == 1 && args[0].equals(stringType)) {
//						// Create a string that is a copy of the previous string. So copy
//						// all constraints.
//						mv.visitVarInsn(ALOAD, 0);
//						mv.visitVarInsn(ALOAD, 1);
//						getTaintField(mv);
//						registerSingleStringOp(mv, STR_CPY);
//						putTaintField(mv);
					if (args.length == 7 && args[1].getSort() == Type.ARRAY && args[3] == Type.INT_TYPE && args[5] == Type.INT_TYPE) {
						mv.visitVarInsn(ALOAD, 0);
						mv.visitVarInsn(ALOAD, 1);
						mv.visitVarInsn(ALOAD, 2);
						mv.visitVarInsn(ALOAD, 3);
						mv.visitVarInsn(ILOAD, 4);
						mv.visitVarInsn(ALOAD, 5);
						mv.visitVarInsn(ILOAD, 6);
						mv.visitMethodInsn(INVOKESTATIC, stringUtilsType.getInternalName(), "registerNewString", "(" +
								stringType +
								Type.getType(LazyArrayObjTags.class) + Type.getType(Object.class).getDescriptor() +
								Configuration.TAINT_TAG_DESC + "I" +
								Configuration.TAINT_TAG_DESC + "I)V",
								false);
					}
					break;
				default:
					break;
			}
			super.visitInsn(opcode);
		}

	}

	private class RedirectMethodVisitor extends MethodVisitor {

		public RedirectMethodVisitor(MethodVisitor methodVisitor) {
			super(ASM6, methodVisitor);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
			if (opcode != Opcodes.INVOKESTATIC && owner.equals(stringType.getInternalName()) && redirectedMethods.contains(name))
				name = name + suffix;

			super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
		}



	}

}
