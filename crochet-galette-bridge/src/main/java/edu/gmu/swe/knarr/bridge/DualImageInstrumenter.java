package edu.gmu.swe.knarr.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import edu.neu.ccs.prl.galette.internal.transform.GaletteTransformer;
import net.jonbell.crochet.transform.CrochetTransformer;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ModuleHashesAttribute;
import org.objectweb.asm.commons.ModuleResolutionAttribute;
import org.objectweb.asm.commons.ModuleTargetAttribute;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.ModuleExportNode;

/**
 * Builds a dual-instrumented JDK image where every class has been
 * processed by Galette first, then by CROCHET.
 *
 * <p>Usage:
 *
 * <pre>
 * java -cp ... edu.gmu.swe.knarr.bridge.DualImageInstrumenter \
 *     /tmp/jdk-galette /tmp/jdk-dual-ga-first \
 *     /path/to/crochet-agent-1.0.0-SNAPSHOT.jar
 * </pre>
 *
 * <p>Strategy (option C from {@code designs/dual-instrumentation.md}):
 * <ol>
 *   <li>Extract {@code <galette-jdk>/lib/modules} (jimage) into an
 *       exploded directory of one folder per module.</li>
 *   <li>Walk every {@code .class} file in those folders and apply
 *       {@link CrochetTransformer#transform(byte[], boolean)} in place.
 *       Galette runtime classes (already packed into {@code java.base})
 *       are skipped — CROCHET's own {@code shouldSkip} list handles the
 *       JDK package prefixes.</li>
 *   <li>Copy the CROCHET runtime classes from the supplied
 *       {@code crochet-agent} jar (filtered to the same set the official
 *       CROCHET pack-step produces) into the exploded {@code java.base}
 *       directory.</li>
 *   <li>Patch {@code java.base/module-info.class} to declare the new
 *       packages and add unqualified exports for them.</li>
 *   <li>Invoke {@code jlink} once with
 *       {@code --module-path=<exploded>} to produce the final image at
 *       the requested output directory. The original native libs and
 *       launcher come along for free because jlink rebuilds the runtime
 *       image from scratch using the exploded modules and the host JDK's
 *       launcher template.</li>
 * </ol>
 */
public final class DualImageInstrumenter {

    /**
     * Mirror of {@code CrochetInstrumentation.shouldPack}. Kept private
     * here so we don't take a hard dependency on
     * {@code crochet-instrument} (which targets a different JDK).
     */
    private static boolean shouldPackCrochet(String resourceName) {
        return resourceName.startsWith(CrochetTransformer.RUNTIME_PACKAGE_PREFIX)
                || resourceName.startsWith(CrochetTransformer.TRANSFORM_PACKAGE_PREFIX)
                || resourceName.startsWith("net/jonbell/crochet/annotation/")
                || resourceName.startsWith("net/jonbell/crochet/patch/")
                || resourceName.startsWith("net/jonbell/crochet/agent/shaded/");
    }

    private DualImageInstrumenter() {
        throw new AssertionError();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println(
                    "usage: DualImageInstrumenter <galette-jdk> <output-jdk> <crochet-agent.jar>");
            System.exit(2);
        }
        Path galetteJdk = Paths.get(args[0]).toAbsolutePath();
        Path outJdk = Paths.get(args[1]).toAbsolutePath();
        Path crochetAgentJar = Paths.get(args[2]).toAbsolutePath();
        if (!Files.isDirectory(galetteJdk)) {
            throw new IllegalArgumentException("Not a JDK directory: " + galetteJdk);
        }
        if (Files.exists(outJdk)) {
            throw new IllegalArgumentException("Output already exists: " + outJdk);
        }
        if (!Files.isRegularFile(crochetAgentJar)) {
            throw new IllegalArgumentException("Not a file: " + crochetAgentJar);
        }
        Path jimage = galetteJdk.resolve("lib").resolve("modules");
        if (!Files.isRegularFile(jimage)) {
            throw new IllegalArgumentException("No lib/modules in: " + galetteJdk);
        }

