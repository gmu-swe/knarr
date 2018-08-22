package edu.gmu.swe.knarr.runtime;

import java.io.Serializable;

public class Coverage implements Serializable {
    private static final long serialVersionUID = -6059233792632965508L;
    public static transient int SIZE = 1 << 20; // Don't make it final to avoid stupid javac constant propagation
    public static transient Coverage instance = new Coverage();
    public final int[] coverage = new int[SIZE];

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
    }
}
