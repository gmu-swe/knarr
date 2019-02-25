package edu.gmu.swe.knarr.runtime;

import edu.columbia.cs.psl.phosphor.instrumenter.TaintAdapter;
import edu.columbia.cs.psl.phosphor.instrumenter.analyzer.NeverNullArgAnalyzerAdapter;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.MethodVisitor;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class RedirectMethodsTaintAdapter extends TaintAdapter {
    private static Type CHARACTER_TYPE = Type.getType(Character.class);
    private static Type STRING_TYPE = Type.getType(String.class);
    private static Type MODEL_UTILS_TYPE = Type.getType(ModelUtils.class);

    private static String NEW_STRING_EQUALS_DESC = Type.getMethodDescriptor(Type.BOOLEAN_TYPE, STRING_TYPE, Type.getType(Object.class));

    private static Set<String> REDIRECTED_METHODS_FROM_CHARACTER = new HashSet<>(Arrays.asList(new String[]{
            "digit",
//            "isWhitespace",
    }));

    private static Set<String> REDIRECTED_METHODS_FROM_STRING = new HashSet<>(Arrays.asList(new String[]{
            "equals",
    }));

    public RedirectMethodsTaintAdapter(int access, String className, String name, String desc, String signature, String[] exceptions, MethodVisitor mv, NeverNullArgAnalyzerAdapter analyzer) {
        super(access, className, name, desc, signature, exceptions, mv, analyzer);
    }

    public RedirectMethodsTaintAdapter(int access, String className, String name, String desc, String signature, String[] exceptions, MethodVisitor mv, NeverNullArgAnalyzerAdapter analyzer, String classSource, String classDebug) {
        super(access, className, name, desc, signature, exceptions, mv, analyzer, classSource, classDebug);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (opcode == INVOKESTATIC && owner.equals(CHARACTER_TYPE.getInternalName())) {
            if (REDIRECTED_METHODS_FROM_CHARACTER.contains(name)) {
                owner = MODEL_UTILS_TYPE.getInternalName();
            }
        }

        if (owner.equals(STRING_TYPE.getInternalName())) {
            if (REDIRECTED_METHODS_FROM_STRING.contains(name)) {
                opcode = INVOKESTATIC;
                owner = MODEL_UTILS_TYPE.getInternalName();
                descriptor = NEW_STRING_EQUALS_DESC;
            }
        }

        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }
}