        Path scratch = Files.createTempDirectory("dual-image-");
        Path exploded = scratch.resolve("modules");
        Files.createDirectories(exploded);

        long t0 = System.currentTimeMillis();
        System.out.println("[dual] step 1: extract jimage -> " + exploded);
        runJimageExtract(galetteJdk, jimage, exploded);

        System.out.println("[dual] step 2: run CrochetTransformer over exploded classes");
        long classCount = transformAllClasses(exploded);
        System.out.println("[dual] transformed " + classCount + " classes");

        System.out.println("[dual] step 3: pack CROCHET runtime classes into java.base");
        Set<String> addedPackages = packCrochetRuntime(crochetAgentJar, exploded.resolve("java.base"));
        System.out.println("[dual] added " + addedPackages.size() + " runtime packages");

        System.out.println("[dual] step 4: patch java.base module-info");
        patchJavaBaseModuleInfo(exploded.resolve("java.base").resolve("module-info.class"),
                addedPackages);

        System.out.println("[dual] step 5a: pre-delete SystemModules*.class so jlink regenerates");
        deleteJlinkRegeneratedClasses(exploded.resolve("java.base"));

        System.out.println("[dual] step 5b: first jlink (lets system-modules emit fresh classes)");
        Path tempJdk = scratch.resolve("first-jlink");
        runJlink(galetteJdk, exploded, tempJdk);

        System.out.println("[dual] step 6a: extract SystemModules from first jlink output, "
                + "Galette-transform, copy back into exploded");
        rebakeJlinkRegeneratedClasses(tempJdk, exploded.resolve("java.base"));

        System.out.println("[dual] step 6b: second jlink with system-modules disabled, "
                + "preserving the now Galette-instrumented SystemModules");
        runJlinkPreservingSystemModules(galetteJdk, exploded, outJdk);

        System.out.println("[dual] step 7: overlay launcher / native libs from "
                + galetteJdk);
        overlayLauncherAndNativeLibs(galetteJdk, outJdk);

