package edu.gmu.swe.knarr.server;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import za.ac.sun.cs.green.Green;
import za.ac.sun.cs.green.Instance;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.IntVariable;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.service.ModelService;
import za.ac.sun.cs.green.service.SATService;
import za.ac.sun.cs.green.service.choco3.ModelChoco3Service;
import za.ac.sun.cs.green.service.choco3.SATChoco3Service;
import za.ac.sun.cs.green.util.Configuration;
public class ConstraintServerHandler extends Thread {
	ObjectInputStream ois;
	Socket sock;
	ObjectOutputStream oos;
	static AtomicInteger clientID = new AtomicInteger();

	static Green green;
	static {
		green = new Green();
	}

	public static void main(String[] args) {
		Properties props = new Properties();
		props.setProperty("green.services", "sat");
		props.setProperty("green.service.sat", "(slice (canonize choco3))");
		props.setProperty("green.service.sat.slice",
				"za.ac.sun.cs.green.service.slicer.SATSlicerService");
		props.setProperty("green.service.sat.canonize",
				"za.ac.sun.cs.green.service.canonizer.SATCanonizerService");
		props.setProperty("green.service.sat.choco3",
				"za.ac.sun.cs.green.service.choco3.SATChoco3Service");
		//props.setProperty("green.store", "za.ac.sun.cs.green.store.redis.RedisStore");
		Configuration config = new Configuration(green, props);
		config.configure();

		IntVariable v = new IntVariable("myVar", 0, 1000);
		Expression e = new Operation(Operator.ADD, new IntConstant(10), v);
//		Expression e2 = new Operation(Operator.GT, new IntConstant(15), e);
		Expression e2 = new Operation(Operator.LT, new IntConstant(4), v);
		
//		e2 = new Operation(Operator.AND,e2, e3);
		Instance i = new Instance(green, null, e2);
//		i = new Instance(green, i, e3);
		System.out.println(i.getFullExpression());
		ModelService m = new ModelChoco3Service(green);
		m.processRequest(i);
		System.out.println(i.getData(m.getClass()));
	}
//	static void updatePossibleStates(ConstraintNode n, PathCondition curPC) {
//		//		System.out.println("---");
//		//		System.out.println("at: " + n.constraint);
//		//		System.out.println("curpc: " + curPC);
//		//		System.out.println("pc: " + n.pc);
//
//		if (n.pc == null) {
//			n.pc = curPC;
//			if (n.pc.count() != 0 || n.pc.spc.count() != 0) {
//				PathCondition.flagSolved = false;
////				if(n.constraint != null)
////				System.out.println("at " + n.constraint);
////				System.out.println("had no sol for pc: " + n.pc);
//				n.solution = solve(n.pc);
////				System.out.println("Sol became " + n.solution);
//				if (!n.isTerminal && n.solution != null) {
//					InputSolution sol = new InputSolution(n.solution);
//					if (!allSolutions.contains(sol)) {
//						solutionsToTry.add(sol);
//						allSolutions.add(sol);
//					}
//				}
//			}
//		}
//
//		//		System.out.println("pc: " + n.pc);
//		//		System.out.println(n.solution);
//		if (n.constraint == null)
//			return;
//
//		PathCondition tPC = curPC.make_copy();
//		if (n.constraint instanceof Constraint)
//			tPC.prependUnlessRepeated((Constraint) n.constraint);
//		if (n.constraint instanceof StringConstraint)
//			tPC.spc.addConstraint((StringConstraint) n.constraint);
//
//		PathCondition fPC = curPC.make_copy();
//		if (n.constraint instanceof Constraint)
//			fPC.prependUnlessRepeated(((Constraint) n.constraint).not());
//		if (n.constraint instanceof StringConstraint)
//			fPC.spc.addConstraint(((StringConstraint) n.constraint).not());
//
//		//		System.out.println("tpc: " + tPC);
//		//		System.out.println("---");
//
//		if (n.andWhenTrue != null)
//			updatePossibleStates(n.andWhenTrue, tPC);
//		if (n.andWhenFalse != null)
//			updatePossibleStates(n.andWhenFalse, fPC);
//	}

//	static void updatePossibleStates() {
//		updatePossibleStates(PathConditionServer.rootConstraintNode, new PathCondition());
//	}

//	static void addConstraintsToTree(PathConditionWrapper pc) {
//		HashSet<Object> unProcessedConstraints = new HashSet<Object>();
//		LinkedList<Object> orderedConstraints = new LinkedList<Object>();
//		for (Object z : pc.constraints) {
////						System.out.println("Original constraint: " + z);
//			Object o = normalize(z);
//			unProcessedConstraints.add(o);
//			orderedConstraints.add(o);
//		}
//		//		System.out.println("pre start root is " + PathConditionServer.rootConstraintNode.constraint);
//		addConstraintsToTree(unProcessedConstraints, orderedConstraints, PathConditionServer.rootConstraintNode, pc);
//	}

