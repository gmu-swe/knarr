package edu.gmu.swe.knarr.runtime;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import edu.neu.ccs.prl.galette.internal.runtime.Tag;
import edu.neu.ccs.prl.galette.internal.runtime.Tainter;
import za.ac.sun.cs.green.expr.BVConstant;
import za.ac.sun.cs.green.expr.BVVariable;
import za.ac.sun.cs.green.expr.BinaryOperation;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntVariable;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.RealVariable;
import za.ac.sun.cs.green.expr.StringVariable;
import za.ac.sun.cs.green.expr.UnaryOperation;

/**
 * Public API for tagging values as symbolic and for retrieving / dumping the
 * accumulated path constraints. Galette-port of the original Phosphor-tied
 * class. Method shapes match the original public surface; the {@code
 * $$PHOSPHORTAGGED} overloads are dropped (Galette does not mangle method
 * names).
 */
public class Symbolicator {
    static Socket serverConnection;
    static String SERVER_HOST = System.getProperty("SATServer", "127.0.0.1");
    static int SERVER_PORT = Integer.valueOf(System.getProperty("SATPort", "9090"));
    static InputSolution mySoln = null;
    public static final boolean DEBUG = Boolean.valueOf(System.getProperty("DEBUG", "false"));
    public static final String INTERNAL_NAME = "edu/gmu/swe/knarr/runtime/Symbolicator";

    private static final BVConstant FF_32 = new BVConstant(0xFF, 32);
    private static final BVConstant FFFF_32 = new BVConstant(0xFFFF, 32);
    private static final Expression FFFFFF00_32 = new UnaryOperation(Operator.BIT_NOT, new BVConstant(0x000000FF, 32));
    private static final Expression FFFF0000_32 = new UnaryOperation(Operator.BIT_NOT, new BVConstant(0x0000FFFF, 32));

    static AtomicInteger autoLblr = new AtomicInteger();
    private static String firstLabel = null;
    static HashMap<Expression, Object> symbolicLabels = new HashMap<>();
    static ConcurrentHashMap<String, AtomicInteger> lblCounters = new ConcurrentHashMap<>();