        long elapsed = System.currentTimeMillis() - t0;
        System.out.println("[dual] done in " + elapsed + " ms");
    }

    private static void runJimageExtract(Path galetteJdk, Path jimage, Path exploded)
            throws IOException, InterruptedException {
        Path jimageBin = galetteJdk.resolve("bin").resolve("jimage");
        if (!Files.isRegularFile(jimageBin)) {
            // Fall back to host JDK's jimage tool — they're version-
            // compatible at the file format level for the same major.
            jimageBin = Paths.get(System.getProperty("java.home"))
                    .resolve("bin").resolve("jimage");
        }
        ProcessBuilder pb = new ProcessBuilder(
                jimageBin.toString(), "extract",
                "--dir=" + exploded,
                jimage.toString());
        pb.inheritIO();
        Process p = pb.start();
        if (p.waitFor() != 0) {
            throw new IOException("jimage extract failed");
        }
    }

    /**
     * Galette's own runtime / transform classes get packed into
     * java.base by Galette's jlink pipeline. They MUST NOT receive
     * CROCHET's $$crochet surface — Galette's runtime is on the JVM's
     * very-early bootstrap path (TagFrame, IndirectTagFrameStore are
     * touched before CROCHET's runtime can initialize), so adding the
     * surface introduces a clinit cycle: Galette runtime → CROCHET
     * runtime → java.lang.Throwable (also instrumented) → SIGSEGV
     * during Throwable.&lt;clinit&gt;.
     */
    private static boolean isGaletteRuntime(String relPath) {
        // Inside the exploded module the path is e.g.
        // java.base/edu/neu/ccs/prl/galette/internal/runtime/Tag.class.
        return relPath.contains("/edu/neu/ccs/prl/galette/")
                || relPath.contains(java.io.File.separator + "edu" + java.io.File.separator
                        + "neu" + java.io.File.separator + "ccs" + java.io.File.separator
                        + "prl" + java.io.File.separator + "galette" + java.io.File.separator);
    }

    private static long transformAllClasses(Path exploded) throws IOException {
        CrochetTransformer transformer = new CrochetTransformer();
        ConcurrentHashMap<String, Throwable> failures = new ConcurrentHashMap<>();
        long[] count = new long[1];
        Files.walkFileTree(exploded, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fname = file.getFileName().toString();
                if (!fname.endsWith(".class")) {
                    return FileVisitResult.CONTINUE;
                }
                if (fname.equals("module-info.class")) {
                    return FileVisitResult.CONTINUE;
                }
                String rel = exploded.relativize(file).toString();
                if (isGaletteRuntime(rel)) {
                    return FileVisitResult.CONTINUE;
                }
                try {
                    byte[] bytes = Files.readAllBytes(file);
                    byte[] out;
                    try {
                        out = transformer.transform(bytes, false);
                    } catch (Throwable t) {
                        // Mirror CrochetInstrumentation.apply: keep
                        // original bytes if transform throws so the
                        // image still builds.
                        failures.put(file.toString(), t);
                        out = null;
                    }
                    if (out != null) {
                        Files.write(file, out);
                    }
                    count[0]++;
                } catch (IOException e) {
                    throw new RuntimeException("rw " + file, e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        if (!failures.isEmpty()) {
            System.out.println("[dual] transform failures (kept original bytes): " + failures.size());
            int shown = 0;
            for (var e : failures.entrySet()) {
                if (shown++ >= 10) break;
                System.out.println("  " + e.getKey() + " : " + e.getValue());
            }
        }
        return count[0];
    }

    private static Set<String> packCrochetRuntime(Path agentJar, Path javaBase) throws IOException {
        Set<String> packages = new LinkedHashSet<>();
        // Galette-transform every CROCHET runtime class BEFORE writing
        // it into java.base. The Galette runtime agent (loaded via
        // -javaagent at the command line) instruments user code's
        // accesses to CROCHET runtime fields/methods (e.g. renames
        // GETSTATIC RuntimeReady.VERSION_GATE -> $$GALETTE_VERSION_GATE).
        // If the CROCHET classes themselves were not Galette-shadowed,
        // the access fails at runtime with NoSuchFieldError. Running
        // GaletteTransformer here makes the packed runtime carry the
        // shadow surface (renamed fields, shadow methods) the agent
        // expects from any other java.base class.
        GaletteTransformer galette = new GaletteTransformer();
        try (ZipFile zf = new ZipFile(agentJar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (!shouldPackCrochet(name)) continue;
                byte[] bytes;
                try (InputStream in = zf.getInputStream(e)) {
                    bytes = in.readAllBytes();
                }
                if (name.endsWith(".class")) {
                    // Galette-transform every packed CROCHET class
                    // INCLUDING the shaded ASM. Skipping shaded ASM is
                    // a foot-gun: callers in transform/ get rewritten
                    // to e.g. `new ClassReader(byte[], TagFrame)` and
                    // crash with NoSuchMethodError if the shaded ASM
                    // ClassReader didn't get the matching shadow.
                    try {
                        byte[] gal = galette.transform(bytes, false);
                        if (gal != null) bytes = gal;
                    } catch (Throwable t) {
                        System.err.println("[dual] galette pre-transform failed for "
                                + name + ": " + t);
                    }
                }
                Path dest = javaBase.resolve(name);
                Files.createDirectories(dest.getParent());
                Files.write(dest, bytes);
                if (name.endsWith(".class") && name.contains("/")) {
                    String pkg = name.substring(0, name.lastIndexOf('/'));
                    packages.add(pkg);
                }
            }
        }
        return packages;
    }

    private static void patchJavaBaseModuleInfo(Path moduleInfo, Set<String> addedPackages)
            throws IOException {
        byte[] in = Files.readAllBytes(moduleInfo);
        ClassNode cn = new ClassNode();
        ClassReader cr = new ClassReader(in);
        Attribute[] attrs = new Attribute[]{
                new ModuleTargetAttribute(),
                new ModuleResolutionAttribute(),
                new ModuleHashesAttribute()
        };
        cr.accept(cn, attrs, 0);
        System.out.println("[dual] module-info read: " + cn.module.packages.size()
                + " packages, " + cn.module.exports.size() + " exports");
        if (!cn.module.packages.isEmpty()) {
            System.out.println("[dual] sample pkg: " + cn.module.packages.get(0));
            System.out.println("[dual] sample pkg last: " + cn.module.packages.get(cn.module.packages.size()-1));
            System.out.println("[dual] sample export: " + cn.module.exports.get(0).packaze);
            // Dump full pkg list
            java.nio.file.Files.write(
                    java.nio.file.Paths.get("/tmp/dual-pkgs.txt"),
                    cn.module.packages);
        }
        Set<String> existing = new HashSet<>(cn.module.packages);
        for (String pkg : addedPackages) {
            if (existing.add(pkg)) {
                cn.module.packages.add(pkg);
                cn.module.exports.add(new ModuleExportNode(pkg, 0, null));
            }
        }
        // Strip ModuleHashes — once we modify any classes in the linked
        // modules the recorded hashes are wrong, and jlink will refuse
        // with "Unable to compute the hash of module java.rmi".
        if (cn.attrs != null) {
            cn.attrs.removeIf(a -> "ModuleHashes".equals(a.type));
        }
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        Files.write(moduleInfo, cw.toByteArray());
    }

    private static void runJlink(Path galetteJdk, Path exploded, Path outJdk)
            throws IOException, InterruptedException {
        // Use the host JDK's jlink — the Galette image's jlink would
        // accept these inputs equally well, but the host JDK is the one
        // we know is healthy and matches the class file major.
        Path jlinkBin = Paths.get(System.getProperty("java.home"))
                .resolve("bin").resolve("jlink");
        if (!Files.isRegularFile(jlinkBin)) {
            jlinkBin = galetteJdk.resolve("bin").resolve("jlink");
        }
        // Build module path: each immediate child of exploded is a
        // separate exploded module.
        StringBuilder mp = new StringBuilder();
        try (var s = Files.list(exploded)) {
            List<Path> dirs = s.filter(Files::isDirectory).sorted().toList();
            for (Path d : dirs) {
                if (mp.length() > 0) mp.append(java.io.File.pathSeparator);
                mp.append(d.toAbsolutePath());
            }
        }
        ProcessBuilder pb = new ProcessBuilder(
                jlinkBin.toString(),
                "--module-path", mp.toString(),
                "--add-modules", "ALL-MODULE-PATH",
                // Galette already injected pre-generated jli species
                // classes during its own jlink. The default
                // generate-jli-classes plugin will try to add them
                // again and fail with "Resource ... already present".
                "--disable-plugin", "generate-jli-classes",
                "--output", outJdk.toString());
        pb.inheritIO();
        System.out.println("[dual] " + String.join(" ", pb.command()));
        Process p = pb.start();
        if (p.waitFor() != 0) {
            throw new IOException("jlink failed");
        }
    }

    /**
     * jlink's system-modules plugin regenerates these classes based on
     * the input module-info. We need to remove the Galette-baked
     * versions so the second jlink emits fresh ones containing CROCHET
     * packages too.
     */
    private static void deleteJlinkRegeneratedClasses(Path javaBase) throws IOException {
        Path mod = javaBase.resolve("jdk").resolve("internal").resolve("module");
        if (!Files.isDirectory(mod)) return;
        try (var s = Files.list(mod)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                String n = p.getFileName().toString();
                // Concrete generated classes only — keep the
                // SystemModules interface (no $).
                boolean concrete = (n.equals("SystemModulesMap.class")
                        || n.startsWith("SystemModules$"));
                if (concrete) {
                    Files.delete(p);
                    System.out.println("[dual]  deleted " + p.getFileName());
                }
            }
        }
    }

    /**
     * Extract the SystemModules*.class files freshly emitted by the
     * first jlink, run Galette's transformer over each (so the shadow
     * methods Galette runtime calls — e.g.
     * {@code defaultSystemModules(TagFrame)} — exist), and write the
     * results back into the exploded java.base so the second jlink
     * picks them up.
     */
    private static void rebakeJlinkRegeneratedClasses(Path freshJdk, Path javaBase)
            throws IOException, InterruptedException {
        Path scratch = Files.createTempDirectory("rebake-");
        Path freshExploded = scratch.resolve("modules");
        Files.createDirectories(freshExploded);
        runJimageExtract(freshJdk, freshJdk.resolve("lib").resolve("modules"), freshExploded);
        Path mod = freshExploded.resolve("java.base").resolve("jdk")
                .resolve("internal").resolve("module");
        GaletteTransformer galette = new GaletteTransformer();
        try (var s = Files.list(mod)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                String n = p.getFileName().toString();
                if (!n.startsWith("SystemModules") || !n.endsWith(".class")) continue;
                byte[] orig = Files.readAllBytes(p);
                byte[] gal = galette.transform(orig, false);
                Path dest = javaBase.resolve("jdk").resolve("internal")
                        .resolve("module").resolve(n);
                Files.createDirectories(dest.getParent());
                Files.write(dest, gal == null ? orig : gal);
                System.out.println("[dual]  re-baked " + n
                        + (gal == null ? " (galette no-op)" : ""));
            }
        }
    }

    private static void runJlinkPreservingSystemModules(Path galetteJdk, Path exploded, Path outJdk)
            throws IOException, InterruptedException {
        Path jlinkBin = Paths.get(System.getProperty("java.home"))
                .resolve("bin").resolve("jlink");
        if (!Files.isRegularFile(jlinkBin)) {
            jlinkBin = galetteJdk.resolve("bin").resolve("jlink");
        }
        StringBuilder mp = new StringBuilder();
        try (var s = Files.list(exploded)) {
            List<Path> dirs = s.filter(Files::isDirectory).sorted().toList();
            for (Path d : dirs) {
                if (mp.length() > 0) mp.append(java.io.File.pathSeparator);
                mp.append(d.toAbsolutePath());
            }
        }
        ProcessBuilder pb = new ProcessBuilder(
                jlinkBin.toString(),
                "--module-path", mp.toString(),
                "--add-modules", "ALL-MODULE-PATH",
                "--disable-plugin", "generate-jli-classes",
                "--disable-plugin", "system-modules",
                "--output", outJdk.toString());
        pb.inheritIO();
        Process p = pb.start();
        if (p.waitFor() != 0) {
            throw new IOException("second jlink failed");
        }
    }

    private static void overlayLauncherAndNativeLibs(Path src, Path dst) throws IOException {
        // jlink reconstructs lib/modules from the exploded sources but
        // does not bring along the native launcher (bin/), shared
        // libraries (lib/*.so), conf/, legal/, etc. Those are on disk
        // in the source jlinked image — overlay them onto dst, skipping
        // anything dst already has (notably lib/modules itself, which
        // jlink just produced and IS our new dual-instrumented image).
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Path rel = src.relativize(dir);
                Path target = dst.resolve(rel.toString());
                if (!Files.exists(target)) {
                    Files.createDirectories(target);
                }
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Path rel = src.relativize(file);
                Path target = dst.resolve(rel.toString());
                if (rel.toString().equals("lib/modules")
                        || rel.toString().equals("lib" + java.io.File.separator + "modules")) {
                    return FileVisitResult.CONTINUE;
                }
                if (!Files.exists(target)) {
                    Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Used by the unit test. */
    static URL findResource(String name, ClassLoader cl) {
        return cl.getResource(name);
    }
}
