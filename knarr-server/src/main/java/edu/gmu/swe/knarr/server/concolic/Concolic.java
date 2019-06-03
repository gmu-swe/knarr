package edu.gmu.swe.knarr.server.concolic;

import edu.gmu.swe.knarr.server.Canonizer;
import edu.gmu.swe.knarr.server.ConstraintServer;
import edu.gmu.swe.knarr.server.ConstraintServerHandler;
import edu.gmu.swe.knarr.server.concolic.driver.ByteDriver;
import edu.gmu.swe.knarr.server.concolic.driver.Driver;
import edu.gmu.swe.knarr.server.concolic.driver.HTTPDriver;
import edu.gmu.swe.knarr.server.concolic.driver.IntSerialDriver;
import edu.gmu.swe.knarr.server.concolic.mutator.ConstraintMutator;
import edu.gmu.swe.knarr.server.concolic.mutator.FixedOutputMutator;
import edu.gmu.swe.knarr.server.concolic.mutator.MaxConstraintsMutator;
import edu.gmu.swe.knarr.server.concolic.mutator.AllMaxConstraintsMutator;
import edu.gmu.swe.knarr.server.concolic.mutator.ImportMutator;
import edu.gmu.swe.knarr.server.concolic.mutator.Mutator;
import edu.gmu.swe.knarr.server.concolic.mutator.VariableMutator;
import edu.gmu.swe.knarr.server.concolic.picker.*;
import za.ac.sun.cs.green.service.z3.Z3JavaTranslator;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Concolic {

    public static void main(final String[] args) throws IOException {
        String to;
        if ((to = System.getProperty("Z3_TIMEOUT")) != null)
            Z3JavaTranslator.timeoutMS = Integer.parseInt(to);
        else
            Z3JavaTranslator.timeoutMS = 3600 * 1000; // 1h

        final Concolic c = new Concolic();

        c.startConstraintServer();

        switch (args[0]) {
            case "http":
                c.setHTTPDriver();
                break;
            case "int":
                c.setIntDriver();
                break;
            case "byte":
                c.setByteDriver();
                break;
            default:
                throw new Error("Unknown drive: " + args[0]);
        }

        c.initMutators(args.length > 3 ? new File(args[3]) : null, args.length > 4 ? new File(args[4]) : null);

        c.picker.initMutators(c.mutators);

        c.log = new Log(Paths.get(new File(args[2]).getAbsolutePath(), "log.csv").toFile());
        c.addInitialInput(new File(args[1]), new File(args[2]));

//        c.loop(new File(args[2]));
        Thread[] ts = new Thread[c.mutators.length];

        for (int i = 0 ; i < ts.length ; i++) {
            final Mutator m = c.mutators[i];
            ts[i] = new Thread() {
                @Override
                public void run() {
                    c.multithreadedLoop(new File(args[2]), m);
                }
            };
        }

        for (Thread t : ts) {
            t.start();
        }

        for (Thread t : ts) {
            while (true) {
                try {
                    t.join();
                    break;
                } catch (InterruptedException e) {
                    continue;
                }
            }
        }
    }

    /*default*/ Driver driver;
    private int lastAddedInput = 0;
    private ServerSocket listener;
    private Mutator[] mutators;
    private Log log;
    protected Picker picker = new MaxBytecodePicker();

    private int mutatorInUse = 0;

    /*default*/ void setHTTPDriver() {
        this.driver = new HTTPDriver(listener, "127.0.0.1", 8080);
    }

    /*default*/ void setIntDriver() {
        this.driver = new IntSerialDriver(listener, "127.0.0.1", 8080);
    }

    /*default*/ void setByteDriver() {
        this.driver = new ByteDriver(listener, "127.0.0.1", 8080);
    }

    private void initMutators(File importDir1, File importDir2) {
//        int[] sorted = new int[200];
//        for (int i = 0 ; i < sorted.length ; i++) {
//            sorted[i] = i;
//        }

        mutators = new Mutator[]{
//                new VariableMutator(driver, false).setName("VariableMutator"),
//                new VariableMutator(driver, true).setName("VariableMutatorReverse"),

                new ConstraintMutator(driver, picker.getCurrentCoverage(), true, true, true).setName("ConstraintMutatorReverseLoop"),
//                new ConstraintMutator(driver, picker.getCurrentCoverage(), true, true, false).setName("ConstraintMutatorLoop"),

//                new AllMaxConstraintsMutator(driver, (MaxConstraintsPicker) picker),
//                new MaxConstraintsMutator(driver, (MaxConstraintsPicker) picker),

                new ConstraintMutator(driver, picker.getCurrentCoverage(), false, true, true).setName("ConstraintMutatorLoop"),
//                new ConstraintMutator(driver, picker.getCurrentCoverage(), false, true, false).setName("ConstraintMutator"),

//                new ImportMutator(driver, importDir1),
//                new ImportMutator(driver, importDir2),

//                new FixedOutputMutator(driver,
//                        new int[] { 1 , 0 , 0 , 0 , 0 },
//                        new int[] { 2 , 1 , 0 , 0 , 0 },
//                        new int[] { 3 , 2 , 1 , 0 , 0 },
//                        new int[] { 10, 9, 8, 7, 6, 5, 4 , 3 , 2 , 1 , 0 }),
//                        sorted),
        };

    }

    private void addInitialInput(File f, File dirToSave) throws IOException {
        Object data = driver.fromFile(f);

        // 1st run just to initialize the target program, throw away results
        driver.drive(data);

        // Keep the results of the 2nd run
        ConstraintServerHandler server = driver.drive(data);

        if (server == null)
            throw new Error("First input failed");

        // Get the constraints and coverage from the server
        Input in = new Input();
        in.constraints = new Canonizer();
        in.constraints.canonize(server.req);
        in.coverage = server.cov;
        in.input = data;
        in.nth = lastAddedInput++;
        in.how = "initial";
        picker.score(in); //MaxConstraintsPicker.countConstraints(in.constraints);

        picker.saveInput(null, in);

        in.toFiles(dirToSave, driver, "");
    }

    private Lock l = new ReentrantLock();
    private Condition newInput = l.newCondition();

    private void multithreadedLoop(File dirToSave, Mutator mutator) {

        HashMap<Input, Integer> varState = new HashMap<>();

        while (true) {
            Input in;

            // Get input to mutate
            in = picker.pickInput(mutator);

            // Get mutation state for this input
            Integer var = varState.get(in);
            if (var == null) {
                var = 0;
            }

            // Mutate input
            Input mutated;
            try {
                mutated = mutator.mutateInput(in, var);
            } catch (Throwable e) {
                e.printStackTrace();
                continue;
            }

            varState.put(in, var+1);

            // Nothing more to do for this input, try another one
            if (mutated == Mutator.OUT_OF_RANGE) {
                // Tell the picker that we are not interested in the input any more
                picker.removeInput(in, mutator);
                varState.remove(in);
                continue;
            }

            if (mutated == null) {
                // Could not generate an input for this var, try next var
                continue;
            }

            boolean success;

            try {
                success = executeInput(mutated);

                // Save input
                saveInput(mutator, mutated, dirToSave);
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
        }
    }

//    private void loop(File dirToSave) {
//
//        int n = 1;
//        int newMutator = 500;
//
//        Mutator mutator = mutators[mutatorInUse];
//
//        while (true) {
//            Input in = picker.pickInput();
//
//            if (in == null || (n % newMutator) == 0) {
//                // No more inputs in rotation, change strategy
//                mutatorInUse = (mutatorInUse + 1) % mutators.length;
//                mutator = mutators[mutatorInUse];
//                System.out.println("Changing mutator to: " + mutator);
//                picker.reset();
//                n++;
//                continue;
//            }
//
//
//            int maxTries = 1;
//            int tries = 0;
//            int var = 0;
//
//            boolean generatedAtLeastOneInput = false;
//
//            while (var < 4000 && (n % newMutator) != 0) {
//                Input toMutate = picker.pickInput();
//
//                if (toMutate != in)
//                    break;
//
//                n++;
//                Input mutated = mutator.mutateInput(toMutate, var);
//
//                if (mutated == Mutator.OUT_OF_RANGE) {
////                    if (var == 0 && tries == 0) {
//                        // was not able to generate a single new input
//                        // Remove from rotation
//                        picker.removeInput(in);
////                    }
//                    break;
//                }
//
//                if (mutated == null) {
//                    tries  = 0;
//                    var++;
////                    toMutate = in;
//                    System.out.println("Moving to var " + var);
//                    continue;
//                }
//
//                boolean success;
//
//                try {
//                    success = executeInput(mutated);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                    success = false;
//                }
//
//                if (!success) {
//                    tries  = 0;
//                    var++;
//                    toMutate = in;
//                    System.out.println("Moving to var " + var);
//                    continue;
//                }
//
//                // Better coverage?
//                if (saveInput(mutated, dirToSave)) {
//                    generatedAtLeastOneInput = true;
////                    tries = 0;
////                    tries++;
////                    toMutate = mutated;
////                } else if (tries >= maxTries) {
////                    tries  = 0;
////                    var++;
////                    toMutate = in;
////                    System.out.println("Moving to var " + var);
////                } else {
////                    toMutate = mutated;
////                    tries++;
//                }
//
//                // Discard info not needed that takes a lot of memory
//                // We can always get it back by re-executing the same input
//                // That should be cheap
////                mutated.coverage = null;
////              candidate.constraints = null;
//
//                var++;
//                System.out.println("Moving to var " + var);
//            }
//
//            if (!generatedAtLeastOneInput)
//                picker.removeInput(in);
//        }
//
//    }

    /*default*/ void startConstraintServer() throws IOException {
        try {
            listener = new ServerSocket(9090);
//            listener.setSoTimeout(2000);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    protected boolean executeInput(Input in) throws IOException {
        // Send the input to the server
        ConstraintServerHandler server = driver.drive(in.input);

        if (server == null)
            return false;

        // By the time we get an answer, or the connection closes
        // we should already have constraints

        // Get the constraints and coverage from the server
        in.constraints = new Canonizer();
        in.constraints.canonize(server.req);
        in.coverage = server.cov;
        picker.score(in);
        return true;
    }

    private boolean saveInput(Mutator m, Input candidate, File dirToSave) throws IOException {
        String reason;
        if ((reason = picker.saveInput(m, candidate)) != null) {
            // Save input to file-system
            candidate.nth = lastAddedInput++;
            String fileName = candidate.toFiles(dirToSave, driver, reason);
            log.log(candidate, reason, fileName, m);

//            for (Input in = candidate ; in != null && in.newConstraint != null ; in = in.parent) {
//                System.out.print("\t");
//                System.out.print(in.newConstraint);
//                System.out.print(" ");
//            }
//
//            System.out.println();

            return true;
        } else {
            return false;
        }
    }

}
