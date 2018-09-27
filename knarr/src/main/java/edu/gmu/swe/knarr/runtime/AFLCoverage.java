package edu.gmu.swe.knarr.runtime;

import java.io.Serializable;
import java.util.BitSet;
import java.util.Random;

public class AFLCoverage extends Coverage implements Serializable {
    private static final long serialVersionUID = 2888531132430305730L;
    private static final int BUFFER_SIZE = 65536; // the measurement data block

    private byte mem[] = new byte[BUFFER_SIZE];
    private int prev_location = 0;
    private long jumps = 0;

    @Override
    public void setCode(int id) {
        set(id);
    }

    @Override
    public void setPath(int id) {
        set(id);
    }

    @Override
    public void set(int id) {
        mem[id^prev_location]++;
        prev_location = id >> 1;
        jumps++;
    }

    @Override
    public int set(int takenID, int notTakenID) {
        notTakenID = notTakenID ^ prev_location;
        this.set(takenID);

        return notTakenID;
    }

    @Override
    public long countCodeCoverage() {
        long ret = 0;
        for (int i = 0 ; i < BUFFER_SIZE ; i++)
            if (mem[i] > 0)
                ret++;

        return ret;
    }

    @Override
    public long countCoverage() {
        return jumps;
    }

    @Override
    public boolean coversTheSameCodeAs(Coverage c) {
        AFLCoverage cc = (AFLCoverage) c;

        for (int i = 0 ; i < BUFFER_SIZE ; i++)
            if (mem[i] == 0 && cc.mem[i] > 0)
                return false;

        return true;
    }

    @Override
    public boolean coversTheSameAs(Coverage c) {
        AFLCoverage cc = (AFLCoverage) c;

        for (int i = 0 ; i < BUFFER_SIZE ; i++) {
            if (cc.mem[i] > mem[i] && bucket(cc.mem[i]) > bucket(mem[i]))
                return false;
        }

        return true;
//
//        return false;
    }

    private int bucket(int n) {
        int ret = 0;

        while (n != 0) {
            ret++;
            n >>= 1;
        }

        return ret;
    }

    @Override
    public void reset() {
        for (int i = 0; i < BUFFER_SIZE ; i++)
            mem[i] = 0;
        jumps = 0L;
    }

    @Override
    public void merge(Coverage c) {
        AFLCoverage cc = (AFLCoverage) c;

        for (int i = 0; i < BUFFER_SIZE ; i++)
            if (cc.mem[i] > mem[i])
                mem[i] = cc.mem[i];
    }

    private static BitSet used = new BitSet(SIZE*32);
    private static Random r = new Random();

    @Override
    /*default*/ int getNewLocationId() {
        int id;
        int tries = 0;
        do {
            id = r.nextInt(BUFFER_SIZE); // 32 bits per int, at least
            tries++;
        } while (tries <= 10 && !used.get(id));
        used.set(id);
        return id;
    }
}
