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

        c.initMutators(args.length > 3 ? new File(args[3]) : null);

        c.picker.initMutators(c.mutators);

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

    private void initMutators(File importDir) {
//        int[] sorted = new int[200];
//        for (int i = 0 ; i < sorted.length ; i++) {
//            sorted[i] = i;
//        }
        mutators = new Mutator[]{
                new VariableMutator(driver, false),
                new VariableMutator(driver, true),

                new ConstraintMutator(driver, picker.getCurrentCoverage(), true, true, true),
                new ConstraintMutator(driver, picker.getCurrentCoverage(), true, true, false),

//                new AllMaxConstraintsMutator(driver, (MaxConstraintsPicker) picker),

                new ConstraintMutator(driver, picker.getCurrentCoverage(), false, true, true),
                new ConstraintMutator(driver, picker.getCurrentCoverage(), false, true, false),
//
//                new AllMaxConstraintsMutator(driver, (MaxConstraintsPicker) picker),
//                new MaxConstraintsMutator(driver, (MaxConstraintsPicker) picker),
//                new AllMaxConstraintsMutator(driver, (MaxConstraintsPicker) picker),
//                new FixedOutputMutator(driver,
//                        new int[] { 1 , 0 , 0 , 0 , 0 },
//                        new int[] { 2 , 1 , 0 , 0 , 0 },
//                        new int[] { 3 , 2 , 1 , 0 , 0 },
//                        new int[] { 10, 9, 8, 7, 6, 5, 4 , 3 , 2 , 1 , 0 }),
//                        sorted),
        };

        if (importDir != null) {
            Mutator importer = new ImportMutator(driver, importDir);
            Mutator[] old = mutators;
            mutators = new Mutator[(old.length * 2)];

            mutators[0] = importer;

            for (int i = 0 ; i < old.length ; i++) {
                mutators[(i*2) + 1] = old[i];

                if (i != old.length-1)
                    mutators[(i*2) + 1 + 1] = importer;
            }
        }

    }

    private void addInitialInput(File f, File dirToSave) throws IOException {
        Object data = driver.fromFile(f);

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

            varState.put(in, var+1);

            // Mutate input
            Input mutated = mutator.mutateInput(in, var);

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
            } catch (IOException e) {
                e.printStackTrace();
                success = false;
            }

            if (!success) {
                // Could not execute this input, try next var
                continue;
            }

            // Save input
            saveInput(mutator, mutated, dirToSave);
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

    private boolean saveInput(Mutator m, Input candidate, File dirToSave) {
        String reason;
        if ((reason = picker.saveInput(m, candidate)) != null) {
            // Save input to file-system
            candidate.nth = lastAddedInput++;
            candidate.toFiles(dirToSave, driver, reason);

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
