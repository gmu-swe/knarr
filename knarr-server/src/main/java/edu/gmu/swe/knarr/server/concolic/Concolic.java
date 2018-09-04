package edu.gmu.swe.knarr.server.concolic;

import edu.gmu.swe.knarr.runtime.Coverage;
import edu.gmu.swe.knarr.server.Canonizer;
import edu.gmu.swe.knarr.server.ConstraintServerHandler;
import edu.gmu.swe.knarr.server.concolic.driver.Driver;
import edu.gmu.swe.knarr.server.concolic.driver.HTTPDriver;
import edu.gmu.swe.knarr.server.concolic.driver.IntSerialDriver;
import edu.gmu.swe.knarr.server.concolic.mutator.ConstraintMutator;
import edu.gmu.swe.knarr.server.concolic.mutator.Mutator;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Iterator;
import java.util.LinkedList;

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

        c.addInitialInput(new File(args[1]), new File(args[2]));

        c.loop(new File(args[2]));
    }

    private Driver driver;
    private Coverage master = new Coverage();
    private LinkedList<Input> inputsInRotation = new LinkedList<>();
    private LinkedList<Input> inputsOutOfRotation = new LinkedList<>();
    private int lastAddedInput = 0;
    private ServerSocket listener;
    private Mutator[] mutators = new Mutator[] {
            new ConstraintMutator(master, true, false),
            new ConstraintMutator(master, false, false),
            new ConstraintMutator(master, true, true),
            new ConstraintMutator(master, false, true),
//            new VariableMutator(),
    };

    private int mutatorInUse = 0;

    private void setHTTPDriver() {
        this.driver = new HTTPDriver(listener, "127.0.0.1", 8080);
    }

    private void setIntDriver() {
        this.driver = new IntSerialDriver(listener, "127.0.0.1", 8080);
    }

    private void addInitialInput(File f, File dirToSave) throws IOException {
        Object data = driver.fromFile(f);

        ConstraintServerHandler server = driver(data);

        if (server == null)
            throw new Error("First input failed");

        // Get the constraints and coverage from the server
        Input in = new Input();
        in.constraints = new Canonizer();
        in.constraints.canonize(server.req);
        in.coverage = server.cov;
        in.input = data;

        master.merge(in.coverage);

        inputsInRotation.add(in);

        in.toFiles(dirToSave, lastAddedInput++, driver);
    }

    private void loop(File dirToSave) {

        int n = 0;
        int newMutator = 100;

        Mutator mutator = mutators[mutatorInUse];

        while (true) {
            Input in = pickInput();

            if (in == null || (n % newMutator) == 0) {
                // No more inputs in rotation, change strategy
                mutatorInUse = (mutatorInUse + 1) % mutators.length;
                mutator = mutators[mutatorInUse];
                System.out.println("Changing mutator to: " + mutator);
                resetInputs();
                n++;
                continue;
            }

            Input toMutate = in;

            int maxTries = 0;
            int tries = 0;
            int var = 0;

            boolean generatedAtLeastOneInput = false;

            while (var < 4000 && (n % newMutator) != 0) {
                n++;
                Input mutated = mutator.mutateInput(toMutate, var);

                if (mutated == Mutator.OUT_OF_RANGE) {
                    if (var == 0 && tries == 0) {
                        // was not able to generate a single new input
                        // Remove from rotation
                        removeInput(in);
                    }
                    break;
                }

                if (mutated == null) {
                    tries  = 0;
                    var++;
                    toMutate = in;
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
                    tries++;
                    toMutate = mutated;
                } else if (tries >= maxTries) {
                    tries  = 0;
                    var++;
                    toMutate = in;
                    System.out.println("Moving to var " + var);
                } else {
                    toMutate = mutated;
                    tries++;
                }
            }

            if (!generatedAtLeastOneInput)
                removeInput(in);
        }

    }

    private void startConstraintServer() throws IOException {
        try {
            listener = new ServerSocket(9090);
            listener.setSoTimeout(2000);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private Input pickInput() {
        if (inputsInRotation.isEmpty())
            return null;

        Input ret = inputsInRotation.removeFirst();
        inputsInRotation.addLast(ret);
        System.out.println("Picking new input (" + inputsInRotation.size() + ")");
        return ret;
    }

    private void resetInputs() {
        inputsInRotation.addAll(inputsOutOfRotation);
        inputsOutOfRotation.clear();
    }

    private void removeInput(Input in) {
        Iterator<Input> iter = inputsInRotation.descendingIterator();
        while (iter.hasNext()) {
            Input i = iter.next();

            if (i == in) {
                iter.remove();
                inputsOutOfRotation.add(in);
                System.out.println("Removed input (" + inputsInRotation.size()+ " left)");
                break;
            }
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
        return true;
    }

    private boolean saveInput(Input candidate, File dirToSave) {
        if (!master.coversTheSameAs(candidate.coverage)) {
            System.out.println("Added input " + lastAddedInput + "!");
            inputsInRotation.addLast(candidate);

            // Update master coverage
            master.merge(candidate.coverage);

            // Save input to file-system
            candidate.toFiles(dirToSave, lastAddedInput++, driver);

            return true;
        } else {
            return false;
        }
    }

}
