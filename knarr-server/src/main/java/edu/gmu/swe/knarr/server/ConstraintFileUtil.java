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

import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Sort;

import za.ac.sun.cs.green.expr.BoolConstant;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.Variable;
import za.ac.sun.cs.green.service.z3.Z3JavaTranslator;
import za.ac.sun.cs.green.expr.VisitorException;

public class ConstraintFileUtil {

	public static void main(String[] args) throws IOException, ClassNotFoundException {
		
		switch (args[0]) {
			case "solve":
				try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(args[1]))) {
					long deserialTime, solveTime, convertToExpressionTime = 0;
					
					long start = System.currentTimeMillis();
					Object o = ois.readObject();
					long end = System.currentTimeMillis();
					
					deserialTime = end - start;
					
					ArrayList<SimpleEntry<String, Object>> solution;

					if (o instanceof Expression) {
						start = System.currentTimeMillis();
						solution = ConstraintServerHandler.solve((Expression)o, false);
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
						
						for (HashSet<Expression> s : c.getConstArrayInits().values())
							for (Expression e : s)
								res = new Operation(Operator.AND, res, e);

						end = System.currentTimeMillis();
						
						convertToExpressionTime = end - start;

						start = System.currentTimeMillis();
						solution = ConstraintServerHandler.solve(res, false);
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
					return;
				}
			case "print":
				try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(args[1]))) {
					Object o = ois.readObject();
					
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
						
						System.out.println("Const array inits:");
						for (String v : c.getConstArrayInits().keySet()) {
							System.out.println("\t" + v);
							for (Expression e : c.getConstArrayInits().get(v))
								System.out.println("\t\t" + e);
						}
						
						System.out.println("Others:");
						for (Expression e : c.getNotCanonical())
							System.out.println("\t" + e);
					} else throw new UnsupportedOperationException();
					return;
				}
			case "dedup":
				try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(args[1]))) {
					Expression e = (Expression)ois.readObject();

					ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(args[2]));

					Canonizer c = new Canonizer();
					c.canonize(e);

					oos.writeObject(c);
					oos.close();
					return;
				}
			case "stats":
				try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(args[1]))) {
					Object o = ois.readObject();

					if (o instanceof Expression) {
						Expression e = (Expression) o;
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
						Canonizer c = (Canonizer) o;
						System.out.println("Variables: " + c.getVariables().size());
						int constraints = 0;
						for (HashSet<Expression> s : c.getCanonical().values())
							constraints += s.size();
						System.out.println("Canonical constraints: " + constraints);
						System.out.println("Constant array variables: " + c.getConstArrayInits().size());
						int inits = 0;
						for (HashSet<Expression> s : c.getConstArrayInits().values())
							inits += s.size();
						System.out.println("Constant array inits: " + inits);
						System.out.println("Other constraints: " + c.getNotCanonical().size());
						System.out.println("Total constraints: " + (constraints + inits + c.getNotCanonical().size()));
					}
					return;
				}
			case "z3":
				try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(args[1]))) {

					Object o = ois.readObject();

					Context ctx = new Context();		 

					System.out.println("(set-option :produce-unsat-cores true)");

					Z3JavaTranslator translator = new Z3JavaTranslator(ctx);

					if (o instanceof Expression) {
						try {
							((Expression)o).accept(translator);
						} catch (VisitorException ee) {
							throw new Error(ee);
						}

						// Declare variables
						HashSet<String> seen = new HashSet<>();
						for (Expr v : translator.getVariableMap().values()) {
							if (seen.add(v.toString())) {
								Sort s = v.getSort();
								System.out.println("(declare-const " + v + " " + s + ")");
							}
						}

						// Print constraints
						System.out.println("(assert " + translator.getTranslation().toString() + " )");
						System.out.println("\n\n");
					} else if (o instanceof Canonizer) {

						Canonizer c = (Canonizer) o;
						try {

							for (Variable v : c.getVariables()) {
								v.accept(translator);
								Expr vv = translator.getTranslation();
								Sort s = vv.getSort();
								System.out.println("(declare-const " + vv + " " + s + ")");
							}

							int i = 0;
							for (Entry<String, HashSet<Expression>> e : c.getConstArrayInits().entrySet()) {

								for (Expression ee : e.getValue()) {
									// Print constraint number as a comment
									System.out.println("; c" + (++i));

									// Print Knarr constraint as comment
									System.out.println("; " + e.getKey().toString());
									System.out.println("; " + ee.toString());

									ee.accept(translator);
									System.out.println("(assert (!" + translator.getTranslation() + " :named c" + i + "))");
								}

							}

							for (Entry<String, HashSet<Expression>> e : c.getCanonical().entrySet()) {

								for (Expression ee : e.getValue()) {
									// Print constraint number as a comment
									System.out.println("; c" + (++i));

									// Print Knarr constraint as comment
									System.out.println("; " + e.getKey().toString());
									System.out.println("; " + ee.toString());

									ee.accept(translator);
									System.out.println("(assert (!" + translator.getTranslation() + " :named c" + i + "))");
									System.out.println();
								}

							}

							for (Expression ee : c.getNotCanonical()) {

								// Print constraint number as a comment
								System.out.println("; c" + (++i));

								// Print Knarr constraint as comment
								System.out.println("; " + ee.toString());

								ee.accept(translator);
								System.out.println("(assert (!" + translator.getTranslation() + " :named c" + i + "))");
								System.out.println();

							}
						} catch (VisitorException ee) {
							throw new Error(ee);
						}
					}

					// Check model

					System.out.println("(check-sat)");
					System.out.println("(get-model)");

					System.out.println("; uncomment below to get unsat core");
					System.out.println(";(get-unsat-core)");

					return;
				}
			default:
				System.out.println("Unknown action: " + args[0]);
				return;
		}
	}
}