    public static Socket getSocket() {
        if (serverConnection != null)
            return serverConnection;
        try {
            serverConnection = new Socket(SERVER_HOST, SERVER_PORT);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return serverConnection;
    }

    private static void collectArrayLenConstraints() {
        // Disabled — the original implementation was already commented out.
    }

    public static ArrayList<SimpleEntry<String, Object>> dumpConstraints() {
        return dumpConstraints(null);
    }

    @SuppressWarnings("unchecked")
    public static synchronized ArrayList<SimpleEntry<String, Object>> dumpConstraints(String name) {
        collectArrayLenConstraints();
        if (PathUtils.getCurPC().constraints == null)
            return null;

        try (ObjectOutputStream oos = new ObjectOutputStream(getSocket().getOutputStream())) {
            ObjectInputStream ois = new ObjectInputStream(getSocket().getInputStream());
            oos.writeObject(PathUtils.getCurPC().constraints);
            // Solve constraints?
            oos.writeBoolean(true);
            // Dump constraints to file?
            oos.writeObject(name != null ? new File(name + ".dat") : null);
            // Coverage, if any
            Coverage.instance.thisCount = Coverage.count;
            oos.writeObject(Coverage.instance);

            ArrayList<SimpleEntry<String, Object>> solution =
                    (ArrayList<SimpleEntry<String, Object>>) ois.readObject();

            byte[] array = new byte[solution.size()];
            int i = 0;
            boolean found = false;
            for (Entry<String, Object> e : solution) {
                if (!e.getKey().startsWith("autoVar_"))
                    break;
                if (!found && e.getKey().equals(firstLabel))
                    found = true;
                else if (!found)
                    continue;
                Integer b = (Integer) e.getValue();
                if (b == null)
                    break;

                array[i++] = b.byteValue();
            }
            System.out.println(new String(array, StandardCharsets.UTF_8));

            reset();
            oos.close();
            return solution;
        } catch (IOException | ClassNotFoundException | StackOverflowError e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void reset() {
        PathUtils.getCurPC().constraints = null;
        PathUtils.getCurPC().size = 0;
        serverConnection = null;
        firstLabel = null;
        PathUtils.usedLabels.clear();
        Coverage.instance.reset();
        autoLblr.set(0);
        Coverage.count = 0;
        symbolicLabels.clear();
        lblCounters.clear();
    }

    public static void solve() {}

    public static String generateLabel() {
        String ret = "autoVar_" + autoLblr.getAndIncrement();
        if (firstLabel == null)
            firstLabel = ret;
        return ret;
    }

    // ---------- symbolic(label, T) ----------

    public static int symbolic(String label, int v) {
        PathUtils.checkLabelAndInitJPF(label);
        Expression var = new BVVariable(label, 32);
        symbolicLabels.put(var, label);
        if (mySoln != null && !mySoln.isUnconstrained && mySoln.varMapping.get(label) instanceof Integer) {
            v = (Integer) mySoln.varMapping.get(label);
        }
        return Tainter.setTag(v, Tag.of(var));
    }

    public static long symbolic(String label, long v) {
        PathUtils.checkLabelAndInitJPF(label);
        Expression var = new BVVariable(label, 64);
        symbolicLabels.put(var, label);
        if (mySoln != null && !mySoln.isUnconstrained && mySoln.varMapping.get(label) instanceof Long) {
            v = (Long) mySoln.varMapping.get(label);
        }
        return Tainter.setTag(v, Tag.of(var));
    }

    public static byte symbolic(String label, byte v) {
        PathUtils.checkLabelAndInitJPF(label);
        BVVariable var = new BVVariable(label, 32);
        // Original Knarr emitted (var & 0xFFFFFF00) == 0 OR == 0xFFFFFF00 to
        // restrict the BV to a sign-extended byte.
        Expression pos = new BinaryOperation(Operator.EQ,
                new BinaryOperation(Operator.BIT_AND, var, FFFFFF00_32), Operation.ZERO);
        Expression neg = new BinaryOperation(Operator.EQ,
                new BinaryOperation(Operator.BIT_AND, var, FFFFFF00_32), FFFFFF00_32);
        PathUtils.getCurPC()._addDet(Operator.OR, pos, neg);
        symbolicLabels.put(var, label);
        if (mySoln != null && !mySoln.isUnconstrained && mySoln.varMapping.get(label) instanceof Integer) {
            v = ((Integer) mySoln.varMapping.get(label)).byteValue();
        }
        return Tainter.setTag(v, Tag.of(var));
    }

    public static short symbolic(String label, short v) {
        PathUtils.checkLabelAndInitJPF(label);
        BVVariable var = new BVVariable(label, 32);
        // Restrict the high half to zero, matching the original behaviour.
        PathUtils.getCurPC()._addDet(Operator.EQ,
                new BinaryOperation(Operator.BIT_AND, var, FFFF0000_32), Operation.ZERO);
        symbolicLabels.put(var, label);
        if (mySoln != null && !mySoln.isUnconstrained && mySoln.varMapping.get(label) instanceof Short) {
            v = (Short) mySoln.varMapping.get(label);
        }
        return Tainter.setTag(v, Tag.of(var));
    }

    public static char symbolic(String label, char v) {
        PathUtils.checkLabelAndInitJPF(label);
        BVVariable var = new BVVariable(label, 32);
        PathUtils.getCurPC()._addDet(Operator.EQ,
                new BinaryOperation(Operator.BIT_AND, var, FFFF0000_32), Operation.ZERO);
        symbolicLabels.put(var, label);
        if (mySoln != null && !mySoln.isUnconstrained && mySoln.varMapping.get(label) instanceof Integer) {
            v = (char) ((Integer) mySoln.varMapping.get(label)).intValue();
        }
        return Tainter.setTag(v, Tag.of(var));
    }

    public static boolean symbolic(String label, boolean v) {
        PathUtils.checkLabelAndInitJPF(label);
        Expression var = new IntVariable(label, 0, 1);
        symbolicLabels.put(var, label);
        if (mySoln != null && !mySoln.isUnconstrained && mySoln.varMapping.get(label) instanceof Integer) {
            v = ((Integer) mySoln.varMapping.get(label)).intValue() == 1;
        }
        return Tainter.setTag(v, Tag.of(var));
    }

    public static float symbolic(String label, float v) {
        PathUtils.checkLabelAndInitJPF(label);
        Expression var = new RealVariable(label,
                Double.valueOf(Float.MIN_VALUE),
                Double.valueOf(Float.MAX_VALUE));
        symbolicLabels.put(var, label);
        if (mySoln != null && !mySoln.isUnconstrained && mySoln.varMapping.get(label) instanceof Double) {
            v = ((Double) mySoln.varMapping.get(label)).floatValue();
        }
        return Tainter.setTag(v, Tag.of(var));
    }

    public static double symbolic(String label, double v) {
        PathUtils.checkLabelAndInitJPF(label);
        Expression var = new RealVariable(label, Double.MIN_VALUE, Double.MAX_VALUE);
        symbolicLabels.put(var, label);
        if (mySoln != null && !mySoln.isUnconstrained && mySoln.varMapping.get(label) instanceof Double) {
            v = (Double) mySoln.varMapping.get(label);
        }
        return Tainter.setTag(v, Tag.of(var));
    }

    public static String symbolic(String label, String v) {
        if (v == null) return null;
        // Tag the String reference with a StringVariable. (Per-character
        // taints that the original code stored in valuePHOSPHOR_TAG.taints
        // are out of scope for the SPI port; see report.)
        Expression var = new StringVariable(label);
        symbolicLabels.put(var, label);
        if (mySoln != null && !mySoln.isUnconstrained && mySoln.varMapping.get(label) instanceof String) {
            v = (String) mySoln.varMapping.get(label);
        }
        return Tainter.setTag(v, Tag.of(var));
    }

    public static byte[] symbolic(String label, byte[] v) {
        if (v == null) return null;
        // Tag each element with its own per-element BV variable so that
        // subsequent reads pick up symbolic content.
        for (int i = 0; i < v.length; i++) {
            Expression elemVar = new BVVariable(label + "_b" + i, 8);
            symbolicLabels.put(elemVar, label + "_b" + i);
            v[i] = Tainter.setTag(v[i], Tag.of(elemVar));
        }
        // Also tag the array reference itself with a marker variable.
        Expression refVar = new BVVariable(label + "_arr", 32);
        symbolicLabels.put(refVar, label);
        return Tainter.setTag(v, Tag.of(refVar));
    }

    @SuppressWarnings("unchecked")
    public static <T> T symbolic(String label, T v) {
        if (v == null) return null;
        if (v instanceof String) {
            return (T) symbolic(label, (String) v);
        }
        // Generic object: just tag the reference.
        PathUtils.checkLabelAndInitJPF(label);
        Expression var = new IntVariable(label, 0, 1);
        symbolicLabels.put(var, label);
        return Tainter.setTag(v, Tag.of(var));
    }

    public static <T> T[] symbolic(T[] in) {
        return symbolic(generateLabel(), in);
    }

    // ---------- symbolic(T) — auto-labelled ----------

    public static int symbolic(int v)         { return symbolic(generateLabel(), v); }
    public static long symbolic(long v)       { return symbolic(generateLabel(), v); }
    public static byte symbolic(byte v)       { return symbolic(generateLabel(), v); }
    public static short symbolic(short v)     { return symbolic(generateLabel(), v); }
    public static char symbolic(char v)       { return symbolic(generateLabel(), v); }
    public static boolean symbolic(boolean v) { return symbolic(generateLabel(), v); }
    public static float symbolic(float v)     { return symbolic(generateLabel(), v); }
    public static double symbolic(double v)   { return symbolic(generateLabel(), v); }
    public static byte[] symbolic(byte[] v)   { return symbolic(generateLabel(), v); }

    @SuppressWarnings("unchecked")
    public static <T> T symbolic(T v)         { return (T) symbolic(generateLabel(), (Object) v); }

    // ---------- generateExpression / getExpression ----------

    public static Expression generateExpression(String lbl, int sort) {
        if (!lblCounters.containsKey(lbl))
            lblCounters.put(lbl, new AtomicInteger());
        AtomicInteger i = lblCounters.get(lbl);
        lbl = lbl + "_" + i.getAndIncrement();
        PathUtils.checkLabelAndInitJPF(lbl);
        // sort values match org.objectweb.asm.Type sort constants:
        //   VOID=0, BOOLEAN=1, CHAR=2, BYTE=3, SHORT=4, INT=5, FLOAT=6,
        //   LONG=7, DOUBLE=8, ARRAY=9, OBJECT=10
        Expression ret;
        switch (sort) {
            case 1: // BOOLEAN
                ret = new IntVariable(lbl, 0, 1);
                break;
            case 2: // CHAR
                ret = new BinaryOperation(Operator.BIT_AND, new BVVariable(lbl, 32), FFFF_32);
                break;
            case 3: // BYTE
                ret = new BinaryOperation(Operator.BIT_AND, new BVVariable(lbl, 32), FF_32);
                break;
            case 4: // SHORT
                ret = new BinaryOperation(Operator.BIT_AND, new BVVariable(lbl, 32), FFFF_32);
                break;
            case 5: // INT
                ret = new BVVariable(lbl, 32);
                break;
            case 6: // FLOAT
                ret = new RealVariable(lbl,
                        Double.valueOf(Float.MIN_VALUE),
                        Double.valueOf(Float.MAX_VALUE));
                break;
            case 7: // LONG
                ret = new BVVariable(lbl, 64);
                break;
            case 8: // DOUBLE
                ret = new RealVariable(lbl, Double.MIN_VALUE, Double.MAX_VALUE);
                break;
            case 10: // OBJECT
                ret = new IntVariable(lbl, 0, 1);
                break;
            case 9: // ARRAY
            default:
                throw new UnsupportedOperationException("sort=" + sort);
        }
        return ret;
    }

    /** Read the Expression attached to a tagged primitive. Null if untagged. */
    public static Expression getExpression(int v)     { return extract(Tainter.getTag(v)); }
    public static Expression getExpression(long v)    { return extract(Tainter.getTag(v)); }
    public static Expression getExpression(byte v)    { return extract(Tainter.getTag(v)); }
    public static Expression getExpression(char v)    { return extract(Tainter.getTag(v)); }
    public static Expression getExpression(short v)   { return extract(Tainter.getTag(v)); }
    public static Expression getExpression(float v)   { return extract(Tainter.getTag(v)); }
    public static Expression getExpression(double v)  { return extract(Tainter.getTag(v)); }
    public static Expression getExpression(boolean v) { return extract(Tainter.getTag(v)); }
    public static Expression getExpression(Object v)  { return extract(Tainter.getTag(v)); }

    private static Expression extract(Tag t) {
        if (Tag.isEmpty(t)) return null;
        Object[] labels = Tag.getLabels(t);
        return labels.length == 0 ? null : (Expression) labels[0];
    }
}
