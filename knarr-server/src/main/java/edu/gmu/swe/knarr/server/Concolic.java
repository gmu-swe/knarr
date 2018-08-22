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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

        c.addInitialInput(new File(args[0]));

        c.loop();
    }

    private Coverage master = new Coverage();
    private LinkedList<Input> inputs = new LinkedList<>();
    private ServerSocket listener;

    private void addInitialInput(File f) throws IOException {
        byte[] data = Files.readAllBytes(f.toPath());

        ConstraintServerHandler server = driver(data);

        // Get the constraints and coverage from the server
        Input in = new Input();
        in.constraints = new Canonizer();
        in.constraints.canonize(server.req);
        in.coverage = server.cov;

        inputs.add(in);
    }

    private void loop() {

        while (true) {
            Input in = pickInput();

            Input mutated = mutateInput(in);

            executeInput(mutated);

            // Better coverage?
            saveInput(in, mutated);
        }

    }

    private void startConstraintServer() throws IOException {
        System.out.println(ConstraintServerHandler.inZ3);

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

    private Input mutateInput(Input in) {

        // Make a copy of the input so we can modify it
        Map<String, Expression> res = in.constraints.getExpressionMap();
        ArrayList<SimpleEntry<String, Object>> sat = new ArrayList<>();
        HashSet<String> unsat = new HashSet<>();
        ConstraintServerHandler.solve(res, sat, unsat);

        // Solve the initial constraints
        // TODO maybe we can cache this to avoid one solver call
        if (sat.isEmpty())
            throw new Error("UNSAT constraints to start with");

        // Find variable holding input to negate
        Variable varToNegate = null;
        int inputToNegate = 0; // TODO Make argument or field
        {
            int i = 0;
            for (Variable v : in.constraints.getVariables()) {
                if (i++ == inputToNegate) {
                    varToNegate = v;
                    break;
                }
            }
        }

        if (varToNegate == null)
            throw new Error("Var to negate not found");

        // Find value to negate
        Object valueToNegate = null;
        for (SimpleEntry<String, Object> e : sat) {
            if (e.getKey().equals(varToNegate.getName())) {
                valueToNegate = e.getValue();
                break;
            }
        }

        if (valueToNegate == null)
            throw new Error("Value to negate not found in solution");

        // Add negated input to constraints
        Expression negatedInput = new Operation(
                Operator.NOT,
                new Operation(Operator.EQUALS, varToNegate, new BVConstant((int)valueToNegate, ((BVVariable)varToNegate).getSize()))
        );

        // Copy constraints
        Canonizer c = new Canonizer();
        c.getCanonical().putAll(in.constraints.getCanonical());
        c.getConstArrayInits().putAll(in.constraints.getConstArrayInits());
        c.getNotCanonical().addAll(in.constraints.getNotCanonical());
        c.getVariables().addAll(in.constraints.getVariables());
        c.getExpressionMap().putAll(in.constraints.getExpressionMap());
        c.getOrder().addAll(in.constraints.getOrder());

        c.getCanonical().get(varToNegate.getName()).add(negatedInput);
        c.getOrder().addLast(negatedInput);
        res.put(negatedInput.toString(), negatedInput);

        System.out.println(c.getOrder());

        // Solve again
        while (true) {
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

                System.out.println(new String(buf, StandardCharsets.UTF_8));
                Input ret = new Input();
                ret.input = buf;
                return ret;
            } else {
                // UNSAT

                // Find latest constraint in UNSAT core
                boolean found = false;
                LinkedList<Expression> newOrder = new LinkedList<>();
                LinkedList<Expression> toRemove = new LinkedList<>();
                for (Expression e : c.getOrder()) {
                    if (unsat.contains(e.toString())) {
                        System.out.println(e);
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
                res = c.getExpressionMap();
                continue;
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
        server.cov.print();
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
            System.out.println(res);

            return ret;
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private void saveInput(Input original, Input candidate) {
        if (!original.coverage.coversTheSameAs(candidate.coverage)) {
            System.out.println("Added input");
            inputs.addLast(candidate);
        } else {
            System.out.println("Discarded input");
        }
    }

    private static class Input {
        Canonizer constraints;
        Coverage coverage;
        byte[] input;
    }
}
