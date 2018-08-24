package edu.gmu.swe.knarr.runtime;

import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type;
import za.ac.sun.cs.green.expr.Expression;

import java.io.Serializable;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Random;

public class Coverage implements Serializable {
    private static final long serialVersionUID = -6059233792632965508L;
    public static transient int SIZE = 1 << 20; // Don't make it final to avoid stupid javac constant propagation
    public static transient Coverage instance = new Coverage();
    public final int[] coverage = new int[SIZE];

    public HashMap<Expression, Integer> notTaken = new HashMap<>();

    public static final String INTERNAL_NAME = Type.getType(Coverage.class).getInternalName();
    public static final String DESCRIPTOR = Type.getType(Coverage.class).getDescriptor();

    public static boolean enabled = (System.getProperty("addCov") != null);

    public void print() {
        int res = 0;
        for (int i = 0 ; i < SIZE ; i++)
            res += coverage[i];


        System.out.println(res);
    }

    public boolean coversTheSameAs(Coverage c) {
        for (int i = 0 ; i < SIZE ; i++) {
            if ((this.coverage[i] | c.coverage[i]) != this.coverage[i])
                return false;
        }

        return true;
    }

    public void reset() {
        for (int i = 0 ; i < SIZE ; i++)
            coverage[i] = 0;

        notTaken.clear();
    }

    public void merge(Coverage c) {
        for (int i = 0 ; i < SIZE ; i++)
            coverage[i] |= c.coverage[i];
    }

    private static BitSet used = new BitSet(SIZE*32);
    private static Random r = new Random();

    /*default*/ static int getNewLocationId() {
        int id;
        int tries = 0;
        do {
            id = r.nextInt(SIZE*32); // 32 bits per int, at least
            tries++;
        } while (tries <= 10 && !used.get(id));
        used.set(id);
        return id;
    }
}
