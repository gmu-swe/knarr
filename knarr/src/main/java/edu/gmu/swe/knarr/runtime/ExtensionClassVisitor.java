package edu.gmu.swe.knarr.runtime;

import edu.columbia.cs.psl.phosphor.org.objectweb.asm.ClassVisitor;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Opcodes;

public class ExtensionClassVisitor extends ClassVisitor implements Opcodes {

    public static boolean addCoverage = (System.getProperty("addCov") != null);

    public ExtensionClassVisitor(ClassVisitor classVisitor, boolean b) {
        super(ASM6, addCoverage ? new CoverageClassVisitor(new StringTagFactory(classVisitor, b), b) : new StringTagFactory(classVisitor, b));
    }

}
