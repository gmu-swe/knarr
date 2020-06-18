package edu.gmu.swe.knarr.runtime;

import java.util.ArrayList;

import edu.columbia.cs.psl.phosphor.instrumenter.TaintTagFactory;
import org.junit.Assert;
import org.junit.Test;

import edu.columbia.cs.psl.phosphor.instrumenter.TaintAdapter;
import edu.columbia.cs.psl.phosphor.instrumenter.analyzer.NeverNullArgAnalyzerAdapter;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.AnnotationVisitor;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Handle;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Label;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.MethodVisitor;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Opcodes;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type;


public class JunitTestAdapter extends TaintAdapter {
	
	private String methodName;

	public JunitTestAdapter(int access, String className, String name, String desc, String signature,
							String[] exceptions, MethodVisitor mv, NeverNullArgAnalyzerAdapter analyzer, TaintTagFactory taintTagFactory) {
		super(access, className, name, desc, signature, exceptions, mv, analyzer, taintTagFactory);
		methodName = name;
	}
	
	public JunitTestAdapter(int access, String className, String name, String desc, String signature,
			String[] exceptions, MethodVisitor mv, NeverNullArgAnalyzerAdapter analyzer,
			String classSource, String classDebug) {
		super(access, className, name, desc, signature, exceptions, mv, analyzer, classSource, classDebug);
		methodName = name;
	}

	private boolean enabled = false;
	private int currentLine = -1;
	private int varIdx = 0;
	
	private final static Type symbolicatorType = Type.getType(Symbolicator.class);
	private final static Type arrayListType = Type.getType(ArrayList.class);
	private final static Type pathUtilsType = Type.getType(JunitAssert.class);
	private final static Type assertType = Type.getType(Assert.class);
	private final static Type stringType = Type.getType(String.class);

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		enabled = descriptor.equals(Type.getType(Test.class).getDescriptor());
		return super.visitAnnotation(descriptor, visible);
	}
	
	@Override
	public void visitLineNumber(int line, Label start) {
		super.visitLineNumber(line, start);
		this.currentLine = line;
		this.varIdx = 0;
	}

	@Override
	public void visitLdcInsn(Object cst) {
		if (!enabled) {
			super.visitLdcInsn(cst);
			return;
		}

		String name = null;
		Type t = null;
		
		if (cst instanceof Integer) {
			name = "int_" + currentLine + "_" + (varIdx++);
			t = Type.INT_TYPE;
		} else if (cst instanceof Float) {
			name = "float_" + currentLine + "_" + (varIdx++);
			t = Type.FLOAT_TYPE;
		} else if (cst instanceof Long) {
			name = "long_" + currentLine + "_" + (varIdx++);
			t = Type.LONG_TYPE;
		} else if (cst instanceof Double) {
			name = "double_" + currentLine + "_" + (varIdx++);
			t = Type.DOUBLE_TYPE;
		} else if (cst instanceof String) {
			name = "string_" + currentLine + "_" + (varIdx++);
			t = stringType;
		} else if (cst instanceof Type) {
			int sort = ((Type) cst).getSort();
			if (sort == Type.OBJECT) {
				// ...
			} else if (sort == Type.ARRAY) {
				// ...
			} else if (sort == Type.METHOD) {
				// ...
			} else {
				throw new UnsupportedOperationException();
			}
		} else if (cst instanceof Handle) {
			// ...
		} else {
			throw new UnsupportedOperationException();
		}

		if (name != null) {
			super.visitLdcInsn(name);
			super.visitLdcInsn(cst);
			super.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					symbolicatorType.getInternalName(),
					"symbolic",
					Type.getMethodDescriptor(t, stringType, t),
					false);
		} else {
			super.visitLdcInsn(cst);
		}

	}

	@Override
	public void visitInsn(int opcode) {
		if (!enabled) {
			super.visitInsn(opcode);
			return;
		}

		String name = null;
		Type t = null;

		switch (opcode) {
			case IRETURN:
			case LRETURN:
			case FRETURN:
			case DRETURN:
			case ARETURN:
			case RETURN:
				super.visitLdcInsn(className.replace('/', '.') + ":" + methodName);
				super.visitMethodInsn(
						INVOKESTATIC,
						symbolicatorType.getInternalName(),
						"dumpConstraints", 
						Type.getMethodDescriptor(arrayListType, stringType),
						false);
				break;
			case ICONST_M1:
			case ICONST_0:
			case ICONST_1:
			case ICONST_2:
			case ICONST_3:
			case ICONST_4:
			case ICONST_5:
				name = "int_" + currentLine + "_" + (varIdx++);
				t = Type.INT_TYPE;
				break;
			case LCONST_0:
			case LCONST_1:
				name = "long_" + currentLine + "_" + (varIdx++);
				t = Type.LONG_TYPE;
				break;
			case FCONST_0:
			case FCONST_1:
			case FCONST_2:
				name = "float_" + currentLine + "_" + (varIdx++);
				t = Type.FLOAT_TYPE;
				break;
			case DCONST_0:
			case DCONST_1:
				name = "double_" + currentLine + "_" + (varIdx++);
				t = Type.DOUBLE_TYPE;
				break;
			default:
				break;
		}

		if (name != null) {
			super.visitLdcInsn(name);
			super.visitInsn(opcode);
			super.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					symbolicatorType.getInternalName(),
					"symbolic",
					Type.getMethodDescriptor(t, stringType, t),
					false);
		} else {
			super.visitInsn(opcode);
		}
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
		if (!enabled) {
			super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
			return;
		}

		// Do this for all types, even non-test types as such assertions may be in utility code
		if (opcode == Opcodes.INVOKESTATIC
				&& owner.equals(assertType.getInternalName())
				&& name.startsWith("assert")) {
			owner = pathUtilsType.getInternalName();
		}

		super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
	}
	
}
