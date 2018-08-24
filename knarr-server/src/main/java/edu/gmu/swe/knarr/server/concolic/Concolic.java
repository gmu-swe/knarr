package edu.gmu.swe.knarr.server.concolic;

import edu.gmu.swe.knarr.runtime.Coverage;
import edu.gmu.swe.knarr.server.Canonizer;
import edu.gmu.swe.knarr.server.ConstraintServerHandler;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.LinkedList;

public class Concolic {

    public static void main(String[] args) throws IOException {
        Concolic c = new Concolic();

        c.startConstraintServer();

        c.addInitialInput(new File(args[0]), new File(args[1]));

        c.loop(new File(args[1]));
    }

    private Coverage master = new Coverage();
    private LinkedList<Input> inputs = new LinkedList<>();
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

            Mutator mutator = new VariableMutator();

            while (var < 40) {
                n++;
                Input mutated = mutator.mutateInput(toMutate, var);

                if (mutated == null) {
                    tries  = 0;
                    var++;
                    toMutate = in;
                    System.out.println("Moving to var " + var);
                    continue;
                }

                executeInput(mutated);

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
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private Input pickInput() {
        Input ret = inputs.removeFirst();
        inputs.addLast(ret);
        return ret;
    }

    private void executeInput(Input in) {
        // Send the input to the server
        ConstraintServerHandler server = driver(in.input);

        // By the time we get an answer, or the connection closes
        // we should already have constraints

        // Get the constraints and coverage from the server
        in.constraints = new Canonizer();
        in.constraints.canonize(server.req);
        in.coverage = server.cov;
    }

    private ConstraintServerHandler driver(byte[] in) {
        // TODO move out of the concolic executor

        // Add two endlines to ensure request is attempted to be parsed
        // Instead of timing out
        String toSend = new String(in) + "\n\n";

        // Connect to the HTTP server
        try(Socket s = new Socket("127.0.0.1",8080)) {
            BufferedWriter bw =  new BufferedWriter(
                    new OutputStreamWriter(s.getOutputStream()));

            bw.write(toSend);
            bw.flush();

            // Get the constraints from the server
            ConstraintServerHandler ret;
            try (Socket skt = listener.accept()) {
                ret = new ConstraintServerHandler(skt);
            }

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(s.getInputStream()));

            String res = br.readLine();
            // TODO do something with the result

            return ret;
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private boolean saveInput(Input candidate, File dirToSave) {
        if (!master.coversTheSameAs(candidate.coverage)) {
            System.out.println("Added input!");
            inputs.addLast(candidate);

            // Update master coverage
            master.merge(candidate.coverage);

            // Save input to file-system
            candidate.toFiles(dirToSave, inputs.size() - 1);

            return true;
        } else {
            return false;
        }
    }

}
