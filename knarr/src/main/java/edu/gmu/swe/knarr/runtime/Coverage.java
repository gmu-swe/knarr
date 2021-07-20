package edu.gmu.swe.knarr.runtime;

import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type;

import java.io.*;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Random;

public class Coverage implements Serializable {
    private static final long serialVersionUID = -6059233792632965508L;
    public static transient int SIZE = 1 << 20; // Don't make it final to avoid stupid javac constant propagation

    public final int[] codeCoverage = new int[SIZE];
    public final int[] pathCoverage = new int[SIZE];

    public static final String INTERNAL_NAME = Type.getType(Coverage.class).getInternalName();
    public static final String DESCRIPTOR = Type.getType(Coverage.class).getDescriptor();

    public static transient Coverage instance = new Coverage();

    private static boolean enabled;
    private static transient Optional<String[]> whitelist;

    static {
        String c = System.getProperty("addCov");
        if (c == null) {
            // Disable
            enabled = false;
            whitelist = Optional.empty();
        } else if ("".equals(c)) {
            // Without arguments -DaddCov adds coverage to all classes
            // Add universal prefix: "/"
            enabled = true;
            whitelist = Optional.empty();
        } else {
            // Add individual packages
            enabled = true;
            LinkedList<String> paks = new LinkedList<>();
            for (String pak : c.split(":")) {
                paks.addLast(pak);
            }

            whitelist = Optional.of(paks.toArray(new String[0]));
        }
    }

    public static boolean isCovEnabled(String className) {
        if (!enabled)
            return false;

        if (!whitelist.isPresent())
            return true;

        for (String prefix : whitelist.get()) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    public static void setCov(boolean enabled, Optional<String[]> whitelist) {
        Coverage.enabled = enabled;
        Coverage.whitelist = whitelist;
    }

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

    public static abstract class CoverageData implements Externalizable{
        private static final long serialVersionUID = 2555941994342681090L;
        public boolean taken;
        public String source;

        public CoverageData(){

        }
        public CoverageData(boolean taken, String source){
            this.taken  =taken;
            this.source =source;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeBoolean(this.taken);
            if(this.source == null)
                out.writeBoolean(false);
            else{
                out.writeBoolean(true);
                out.writeUTF(this.source);
            }
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            this.taken = in.readBoolean();
            if(in.readBoolean())
                this.source = in.readUTF();
            else
                this.source = null;
        }
    }

    public static class BranchData extends CoverageData {
        private static final long serialVersionUID = -2776780881587606089L;

        public int takenCode;
        public int notTakenCode;
        public int notTakenPath;
        public boolean breaksLoop;

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

        public BranchData(){

        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            out.writeInt(takenCode);
            out.writeInt(notTakenCode);
            out.writeInt(notTakenPath);
            out.writeBoolean(breaksLoop);
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            this.takenCode = in.readInt();
            this.notTakenCode = in.readInt();
            this.notTakenPath = in.readInt();
            this.breaksLoop = in.readBoolean();
        }
    }

    public static class SwitchData extends CoverageData {
        private static final long serialVersionUID = -2776780881587606089L;

        public int switchID;
        public int numArms; // does NOT account for default, default case is arm == numArms
        public int arm;
        public SwitchData(int switchID, int numArms, int arm, boolean taken, String source){
            this.switchID = switchID;
            this.numArms = numArms;
            this.arm = arm;
            this.taken = taken;
            this.source = source;
        }

        public SwitchData(){

        }


        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            out.writeInt(switchID);
            out.writeInt(numArms);
            out.writeInt(arm);
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            this.switchID = in.readInt();
            this.numArms = in.readInt();
            this.arm = in.readInt();
        }
    }


    public static Coverage newCoverage() {
        return ("AFL".equals(System.getProperty("addCov"))) ? new AFLCoverage() : new Coverage();
    }
}
