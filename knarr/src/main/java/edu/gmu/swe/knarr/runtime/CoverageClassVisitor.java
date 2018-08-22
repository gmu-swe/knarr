package edu.gmu.swe.knarr.runtime;

import edu.columbia.cs.psl.phosphor.org.objectweb.asm.ClassVisitor;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Label;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.MethodVisitor;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Opcodes;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type;

import java.lang.reflect.Array;
import java.util.*;

public class CoverageClassVisitor extends ClassVisitor implements Opcodes {
    private BitSet used = new BitSet(Coverage.SIZE*32);
    private Random r = new Random();

    private static final String[] blacklist = new String[]{
            "org/apache/maven/surefire",
            "za/ac/sun/cs/green",
            "com/sun",
    };

    private boolean enabled;

    public CoverageClassVisitor(ClassVisitor classVisitor, boolean what) {
        super(ASM6, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);

        for (String s : blacklist) {
            if (name.startsWith(s)) {
                enabled = false;
                return;
            }
        }

        enabled = true;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        return (enabled ? new CoverageMethodVisitor(mv) : mv);
    }

    private int getNewLocationId() {
        int id;
        int tries = 0;
        do {
            id = r.nextInt(Coverage.SIZE*32); // 32 bits per int, at least
            tries++;
        } while (tries <= 10 && !used.get(id));
        used.set(id);
        return id;
    }

    private void compute(int idx) {
        // Set
        Coverage.instance.coverage[idx / 32] |= (1 << idx % 32);

//        // Unset
//        Coverage.coverage[i] &= ~m;
//
//        // Read
//        boolean ret = (Coverage.coverage[i] & m) != 0;
    }

    private class CoverageMethodVisitor extends MethodVisitor {

        private final Type coverage = Type.getType(Coverage.class);

        public CoverageMethodVisitor(MethodVisitor methodVisitor) {
            super(ASM6, methodVisitor);
        }

        private void instrumentLocation() {
            Integer id = getNewLocationId();
            mv.visitFieldInsn(GETSTATIC, coverage.getInternalName(), "instance", coverage.getDescriptor());
            mv.visitFieldInsn(GETFIELD, coverage.getInternalName(), "coverage", "[I");
            mv.visitLdcInsn(id);
            mv.visitIntInsn(BIPUSH, 32);
            mv.visitInsn(IDIV);
            mv.visitInsn(DUP2);
            mv.visitInsn(IALOAD);
            mv.visitInsn(ICONST_1);
            mv.visitLdcInsn(id);
            mv.visitIntInsn(BIPUSH, 32);
            mv.visitInsn(IREM);
            mv.visitInsn(ISHL);
            mv.visitInsn(IOR);
            mv.visitInsn(IASTORE);
        }


        @Override
        public void visitCode() {
            mv.visitCode();

            /**
             *  Add instrumentation at start of method.
             */
            instrumentLocation();
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            mv.visitJumpInsn(opcode, label);

            /**
             *  Add instrumentation after the jump.
             *  Instrumentation for the if-branch is handled by visitLabel().
             */
//            instrumentLocation();
        }

        @Override
        public void visitLabel(Label label) {
            mv.visitLabel(label);

            /**
             * Since there is a label, we most probably (surely?) jump to this location. Instrument.
             */
//            instrumentLocation();
        }

    }
}
