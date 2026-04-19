package edu.gmu.swe.knarr.bridge;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Inject a no-op {@code $$crochetAccess()V} stub into classes that CROCHET
 * intentionally skips but whose instance field accesses still get wrapped
 * by {@code FieldAccessWrapper} at other callers' emit sites. Without this
 * stub the JVM throws {@code NoSuchMethodError: String.$$crochetAccess()V}
 * the first time an instrumented caller touches a {@link String} field
 * (in practice: {@code TreeMap.put} → {@code String$CaseInsensitiveComparator.compare}
 * during {@code commons-io Charsets.<clinit>}).
 *
 * <p>CROCHET's {@code CrochetTransformer.shouldSkip} skips {@link String}
 * and the boxed-primitive types (Number / Integer / Long / Float / Double
 * / Boolean / Short / Character) for rollback-semantics reasons documented
 * in that class — their hash/value caches are deterministic and safe to
 * miss on rollback. But skipping means no {@code $$crochetAccess} method
 * gets injected, and the FieldAccessWrapper at caller sites is unaware
 * of the skip list and emits an unguarded {@code INVOKEVIRTUAL} targeting
 * those owners. Adding a stub resolves the dispatch without changing
 * rollback semantics — the stub body is a single {@code RETURN}.
 *
 * <p>Applied as a post-pass in {@link CompositeTransformer} only for the
 * specific skipped-but-instanceable JDK classes; ordinary skipped cases
 * (interfaces, enums, annotations, modules) are unaffected because
 * {@code FieldAccessWrapper}'s suspicious-owner path already emits the
 * guarded {@code INSTANCEOF CRIJInstrumented} form against those.
 */
final class SkippedSurfaceStub {

    /**
     * CROCHET-skipped classes that are ordinary instanceable types. These
     * are the only ones for which FieldAccessWrapper will emit an
     * unguarded {@code INVOKEVIRTUAL $$crochetAccess} at a caller site,
     * so only these need the stub. List matches
     * {@code CrochetTransformer.shouldSkip} exactly.
     */
    private static final java.util.Set<String> TARGETS = java.util.Set.of(
            "java/lang/String",
            "java/lang/Number",
            "java/lang/Integer",
            "java/lang/Long",
            "java/lang/Float",
            "java/lang/Double",
            "java/lang/Boolean",
            "java/lang/Short",
            "java/lang/Character");

    static boolean isTarget(String internalName) {
        return TARGETS.contains(internalName);
    }

    /** Returns the input bytes with {@code $$crochetAccess()V} added iff absent. */
    static byte[] addStubIfMissing(byte[] classFileBuffer) {
        ClassReader reader = new ClassReader(classFileBuffer);
        String name = reader.getClassName();
        if (!isTarget(name)) {
            return classFileBuffer;
        }
        if (hasMethod(reader, "$$crochetAccess", "()V")) {
            return classFileBuffer;
        }
        ClassWriter writer = new ClassWriter(reader, 0);
        ClassVisitor injector = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visitEnd() {
                // Not final: Float / Integer / etc. extend Number, and a
                // stub added to Number would clash with a stub added to
                // its subclasses if either side were final. Leaving it
                // non-final lets the subclass's identical stub legally
                // override (the JVM's IncompatibleClassChangeError check
                // triggered when we tried final on both).
                MethodVisitor mv = super.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "$$crochetAccess", "()V", null, null);
                mv.visitCode();
                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(0, 1);
                mv.visitEnd();
                super.visitEnd();
            }
        };
        reader.accept(injector, 0);
        return writer.toByteArray();
    }

    private static boolean hasMethod(ClassReader reader, String methodName, String descriptor) {
        final boolean[] found = new boolean[1];
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {
                if (methodName.equals(name) && descriptor.equals(desc)) {
                    found[0] = true;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }
}