	static void addConstraintsToTree(HashSet<Object> unProcessedConstraints, LinkedList<Object> orderedConstraints, ConstraintNode n, Operator pc) {
		if (unProcessedConstraints.isEmpty()) {
			n.isTerminal = true;
			return;
		}
		//		System.out.println("Add to node: " + n.constraint);
		if (n.constraint == null) {
			//This branch has not been explored yet
			Object c = orderedConstraints.pop();
			while (!unProcessedConstraints.contains(c))
				c = orderedConstraints.pop();
			unProcessedConstraints.remove(c);
			n.constraint = c;
			//			System.out.println("Root is now " + PathConditionServer.rootConstraintNode.constraint);

			n.andWhenTrue = new ConstraintNode(null);
			n.andWhenFalse = new ConstraintNode(null);
			if (orderedConstraints.isEmpty()) {
				n.andWhenTrue.isTerminal = true;
			}
			addConstraintsToTree(unProcessedConstraints, orderedConstraints, n.andWhenTrue, pc);
		} else if (unProcessedConstraints.contains(n.constraint)) {
			unProcessedConstraints.remove(n.constraint);
			addConstraintsToTree(unProcessedConstraints, orderedConstraints, n.andWhenTrue, pc);
//		} else if (n.constraint instanceof Operator && unProcessedConstraints.contains(((Operator) n.constraint).not())) {
//			unProcessedConstraints.remove(((Constraint) n.constraint).not());
//			addConstraintsToTree(unProcessedConstraints, orderedConstraints, n.andWhenFalse, pc);
//		} else if (n.constraint instanceof StringConstraint && unProcessedConstraints.contains(((StringConstraint) n.constraint).not())) {
//			unProcessedConstraints.remove(((StringConstraint) n.constraint).not());
//			addConstraintsToTree(unProcessedConstraints, orderedConstraints, n.andWhenFalse, pc);
		} else
			System.err.println("Can't find constraint in PC: " + n.constraint);
	}

//	static HashMap<String, Expression> varNameToObj = new HashMap<String, Expression>();
//	static LinkedList<InputSolution> solutionsToTry;
//	static LinkedList<InputSolution> solutionsTried = new LinkedList<InputSolution>();
//	static HashSet<InputSolution> allSolutions = new HashSet<InputSolution>();

	public ConstraintServerHandler(Socket sock) {

		this.sock = sock;
		try {
			ois = new ObjectInputStream(sock.getInputStream());

			Object input = ois.readObject();

			if (input instanceof Operator) {
				
					//				System.out.println(pc);
					//				System.out.println(pc.spc);
//					pc.flagSolved = false;
//					addConstraintsToTree(pc);
////									PathConditionServer.rootConstraintNode.print();
//					updatePossibleStates();
//					PathConditionServer.rootConstraintNode.print();


//				System.out.println("Remaining input pairs: " + solutionsToTry);

//				if(PathConditionServer.STATELESS)
//				{
//					PathConditionServer.rootConstraintNode = new ConstraintNode(null);;
//					allSolutions = new HashSet<InputSolution>();
//					solutionsToTry = null;
//					solutionsTried = new LinkedList<InputSolution>();
//					varNameToObj = new HashMap<String, Expression>();
//				}
				
				//				oos.writeObject(solution);
			} else if (input.equals("REGISTER")) //wow this is a great idea
			{
				if(ConstraintServer.STATELESS)
				{
//					PathConditionServer.rootConstraintNode = new ConstraintNode(null);;
//					allSolutions = new HashSet<InputSolution>();
//					solutionsToTry = null;
//					solutionsTried = new LinkedList<InputSolution>();
//					varNameToObj = new HashMap<String, Expression>();
				}
				ObjectOutputStream oos = new ObjectOutputStream(sock.getOutputStream());
				int newId = clientID.incrementAndGet();
//				System.out.println("Remaining input pairs: " + solutionsToTry);
//				if (solutionsToTry == null) {
//					solutionsToTry = new LinkedList<InputSolution>();
//					InputSolution sol = new InputSolution();
//					sol.isUnconstrained = true;
//					oos.writeObject(sol);
//				} else if (solutionsToTry.isEmpty()) {
//					InputSolution sol = new InputSolution();
//					sol.isFullyExplored = true;
//					oos.writeObject(sol);
//				} else {
//					InputSolution soln = solutionsToTry.pop();
//					solutionsTried.add(soln);
//					soln.solverId = newId;
//					oos.writeObject(soln);
//				}
			} else if (input.equals("POLL")) {
				oos = new ObjectOutputStream(sock.getOutputStream());
//				if (solutionsToTry == null)
//					oos.writeObject("Not executed yet");
//				else
//					oos.writeObject("Remaining input combs: " + solutionsToTry.size());
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		try {
			this.sock.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		

	}

	static void buildSolution(Operation c, final HashMap<String, Serializable> solution) {

//		c.accept(new ConstraintExpressionVisitor() {
//			@Override
//			public void postVisit(SymbolicInteger expr) {
//				//				System.out.println(expr.getName() + "->" + expr.solution);
//				solution.put(expr.getName(), expr.solution);
//			}
//
//			@Override
//			public void preVisit(StringSymbolic expr) {
//				solution.put(expr.getName(), expr.solution);
//
//			}
//
//			@Override
//			public void preVisit(SymbolicReal expr) {
//				solution.put(expr.getName(), expr.solution);
//			}
//		});
//		if (c.and != null)
//			buildSolution(c.and, solution);
	}

	public static HashMap<String, Serializable> solve(Operator pc) {
		HashMap<String, Serializable> solution = new HashMap<String, Serializable>();
//		PathCondition.flagSolved = false;
//
//		if (pc.simplify()) {
//			buildSolution(pc.header, solution);
//		} else
//			return null;
//		if (pc.spc.simplify()) {
//			SymbolicStringConstraintsGeneral g = new SymbolicStringConstraintsGeneral();
//			for (StringSymbolic s : g.setOfSolution) {
//				solution.put(s.getName(), s.solution);
//			}
//		}
		return solution;
	}


}
