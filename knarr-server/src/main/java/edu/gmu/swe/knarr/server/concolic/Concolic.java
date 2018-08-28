package edu.gmu.swe.knarr.server.concolic;

import edu.gmu.swe.knarr.runtime.Coverage;
import edu.gmu.swe.knarr.server.Canonizer;
import edu.gmu.swe.knarr.server.ConstraintServerHandler;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

public class Concolic {

    public static void main(String[] args) throws IOException {
        Concolic c = new Concolic();

        c.startConstraintServer();

        c.addInitialInput(new File(args[0]), new File(args[1]));

        c.loop(new File(args[1]));
    }

    private Coverage master = new Coverage();
    private LinkedList<Input> inputs = new LinkedList<>();
    private int lastAddedInput = 0;
    private ServerSocket listener;

    private void addInitialInput(File f, File dirToSave) throws IOException {
        byte[] data = Files.readAllBytes(f.toPath());

        ConstraintServerHandler server = driver(data);

        // Get the constraints and coverage from the server
        Input in = new Input();
        in.constraints = new Canonizer();
        in.constraints.canonize(server.req);
        in.coverage = server.cov;
        in.input = data;

        master.merge(in.coverage);

        inputs.add(in);

        in.toFiles(dirToSave, 0);
    }

    private void loop(File dirToSave) {

        int n = 0;

        while (true) {
            Input in = pickInput();
            Input toMutate = in;
            System.out.println("Picking new input");

            int maxTries = 10;
            int tries = 0;
            int var = 0;

//            Mutator mutator = new VariableMutator();
            Mutator mutator = new ConstraintMutator(master);

            while (var < 4000) {
                n++;
                Input mutated = mutator.mutateInput(toMutate, var);

                if (mutated == Mutator.OUT_OF_RANGE) {
                    if (var == 0) {
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

                try {
                    executeInput(mutated);
                } catch (IOException e) {
                    e.printStackTrace();
                    tries  = 0;
                    var++;
                    toMutate = in;
                    System.out.println("Moving to var " + var);
                    continue;
                }

                // Better coverage?
                if (saveInput(mutated, dirToSave)) {
                    tries = 0;
                    toMutate = mutated;
                } else if (tries == maxTries) {
                    tries  = 0;
                    var++;
                    toMutate = in;
                    System.out.println("Moving to var " + var);
                } else {
                    toMutate = mutated;
                    tries++;
                }
            }
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
        Input ret = inputs.removeFirst();
        inputs.addLast(ret);
        return ret;
    }

    private void removeInput(Input in) {
        Iterator<Input> iter = inputs.descendingIterator();
        while (iter.hasNext()) {
            Input i = iter.next();

            if (i == in) {
                iter.remove();
                break;
            }
        }
    }

    private void executeInput(Input in) throws IOException {
        // Send the input to the server
        ConstraintServerHandler server = driver(in.input);

        // By the time we get an answer, or the connection closes
        // we should already have constraints

        // Get the constraints and coverage from the server
        in.constraints = new Canonizer();
        in.constraints.canonize(server.req);
        in.coverage = server.cov;
    }

    private ConstraintServerHandler driver(byte[] in) throws IOException {
        // TODO move out of the concolic executor

        // Add two endlines to ensure request is attempted to be parsed
        // Instead of timing out
        String toSend = new String(in) + "\n\n";

        // Connect to the HTTP server
        try(Socket s = new Socket("127.0.0.1",8080)) {
            s.setSoTimeout(2000);
            BufferedWriter bw =  new BufferedWriter(
                    new OutputStreamWriter(s.getOutputStream()));

            bw.write(toSend);
            bw.flush();

            // TODO do something with the timeout input
            // Get the constraints from the server
            ConstraintServerHandler ret;
            while (true) {
                try (Socket skt = listener.accept()) {
                    ret = new ConstraintServerHandler(skt);
                    break;
                } catch (InterruptedIOException e) {
                    bw.write("\n");
                    bw.flush();
                }
            }

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(s.getInputStream()));

            String res = br.readLine();
            // TODO do something with the result

            return ret;
        }
    }

    private boolean saveInput(Input candidate, File dirToSave) {
        if (!master.coversTheSameAs(candidate.coverage)) {
            System.out.println("Added input " + lastAddedInput + "!");
            inputs.addLast(candidate);

            // Update master coverage
            master.merge(candidate.coverage);

            // Save input to file-system
            candidate.toFiles(dirToSave, lastAddedInput++);

            return true;
        } else {
            return false;
        }
    }

}
