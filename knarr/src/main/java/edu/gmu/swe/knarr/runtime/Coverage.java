package edu.gmu.swe.knarr.runtime;

import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type;

import java.io.Serializable;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.Random;

public class Coverage implements Serializable {
    private static final long serialVersionUID = -6059233792632965508L;
    public static transient int SIZE = 1 << 20; // Don't make it final to avoid stupid javac constant propagation

    public final int[] codeCoverage = new int[SIZE];
    public final int[] pathCoverage = new int[SIZE];

    public static final String INTERNAL_NAME = Type.getType(Coverage.class).getInternalName();
    public static final String DESCRIPTOR = Type.getType(Coverage.class).getDescriptor();

    public static boolean enabled = (System.getProperty("addCov") != null);
    public static transient Coverage instance = new Coverage();

    public static int count = 0;
    public int thisCount;

    private static LinkedList<ThreadLocalID> ids = new LinkedList<>();
//    private ThreadLocalID lastID = new ThreadLocalID();
    private int lastID = 0;

    public void setCode(int id) {
        codeCoverage[(id / 32)] |= (1 << id % 32);
    }

    public void setPath(int id){
        pathCoverage[(id / 32)] |= (1 << id % 32);
    }

    public void set(int id){
        setCode(id);

        int pathID = (lastID ^ id) % SIZE;
        pathID *= (pathID > 0 ? 1 : -1);
        setPath(pathID);
        lastID = pathID >> 1;

//        System.out.println("\t" + lastID + "\t" + id);
    }

    public int set(int takenID, int notTakenID) {
        set(takenID);

        int pathID = (lastID ^ notTakenID) % SIZE;
        pathID *= (pathID > 0 ? 1 : -1);

        return pathID;
    }

    public void print() {
        int res = 0;
        int res2 = 0;
        for (int i = 0 ; i < SIZE ; i++) {
            res += codeCoverage[i];
            res2 += pathCoverage[i];
        }


        System.out.println(res + " " + res2);
    }

    public long countCodeCoverage() {
        long total = 0;

        for (int i = 0 ; i < SIZE ; i++) {
            int n = this.codeCoverage[i];
            while (n > 0) {
                total += n & 1;
                n >>>= 1;
            }
        }

        return total;
    }

    public long countCoverage() {
        long total = countCodeCoverage();

        for (int i = 0 ; i < SIZE ; i++) {
            int n = this.pathCoverage[i];
            while (n > 0) {
                total += n & 1;
                n >>>= 1;
            }
        }

        return total;
    }

    public boolean coversTheSameCodeAs(Coverage c) {
        for (int i = 0 ; i < SIZE ; i++) {
            if ((this.codeCoverage[i] | c.codeCoverage[i]) != this.codeCoverage[i])
                return false;
        }

        return true;
    }

    public boolean coversTheSameAs(Coverage c) {
        for (int i = 0 ; i < SIZE ; i++) {
            if ((this.codeCoverage[i] | c.codeCoverage[i]) != this.codeCoverage[i])
                return false;
            if ((this.pathCoverage[i] | c.pathCoverage[i]) != this.pathCoverage[i])
                return false;
        }

        return true;
    }

    public void reset() {
        for (int i = 0 ; i < SIZE ; i++) {
            codeCoverage[i] = 0;
            pathCoverage[i] = 0;
        }

        synchronized (ids) {
            for (ThreadLocalID id : ids)
                id.set(0);
        }

        lastID = 0;
    }

    public void merge(Coverage c) {
        for (int i = 0 ; i < SIZE ; i++) {
            codeCoverage[i] |= c.codeCoverage[i];
            pathCoverage[i] |= c.pathCoverage[i];
        }
    }

    private static BitSet used = new BitSet(SIZE*32);
    private static Random r = new Random();

    /*default*/ int getNewLocationId() {
        int id;
        int tries = 0;
        do {
            id = r.nextInt(SIZE*32); // 32 bits per int, at least
            tries++;
        } while (tries <= 10 && !used.get(id));
        used.set(id);
        return id;
    }

    private static class ThreadLocalID extends ThreadLocal<Integer> implements Serializable {

        @Override
        protected Integer initialValue() {
            synchronized (ids) {
                ids.add(this);
            }
            return 0;
        }

    }

    public static class BranchData implements Serializable {
        private static final long serialVersionUID = -2776780881587606089L;

        public final int takenCode;
        public final int notTakenCode;
        public final int notTakenPath;
        public final boolean breaksLoop;
        public final boolean taken;
        public final String source;

        public BranchData(int takenCode, int notTakenCode, int notTakenPath, boolean breaksLoop, boolean taken) {
            this.takenCode = takenCode;
            this.notTakenCode = notTakenCode;
            this.notTakenPath = notTakenPath;
            this.breaksLoop = breaksLoop;
            this.taken = taken;
            this.source = "";
        }

        public BranchData(int takenCode, int notTakenCode, int notTakenPath, boolean breaksLoop, boolean taken, String source) {
            this.takenCode = takenCode;
            this.notTakenCode = notTakenCode;
            this.notTakenPath = notTakenPath;
            this.breaksLoop = breaksLoop;
            this.taken = taken;
            this.source = source;
        }
    }

    public static Coverage newCoverage() {
        return ("AFL".equals(System.getProperty("addCov"))) ? new AFLCoverage() : new Coverage();
    }
}
