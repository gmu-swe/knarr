package edu.gmu.swe.knarr.server.concolic;

import edu.gmu.swe.knarr.server.Canonizer;
import edu.gmu.swe.knarr.server.ConstraintServer;
import edu.gmu.swe.knarr.server.ConstraintServerHandler;
import edu.gmu.swe.knarr.server.concolic.driver.Driver;
import edu.gmu.swe.knarr.server.concolic.driver.HTTPDriver;
import edu.gmu.swe.knarr.server.concolic.driver.IntSerialDriver;
import edu.gmu.swe.knarr.server.concolic.mutator.ConstraintMutator;
import edu.gmu.swe.knarr.server.concolic.mutator.FixedOutputMutator;
import edu.gmu.swe.knarr.server.concolic.mutator.MaxConstraintsMutator;
import edu.gmu.swe.knarr.server.concolic.mutator.ImportMutator;
import edu.gmu.swe.knarr.server.concolic.mutator.Mutator;
import edu.gmu.swe.knarr.server.concolic.mutator.VariableMutator;
import edu.gmu.swe.knarr.server.concolic.picker.MaxConstraintsPicker;
import edu.gmu.swe.knarr.server.concolic.picker.MaxMeanConstraintsPicker;
import edu.gmu.swe.knarr.server.concolic.picker.MaxPathsPicker;
import edu.gmu.swe.knarr.server.concolic.picker.MaxStaticCoveragePicker;
import edu.gmu.swe.knarr.server.concolic.picker.Picker;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Paths;

public class Concolic {

    public static void main(String[] args) throws IOException {
        Concolic c = new Concolic();

        c.startConstraintServer();

        switch (args[0]) {
            case "http":
                c.setHTTPDriver();
                break;
            case "int":
                c.setIntDriver();
                break;
            default:
                throw new Error("Unknown drive: " + args[0]);
        }

        c.initMutators(args.length > 3 ? new File(args[3]) : null);

        c.addInitialInput(new File(args[1]), new File(args[2]));

        c.loop(new File(args[2]));
    }

    private Driver driver;
    private int lastAddedInput = 0;
    private ServerSocket listener;
    private Mutator[] mutators;
    private Picker picker = new MaxConstraintsPicker();

    private int mutatorInUse = 0;

    private void setHTTPDriver() {
        this.driver = new HTTPDriver(listener, "127.0.0.1", 8080);
    }

    private void setIntDriver() {
        this.driver = new IntSerialDriver(listener, "127.0.0.1", 8080);
    }

    private void initMutators() {
//        int[] sorted = new int[200];
//        for (int i = 0 ; i < sorted.length ; i++) {
//            sorted[i] = i;
//        }
        mutators = new Mutator[]{
//                new ConstraintMutator(driver, picker.getCurrentCoverage(), true, false),
//                new ConstraintMutator(driver, picker.getCurrentCoverage(), false, false),
                new ConstraintMutator(driver, picker.getCurrentCoverage(), true, true),
                new ConstraintMutator(driver, picker.getCurrentCoverage(), false, true),
                new MaxConstraintsMutator(driver, (MaxConstraintsPicker) picker),
//                new VariableMutator(driver),
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

        picker.saveInput(in);
        picker.setThreshold(MaxConstraintsPicker.countConstraints(in.constraints));

        in.toFiles(dirToSave, driver, "");
    }

    private void loop(File dirToSave) {

        int n = 1;
        int newMutator = 500;

        Mutator mutator = mutators[mutatorInUse];

        while (true) {
            Input in = picker.pickInput();

            if (in == null || (n % newMutator) == 0) {
                // No more inputs in rotation, change strategy
                mutatorInUse = (mutatorInUse + 1) % mutators.length;
                mutator = mutators[mutatorInUse];
                System.out.println("Changing mutator to: " + mutator);
                picker.reset();
                n++;
                continue;
            }


            int maxTries = 1;
            int tries = 0;
            int var = 0;

            boolean generatedAtLeastOneInput = false;

            while (var < 4000 && (n % newMutator) != 0) {
                Input toMutate = picker.pickInput();

                if (toMutate != in)
                    break;

                n++;
                Input mutated = mutator.mutateInput(toMutate, var);

                if (mutated == Mutator.OUT_OF_RANGE) {
//                    if (var == 0 && tries == 0) {
                        // was not able to generate a single new input
                        // Remove from rotation
                        picker.removeInput(in);
//                    }
                    break;
                }

                if (mutated == null) {
                    tries  = 0;
                    var++;
//                    toMutate = in;
                    System.out.println("Moving to var " + var);
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
                    tries  = 0;
                    var++;
                    toMutate = in;
                    System.out.println("Moving to var " + var);
                    continue;
                }

                // Better coverage?
                if (saveInput(mutated, dirToSave)) {
                    generatedAtLeastOneInput = true;
//                    tries = 0;
//                    tries++;
//                    toMutate = mutated;
//                } else if (tries >= maxTries) {
//                    tries  = 0;
//                    var++;
//                    toMutate = in;
//                    System.out.println("Moving to var " + var);
//                } else {
//                    toMutate = mutated;
//                    tries++;
                }

                // Discard info not needed that takes a lot of memory
                // We can always get it back by re-executing the same input
                // That should be cheap
//                mutated.coverage = null;
//              candidate.constraints = null;

                var++;
                System.out.println("Moving to var " + var);
            }

            if (!generatedAtLeastOneInput)
                picker.removeInput(in);
        }

    }

    private void startConstraintServer() throws IOException {
        try {
            listener = new ServerSocket(9090);
//            listener.setSoTimeout(2000);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private boolean executeInput(Input in) throws IOException {
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

    private boolean saveInput(Input candidate, File dirToSave) {
        String reason;
        if ((reason = picker.saveInput(candidate)) != null) {
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
