package edu.gmu.swe.knarr.server;

import edu.gmu.swe.knarr.runtime.Coverage;
import za.ac.sun.cs.green.expr.BVConstant;
import za.ac.sun.cs.green.expr.BVVariable;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.Variable;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

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

            while (var < 40) {
                n++;
                Input mutated = mutateInput(toMutate, var);

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

    private Input mutateInput(Input in, int inputToNegate) {

        // Make a copy of the input so we can modify it
        Canonizer c = new Canonizer();
        for (Entry<String, HashSet<Expression>> entry : in.constraints.getCanonical().entrySet())
            c.getCanonical().put(entry.getKey(), new HashSet<>(entry.getValue()));

        for (Entry<String, HashSet<Expression>> entry : in.constraints.getConstArrayInits().entrySet())
            c.getConstArrayInits().put(entry.getKey(), new HashSet<>(entry.getValue()));

        c.getNotCanonical().addAll(in.constraints.getNotCanonical());
        c.getVariables().addAll(in.constraints.getVariables());
        c.getExpressionMap().putAll(in.constraints.getExpressionMap());
        c.getOrder().addAll(in.constraints.getOrder());

        in = null;

        Map<String, Expression> res = c.getExpressionMap();
        ArrayList<SimpleEntry<String, Object>> sat = new ArrayList<>();
        HashSet<String> unsat = new HashSet<>();
        ConstraintServerHandler.solve(res, sat, unsat);

        // Solve the initial constraints
        // TODO maybe we can cache this to avoid one solver call
        if (sat.isEmpty())
            throw new Error("UNSAT constraints to start with");

        // Find variable holding input to negate
        Variable varToNegate = null;
        {
            int i = 0;
            for (Variable v : c.getVariables()) {
                if (i++ == inputToNegate) {
                    varToNegate = v;
                    break;
                }
            }
        }

        if (varToNegate == null)
            return null;

        // Find value to negate
        Object valueToNegate = null;
        for (SimpleEntry<String, Object> e : sat) {
            if (e.getKey().equals(varToNegate.getName())) {
                valueToNegate = e.getValue();
                break;
            }
        }

        if (valueToNegate == null)
            return null;

        // Add negated input to constraints
        Expression negatedInput = new Operation(
                Operator.NOT,
                new Operation(Operator.EQUALS, varToNegate, new BVConstant((int)valueToNegate, ((BVVariable)varToNegate).getSize()))
        );

        // Solve
        while (true) {
            c.getCanonical().get(varToNegate.getName()).add(negatedInput);
            c.getOrder().addLast(negatedInput);
            c.getExpressionMap().put(negatedInput.toString(), negatedInput);

            res = c.getExpressionMap();

            sat.clear();
            unsat.clear();
            ConstraintServerHandler.solve(res, sat, unsat);

            if (!sat.isEmpty()) {
                // SAT -> generate input
                byte[] buf = new byte[sat.size()];
                int i = 0;
                for (Entry<String, Object> e: sat) {
                    if (!e.getKey().startsWith("autoVar_"))
                        break;
                    Integer b = (Integer) e.getValue();
                    if (b == null)
                        break;

                    buf[i++] = b.byteValue();
                }

                Input ret = new Input();
                ret.input = buf;
                return ret;
            } else if (!unsat.isEmpty()) {
                // UNSAT

                // Find latest constraint in UNSAT core
                boolean found = false;
                LinkedList<Expression> newOrder = new LinkedList<>();
                LinkedList<Expression> toRemove = new LinkedList<>();
                for (Expression e : c.getOrder()) {
                    if (unsat.contains(e.toString())) {
                        found = true;
                    }

                    (!found ? newOrder : toRemove).addLast(e);
                }

                // Remove it and all later constraints
                for (HashSet<Expression> es : c.getCanonical().values())
                    es.removeAll(toRemove);

                for (HashSet<Expression> es : c.getConstArrayInits().values())
                    es.removeAll(toRemove);

                c.getNotCanonical().removeAll(toRemove);

                // Try again
                continue;
            } else {
                return null;
            }
        }
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

    private static class Input {
        Canonizer constraints;
        Coverage coverage;
        byte[] input;

        public void toFiles(File dirToSave, int nth) {
            try {
                save(constraints, "constraints_" + nth, dirToSave);
                save(coverage, "coverage_" + nth, dirToSave);
                save(input, "input_" + nth, dirToSave);
            } catch (IOException e) {
                throw new Error(e);
            }
        }

        private void save(Object toSave, String fileName, File dirToSave) throws IOException {
            URI uri = Paths.get(dirToSave.getAbsolutePath(), fileName).toUri();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(uri)))) {
                oos.writeObject(toSave);
            }

        }

        private void save(byte[] toSave, String fileName, File dirToSave) throws IOException {
            URI uri = Paths.get(dirToSave.getAbsolutePath(), fileName).toUri();
            try (FileOutputStream fos = new FileOutputStream(new File(uri))) {
                fos.write(toSave);
            }
        }
    }
}
