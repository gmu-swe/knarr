package edu.gmu.swe.knarr.server;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map.Entry;

import za.ac.sun.cs.green.expr.BoolConstant;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;

public class ConstraintFileUtil {

	public static void main(String[] args) throws IOException, ClassNotFoundException {
		
		switch (args[0]) {
			case "solve":
				long deserialTime, solveTime, convertToExpressionTime = 0;
				ObjectInputStream  ois = new ObjectInputStream(new FileInputStream(args[1]));
				
				long start = System.currentTimeMillis();
				Object o = ois.readObject();
				long end = System.currentTimeMillis();
				
				deserialTime = end - start;
				
				ArrayList<SimpleEntry<String, Object>> solution;

				if (o instanceof Expression) {
					start = System.currentTimeMillis();
					solution = ConstraintServerHandler.solve((Expression)o);
					end = System.currentTimeMillis();
				} else if (o instanceof Canonizer) {
					Canonizer c = (Canonizer) o;
					
					start = System.currentTimeMillis();
					Expression res = new Operation(Operator.NOT, new BoolConstant(false));
					
					for (HashSet<Expression> s : c.getCanonical().values())
						for (Expression e : s)
							res = new Operation(Operator.AND, res, e);
					
					for (Expression e : c.getNotCanonical())
						res = new Operation(Operator.AND, res, e);
					end = System.currentTimeMillis();
					
					convertToExpressionTime = end - start;

					start = System.currentTimeMillis();
					solution = ConstraintServerHandler.solve(res);
					end = System.currentTimeMillis();
					
				} else throw new UnsupportedOperationException();
				
				solveTime = end - start;

				byte[] buf = new byte[solution.size()];
				int i = 0;
				for (Entry<String, Object> e: solution) {
					if (!e.getKey().startsWith("autoVar_"))
						break;
					Integer b = (Integer) e.getValue();
					if (b == null)
						break;

					buf[i++] = b.byteValue();
				}

				System.out.println("Time to deserialize: " + deserialTime + "ms");
				System.out.println("Time to convert: " + convertToExpressionTime + "ms");
				System.out.println("Time to solve: " + solveTime + "ms");
				System.out.println("Solution as UTF_8 string:");
				System.out.println(new String(buf, StandardCharsets.UTF_8));
				
				try {
					// Allow the timer thread to print the time taken inside the solver
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
					// Don't care
				}
				
				break;
			case "print":
				ois = new ObjectInputStream(new FileInputStream(args[1]));
				o = ois.readObject();
				
				if (o instanceof Expression) {
					Expression e = (Expression)o;
					while (true)
					{
						Operation op = (Operation)e;
						if (op.getOperator() == Operator.AND)
						{
							System.out.println(op.getOperand(1));
							e = op.getOperand(0);
						} else {
							System.out.println(op);
							break;
						}
					}
				} else if (o instanceof Canonizer) {
					Canonizer c = (Canonizer) o;
					
					System.out.println("Canonical:");
					for (String v : c.getCanonical().keySet()) {
						System.out.println("\t" + v);
						for (Expression e : c.getCanonical().get(v))
							System.out.println("\t\t" + e);
					}
					
					System.out.println("Others:");
					for (Expression e : c.getNotCanonical())
						System.out.println("\t" + e);
				} else throw new UnsupportedOperationException();
				return;
			case "dedup":
				ois = new ObjectInputStream(new FileInputStream(args[1]));
				Expression e = (Expression)ois.readObject();

				ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(args[2]));
				
				Canonizer c = new Canonizer();
				c.canonize(e);
				
				oos.writeObject(c);
				oos.close();
				return;
			case "stats":
				ois = new ObjectInputStream(new FileInputStream(args[1]));
				o = ois.readObject();
				
				if (o instanceof Expression) {
					e = (Expression) o;
					int constraints = 0;
					while (true)
					{
						Operation op = (Operation)e;
						if (op.getOperator() == Operator.AND)
						{
							constraints++;
							e = op.getOperand(0);
						} else {
							constraints++;
							break;
						}
					}

					System.out.println("Constraints: " + constraints);
				} else if (o instanceof Canonizer) {
					c = (Canonizer) o;
					System.out.println("Canonical variables: " + c.getCanonical().size());
					int constraints = 0;
					for (HashSet<Expression> s : c.getCanonical().values())
						constraints += s.size();
					System.out.println("Canonical constraints: " + constraints);
					System.out.println("Other constraints: " + c.getNotCanonical().size());
					System.out.println("Total constraints: " + (constraints + c.getNotCanonical().size()));
				}
				return;
			default:
				System.out.println("Unknown action: " + args[0]);
				return;
		}
	}

}
