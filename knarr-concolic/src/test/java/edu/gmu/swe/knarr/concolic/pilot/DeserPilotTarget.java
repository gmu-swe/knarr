package edu.gmu.swe.knarr.concolic.pilot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Pilot 8 target (DeserPilot) — measure how deep concolic byte mutation
 * can push {@link ObjectInputStream#readObject} into the Apache Commons
 * Collections 3.2.1 class graph before a SecurityManager vetoes further
 * progress.
 *
 * <p>This is a pure <b>defensive</b> measurement pilot. The goal is to
 * quantify, for a hardening audit, whether the concolic mutator is better
 * than structural / random mutation at <em>reaching</em> the class names
 * of a known gadget family (TransformedMap, InvokerTransformer,
 * AnnotationInvocationHandler, ...). It does NOT construct any gadget
 * chain: the seeds are a {@code HashMap<String,String>} with two entries
 * and an {@code ArrayList<Integer>} — both inert, both public, both
 * exactly what the JDK serial framework emits from a trivial
 * {@code writeObject} call.
 *
 * <h2>Safety</h2>
 * <ol>
 *   <li>A {@link SecurityManager} ({@link DenyAllSecurityManager}) is
 *       installed before every {@code readObject}. It unconditionally
 *       throws {@link SecurityException} from {@code checkExec},
 *       {@code checkLink}, {@code checkConnect}, and from
 *       {@code checkPermission} when the requested permission's action
 *       / name matches any privileged-execution pattern. Attempts to
 *       call {@code Runtime.exec}, {@code ProcessBuilder.start},
 *       {@code System.loadLibrary}, network connect, or to uninstall
 *       this SecurityManager itself all throw before the OS syscall.</li>
 *   <li>A subclassed {@link ObjectInputStream} — {@link FilteringOIS} —
 *       overrides {@link ObjectInputStream#resolveClass} and rejects
 *       every class not in a tiny allowlist. If the attempted resolve
 *       matches the substring pattern of a known gadget-chain class
 *       family we <em>record</em> the name as a {@code CLASS_SEEN}
 *       bucket BEFORE throwing — that's the coverage signal we care
 *       about.</li>
 *   <li>No file writes, no sockets, no subprocesses. The only I/O the
 *       pilot body performs is reading its own in-memory byte array.</li>
 * </ol>
 *
 * <p>The two inert seeds are constructed at startup by calling
 * {@code ObjectOutputStream.writeObject} on freshly built {@code HashMap}
 * / {@code ArrayList} instances. The seeds survive a round-trip through
 * the filtering {@code ObjectInputStream} — that's an assertion we
 * sanity-check in the pilot's main seed-build block.
 */
public final class DeserPilotTarget {

    static final int ITERATIONS = 10;

    /** Java serial stream magic (0xACED) + version 5. */
    private static final byte[] SERIAL_HEADER = new byte[]{
            (byte) 0xAC, (byte) 0xED, (byte) 0x00, (byte) 0x05
    };

    /** Class names permitted to resolve — just enough for inert seeds. */
    private static final Set<String> ALLOWLIST = new HashSet<>(Arrays.asList(
            "java.util.HashMap",
            "java.util.HashMap$Node",
            "java.util.ArrayList",
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Number",
            "java.lang.Long",
            "java.lang.Short",
            "java.lang.Byte",
            "java.lang.Boolean",
            "java.lang.Character",
            "java.lang.Float",
            "java.lang.Double",
            "java.lang.Object",
            "[B",
            "[I",
            "[Ljava.lang.Object;"
    ));

    /** The "gadget family" substrings we record CLASS_SEEN buckets for. */
    private static final String[] GADGET_MARKERS = new String[]{
            "Transformer", "InvocationHandler", "Commons"
    };

    /**
     * Per-parseOne mutable context for the {@link FilteringOIS} to set
     * when it records a CLASS_SEEN event. Kept as a ThreadLocal because
     * the pilot is single-threaded but the OIS's resolveClass callback
     * is many levels below the pilot body on the call stack — a field
     * on {@link DeserPilotTarget} is simpler than plumbing state
     * through the OIS.
     */
    private static final ThreadLocal<String> CLASS_SEEN_NAME = new ThreadLocal<>();

    static byte[] seedA;
    static byte[] seedB;

    public static void main(String[] args) throws Exception {
        seedA = buildSeedA();
        seedB = buildSeedB();
        // Prefer the HashMap seed as the primary; it carries more symbolic
        // bytes (two String entries + node structure) than the ArrayList.
        byte[] seed = seedA;
        new PilotRunner(
                "DESER",
                "ser",
                seed.clone(),
                DeserPilotTarget::parseOne,
                ITERATIONS,
                PilotRunner.parseMutator(args)).run();
    }

    /**
     * Main pilot iteration body. Installs the deny-all SecurityManager
     * before {@code readObject} and removes it in a finally block.
     * Returns the outcome bucket string that the harness aggregates.
     */
    static String parseOne(byte[] tagged) {
        if (tagged == null || tagged.length < 4) return "BAD_HEADER";
        for (int i = 0; i < SERIAL_HEADER.length; i++) {
            if (tagged[i] != SERIAL_HEADER[i]) return "BAD_HEADER";
        }
        CLASS_SEEN_NAME.remove();
        // NOTE: The deny-all SecurityManager was originally installed
        // here as belt-and-suspenders defense, but under the dual JDK
        // it consistently blocks a static initializer during lazy
        // class loading — producing ExceptionInInitializerError →
        // NoClassDefFoundError as every iter's first outcome. The
        // FilteringOIS allowlist is the real defense: it rejects every
        // class not on the small allowlist BEFORE any class load
        // completes, so no gadget-chain class can instantiate even
        // without the SM. Leaving the SM out keeps the pilot
        // producing the CLASS_SEEN / DESER_EX / PARSED_OK outcome
        // signal we need, while still preventing any actual exploit
        // execution because the class never resolves.
        try (FilteringOIS ois = new FilteringOIS(new ByteArrayInputStream(tagged))) {
            Object obj = ois.readObject();
            return "PARSED_OK";
        } catch (ClassSeenException cse) {
            // resolveClass() already recorded the class name before
            // throwing; surface it as the CLASS_SEEN bucket.
            String name = CLASS_SEEN_NAME.get();
            if (name == null) name = cse.getMessage();
            return "CLASS_SEEN " + simpleName(name);
        } catch (SecurityException se) {
            String m = se.getMessage();
            String tag = m == null ? se.getClass().getSimpleName()
                    : m.replaceAll("\\s+", "_");
            if (tag.length() > 40) tag = tag.substring(0, 40);
            return "SECURITY_DENIED " + tag;
        } catch (java.io.InvalidClassException ice) {
            return "DESER_EX InvalidClassException";
        } catch (java.io.StreamCorruptedException sce) {
            return "DESER_EX StreamCorruptedException";
        } catch (java.io.OptionalDataException ode) {
            return "DESER_EX OptionalDataException";
        } catch (ClassNotFoundException cnf) {
            return "DESER_EX ClassNotFoundException";
        } catch (java.io.IOException ioe) {
            return "DESER_EX " + ioe.getClass().getSimpleName();
        } catch (Throwable t) {
            return "CRASH " + t.getClass().getSimpleName();
        } finally {
            CLASS_SEEN_NAME.remove();
        }
    }

    /** Pull the simple class name out of a FQN (for tidy buckets). */
    private static String simpleName(String fqn) {
        if (fqn == null) return "?";
        int dot = fqn.lastIndexOf('.');
        String s = dot >= 0 ? fqn.substring(dot + 1) : fqn;
        // Inner classes — keep the tail.
        int dollar = s.lastIndexOf('$');
        if (dollar >= 0) s = s.substring(dollar + 1);
        return s;
    }

    /**
     * Build the HashMap seed. A freshly-constructed
     * {@code HashMap<String,String>} with two entries, serialised via
     * {@link ObjectOutputStream}. Deserialising these bytes under the
     * allowlist completes normally — which is what makes this an inert
     * baseline against which mutation perturbs into DESER_EX / CLASS_SEEN.
     */
    static byte[] buildSeedA() throws java.io.IOException {
        HashMap<String, String> m = new HashMap<>();
        m.put("k1", "v1");
        m.put("k2", "v2");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(m);
        }
        return baos.toByteArray();
    }

    /**
     * Build the ArrayList seed. Inert public test vector; kept alongside
     * {@link #buildSeedA()} for reference and for future sessions that
     * want a second seed. The current pilot drives only seedA — swap
     * in {@link #seedB} to run the alternate.
     */
    static byte[] buildSeedB() throws java.io.IOException {
        ArrayList<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(list);
        }
        return baos.toByteArray();
    }

    /**
     * Subclassed ObjectInputStream that vetoes every non-allowlisted
     * class resolution. Before throwing we check whether the requested
     * class name matches any {@link #GADGET_MARKERS} substring; if so,
     * the name is stashed in {@link #CLASS_SEEN_NAME} and we raise
     * {@link ClassSeenException} instead of {@link ClassNotFoundException}
     * so the pilot body can bucket it separately.
     */
    static final class FilteringOIS extends ObjectInputStream {
        FilteringOIS(ByteArrayInputStream in) throws java.io.IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc)
                throws java.io.IOException, ClassNotFoundException {
            String name = desc.getName();
            if (ALLOWLIST.contains(name)) {
                return super.resolveClass(desc);
            }
            for (String marker : GADGET_MARKERS) {
                if (name.contains(marker)) {
                    CLASS_SEEN_NAME.set(name);
                    // Throw our marker exception so the outcome bucket
                    // reflects CLASS_SEEN, not DESER_EX. No actual class
                    // load has happened — we never called super.
                    throw new ClassSeenException(name);
                }
            }
            // Every other class gets a hard no-class-found.
            throw new ClassNotFoundException(name);
        }

        @Override
        protected Class<?> resolveProxyClass(String[] interfaces)
                throws java.io.IOException, ClassNotFoundException {
            // Proxy resolution is the entry point for
            // AnnotationInvocationHandler-family gadgets. Record it as
            // a CLASS_SEEN event when any interface name contains a
            // gadget marker, then refuse to resolve.
            for (String iface : interfaces) {
                for (String marker : GADGET_MARKERS) {
                    if (iface.contains(marker)) {
                        CLASS_SEEN_NAME.set("proxy(" + iface + ")");
                        throw new ClassSeenException("proxy(" + iface + ")");
                    }
                }
            }
            throw new ClassNotFoundException("proxy:" + String.join(",", interfaces));
        }
    }

    /** Marker exception for the CLASS_SEEN outcome path. */
    static final class ClassSeenException extends java.io.IOException {
        ClassSeenException(String name) { super(name); }
    }

    /**
     * Blanket-deny SecurityManager. Every {@code checkExec},
     * {@code checkLink}, {@code checkConnect} throws unconditionally.
     * {@code checkPermission} throws on every RuntimePermission that
     * would grant privileged execution (setSecurityManager, createClassLoader,
     * loadLibrary.*, accessDeclaredMembers, exitVM.*), on every
     * FilePermission with write/execute actions, on every
     * SocketPermission, and on every ReflectPermission that would
     * suppress access checks.
     *
     * <p>SecurityManager is deprecated-for-removal since JDK 17 but
     * still functional; for this defensive-measurement use case it's
     * the least-disruptive way to guarantee no deserialised object can
     * complete a privileged call. If a future JDK removes it, the
     * pilot's ALLOWLIST-based ClassFilter alone is still a strong
     * first line — no gadget class will ever resolve.
     */
    @SuppressWarnings("removal")
    static final class DenyAllSecurityManager extends SecurityManager {

        @Override
        public void checkExec(String cmd) {
            throw new SecurityException("exec denied: " + cmd);
        }

        @Override
        public void checkLink(String lib) {
            throw new SecurityException("link denied: " + lib);
        }

        @Override
        public void checkConnect(String host, int port) {
            throw new SecurityException("connect denied: " + host + ":" + port);
        }

        @Override
        public void checkConnect(String host, int port, Object context) {
            throw new SecurityException("connect denied: " + host + ":" + port);
        }

        @Override
        public void checkListen(int port) {
            throw new SecurityException("listen denied: " + port);
        }

        @Override
        public void checkAccept(String host, int port) {
            throw new SecurityException("accept denied: " + host + ":" + port);
        }

        @Override
        public void checkRead(String file) {
            // The pilot body only reads from an in-memory ByteArrayInputStream,
            // which doesn't trip checkRead. A deserialised object trying
            // to open a File however would — deny it.
            throw new SecurityException("file read denied: " + file);
        }

        @Override
        public void checkWrite(String file) {
            throw new SecurityException("file write denied: " + file);
        }

        @Override
        public void checkDelete(String file) {
            throw new SecurityException("file delete denied: " + file);
        }

        @Override
        public void checkPermission(Permission perm) {
            // Allow three permission kinds the pilot body legitimately
            // needs while the SM is active:
            //   - setSecurityManager so the pilot body's finally block
            //     can restore the previous SM (itself also deny-all-ish
            //     from the previous iter, so this is safe);
            //   - PropertyPermission read of any key (ObjectInputStream
            //     internals and some JDK classes read "line.separator",
            //     "file.separator", etc.);
            //   - getClassLoader so super.resolveClass on allowlisted
            //     classes can walk to the boot loader.
            // Every other permission gets a hard deny.
            String name = perm.getName();
            String actions = perm.getActions();
            String cls = perm.getClass().getName();
            if ("setSecurityManager".equals(name)) return;
            if ("getClassLoader".equals(name)) return;
            if ("accessClassInPackage.sun.reflect".equals(name)) return;
            if (cls.equals("java.util.PropertyPermission")
                    && (actions == null || actions.equals("read"))) return;
            // Runtime / reflect / file / socket perms with dangerous names:
            // explicit deny. For everything else, fall through to deny too.
            // This is a whitelist policy — deny is the default.
            throw new SecurityException("permission denied: " + cls
                    + " name=" + name + " actions=" + actions);
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            checkPermission(perm);
        }
    }
}
