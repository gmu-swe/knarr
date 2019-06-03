package edu.gmu.swe.knarr.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Sort;

import za.ac.sun.cs.green.expr.BVConstant;
import za.ac.sun.cs.green.expr.BVVariable;
import za.ac.sun.cs.green.expr.BoolConstant;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.Variable;
import za.ac.sun.cs.green.expr.VisitorException;
import za.ac.sun.cs.green.service.z3.Z3JavaTranslator;

public class ConstraintFileUtil {
	
	public static void main(String[] args) throws IOException, ClassNotFoundException {

		Z3JavaTranslator.timeoutMS = 3600 * 1000; // 1h

		switch (args[0]) {
			case "solve":
				try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(args[1]))) {
					long deserialTime, solveTime, convertToExpressionTime = 0;
					
					long start = System.currentTimeMillis();
					Object o = ois.readObject();
					long end = System.currentTimeMillis();
					
					deserialTime = end - start;
					
					ArrayList<SimpleEntry<String, Object>> solution = new ArrayList<>();
					HashSet<String> unsat = new HashSet<>();

					if (o instanceof Expression) {
						start = System.currentTimeMillis();
						ConstraintServerHandler.solve((Expression)o, false, solution, new HashSet<String>());
						end = System.currentTimeMillis();
					} else if (o instanceof Canonizer) {
						Canonizer c = (Canonizer) o;
						
						start = System.currentTimeMillis();
						Expression res = ((Canonizer) o).getExpression();
						end = System.currentTimeMillis();
						
						convertToExpressionTime = end - start;

						start = System.currentTimeMillis();
//						ConstraintServerHandler.solve(res, false, solution, new HashSet<String>());
						ConstraintServerHandler.solve(c.getExpressionMap(), solution, unsat);
						end = System.currentTimeMillis();
						
					} else throw new UnsupportedOperationException();
					
					solveTime = end - start;

					System.out.println("Time to deserialize: " + deserialTime + "ms");
					System.out.println("Time to convert: " + convertToExpressionTime + "ms");
					System.out.println("Time to solve: " + solveTime + "ms");

					if (!unsat.isEmpty()) {
						System.out.println("UNSAT");

						for (String s : unsat)
							System.out.println(s);
					} else {
						byte[] buf = new byte[solution.size()];
						int i = 0;
						for (Entry<String, Object> e: solution) {
							if (!e.getKey().startsWith("autoVar_"))
								continue;
							Integer b = (Integer) e.getValue();
							if (b == null)
								break;

							buf[i++] = b.byteValue();
						}

						System.out.println("Solution as UTF_8 string:");
						System.out.println(new String(buf, StandardCharsets.UTF_8));

						if (args.length > 2) {
							try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[2])))) {
								bw.write(new String(buf, StandardCharsets.UTF_8));
							}
						};
					}

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
						
						LinkedList<Expression> exprs = new LinkedList<>();
						
						{
							Expression e = (Expression) o;
							while (true) {
								Operation op;
								if (e instanceof Operation && (op = (Operation)e).getOperator() == Operator.AND) {
									exprs.addLast(op.getOperand(1));
									e = op.getOperand(0);
								} else {
									exprs.addLast(e);
									break;
								}
							}
						}
						
						{
							int i = 0;
							for (Expression e : exprs) {
								Z3JavaTranslator t = new Z3JavaTranslator(ctx);
								try {
									e.accept(t);
								} catch (VisitorException e1) {
									throw new Error(e1);
								}

								// Print constraint number as a comment
								System.out.println("; c" + (++i));

								// Print Knarr constraint as comment
								System.out.println("; " + e.toString());

								System.out.println("(assert (!" + t.getTranslation() + " :named c" + i + "))");
								System.out.println();
							}
						}


						// Print constraints
//						System.out.println("(assert " + translator.getTranslation().toString() + " )");
//						System.out.println("\n\n");
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
			case "compare":
				// compare a b common a-rest b-rest
			{
				Canonizer a = null, b = null;
				try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(args[1]))) {
					Object o = ois.readObject();

					if (o instanceof Expression)
						throw new UnsupportedOperationException("Please dedup file " +  args[1]);
					else if (o instanceof Canonizer)
						a = (Canonizer) o;
				}

				try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(args[2]))) {
					Object o = ois.readObject();

					if (o instanceof Expression)
						throw new UnsupportedOperationException("Please dedup file " +  args[2]);
					else if (o instanceof Canonizer)
						b = (Canonizer) o;
				}

				Canonizer common = Canonizer.compare(a, b);
				

				try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(args[3]))) {
					oos.writeObject(common);
				}
				
//				try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(args[4]))) {
//					oos.writeObject(a);
//				}
//
//				try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(args[5]))) {
//					oos.writeObject(b);
//				}
				
				break;
			}
				
			case "serve-compare":
				// serve-compare address port
				
				 ServerSocket listener = new ServerSocket(9090);
				 System.out.println(ConstraintServerHandler.inZ3);
				 while (true) {
					 try {
						 ConstraintServerHandler csh = new ConstraintServerHandler(listener.accept());
						 ArrayList<SimpleEntry<String, Object>> solution = csh.getSolution();
						 
						 byte[] solBytes = new byte[solution.size()];
						 int i = 0;
						 
						 for (SimpleEntry<String, Object> e : solution) {
							 if (e.getKey().startsWith("autoVar_"))
								 solBytes[i++] = (byte) (int) e.getValue();
						 }
						 
						 try (Socket s = new Socket(args[1], Integer.parseInt(args[2]))) {
							 try (BufferedOutputStream bos = new BufferedOutputStream(s.getOutputStream())) {
								 bos.write(solBytes);
							 }
						 }

						 new ConstraintServerHandler(listener.accept());
					 }catch(SocketException e)
					 {
						 //nop
					 }
					 catch (Throwable e) {
						 System.err.println("Fatal exception in handler!!!");
						 e.printStackTrace();
						 listener.close();
					 }
				 }
			case "gen-input":
				try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(args[1]))) {
					Object o = ois.readObject();
					
					if (o instanceof Expression) {
						throw new Error("Please canonize/dedup constraints first");
					} else if (! (o instanceof Canonizer)) {
						throw new UnsupportedOperationException();
					}
					
					Canonizer c = (Canonizer) o;
					
					int inputToNegate = Integer.parseInt(args[2]);

					Map<String, Expression> res = c.getExpressionMap();
					ArrayList<SimpleEntry<String, Object>> sat = new ArrayList<>();
					HashSet<String> unsat = new HashSet<>();
					ConstraintServerHandler.solve(res, sat, unsat);
					
					if (sat.isEmpty())
						throw new Error("UNSAT constraints to start with");
					
					for (int ii = 0 ; ii < Integer.parseInt(args[3]) ; ii++) {
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

						c.getCanonical().get(varToNegate.getName()).add(negatedInput);
						c.getOrder().addLast(negatedInput);
						res.put(negatedInput.toString(), negatedInput);

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
								break;
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
				}
				break;
			default:
				System.out.println("Unknown action: " + args[0]);
				return;
		}
	}
}
