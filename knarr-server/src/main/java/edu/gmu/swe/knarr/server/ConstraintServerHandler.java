package edu.gmu.swe.knarr.server;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import za.ac.sun.cs.green.Green;
import za.ac.sun.cs.green.Instance;
import za.ac.sun.cs.green.expr.ArrayVariable;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.IntVariable;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.RealConstant;
import za.ac.sun.cs.green.expr.RealVariable;
import za.ac.sun.cs.green.expr.StringConstant;
import za.ac.sun.cs.green.expr.StringVariable;
import za.ac.sun.cs.green.expr.Variable;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.parser.klee.ParseException;
import za.ac.sun.cs.green.parser.klee.Parser;
import za.ac.sun.cs.green.parser.klee.Scanner;
import za.ac.sun.cs.green.service.ModelService;
import za.ac.sun.cs.green.service.SATService;
import za.ac.sun.cs.green.service.canonizer.ModelCanonizerService;
import za.ac.sun.cs.green.service.choco3.ModelChoco3Service;
import za.ac.sun.cs.green.service.choco3.SATChoco3Service;
import za.ac.sun.cs.green.service.factorizer.ModelFactorizerService;
import za.ac.sun.cs.green.service.factorizer.SATFactorizerService;
import za.ac.sun.cs.green.service.slicer.SATSlicerService;
import za.ac.sun.cs.green.service.z3.ModelZ3JavaService;
import za.ac.sun.cs.green.util.Configuration;
public class ConstraintServerHandler extends Thread {
	ObjectInputStream ois;
	Socket sock;
	ObjectOutputStream oos;
	static AtomicInteger clientID = new AtomicInteger();

	static final Green green;
	static final ModelFactorizerService slicer;
	static final ModelCanonizerService canonizer;
	static final ModelService modeler;
	static final Map<Variable, Variable> variableMap;
	static {
		green = new Green();
		Properties props = new Properties();
		props.setProperty("green.services", "model");
		props.setProperty("green.service.model", "(slice (canonize z3))");
		props.setProperty("green.service.model.slice",
				"za.ac.sun.cs.green.service.slicer.SATSlicerService");
		props.setProperty("green.service.model.canonize",
				"za.ac.sun.cs.green.service.canonizer.ModelCanonizerService");
		props.setProperty("green.service.model.z3",
				"za.ac.sun.cs.green.service.z3.ModelZ3JavaService");
		//props.setProperty("green.store", "za.ac.sun.cs.green.store.redis.RedisStore");
		Configuration config = new Configuration(green, props);
		config.configure();
		slicer = new ModelFactorizerService(green);
		canonizer = new ModelCanonizerService(green);
		variableMap = new HashMap<Variable, Variable>();
		modeler = new ModelZ3JavaService(green, null);
		
	}

	public static void main(String[] args) throws Throwable {
		

		RealVariable v = new RealVariable("myVar", 0d, 1000d);
//		Expression e = new Operation(Operator.ADD, new RealConstant(10), v);
//		Expression e2 = new Operation(Operator.MUL, new RealConstant(15), e);
//		e2 = new Operation(Operator.GT, new RealConstant(20), e2);
//		IntVariable v2 = new IntVariable("myVar2", 0, 1000);
//		Cp
//		System.out.println(e2);
//		e2 = new Operation(Operator.AND, e2, e3);
//
//		e3 = new Operation(Operator.GT, new IntConstant(20), v2);
//		e2 = new Operation(Operator.AND, e2, e3);
//
//		
		StringVariable sv = new StringVariable("myStr");
		Expression e2 = new Operation(Operator.EQUALS,new StringConstant("foo"),new Operation(Operator.SUBSTRING, sv, new IntConstant(4), new IntConstant(3)));
//		e3 = new Operation(Operator.STARTSWITH, new StringConstant("foo"), sv);
//		e2 = new Operation(Operator.AND, e2,e3);
//		e3 = new Operation(Operator.ENDSWITH, new StringConstant("bar"), sv);
//		e2 = new Operation(Operator.AND, e2,e3);
//
//		e3 = new Operation(Operator.EQ, new IntConstant(20), new Operation(Operator.LENGTH, sv));
//		e2 = new Operation(Operator.AND, e2, e3);
//		
//		ArrayVariable av = new ArrayVariable("myAr");
//		e3 = new Operation(Operator.EQ, v, new Operation(Operator.SELECT, av, new IntConstant(0)));
//		e2 = new Operation(Operator.AND, e2, e3);

		
		Instance i = new Instance(green, null, e2);
		System.out.println(e2);
		ModelFactorizerService slicer = new ModelFactorizerService(green);
		Set<Instance> r = slicer.processRequest(i);
		ModelCanonizerService cs = new ModelCanonizerService(green);
		final Map<Variable, Variable> variableMap = new HashMap<Variable, Variable>();
		ModelService m = new ModelZ3JavaService(green, null);
		for(Instance j : r)
		{
			System.out.println("Fact: " + j.getFullExpression());
			Expression exp = j.getFullExpression();
			
			Expression c = cs.canonize(j.getFullExpression(), variableMap);
			if(c != null)
				exp = c;
			Instance k = new Instance(green, j, null, exp);
			System.out.println("Modeling: " + k.getFullExpression());
			m.processRequest(k);
			System.out.println(k.getData(m.getClass()));

		}
//		
//		//		i = new Instance(green, i, e3);
//		System.out.println("Orig:" + i.getFullExpression());
//		ModelService m = new ModelChoco3Service(green);
//		m.processRequest(i);
//		System.out.println(i.getData(m.getClass()));
		
//		System.out.println(i.request("model"));
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
			oos = new ObjectOutputStream(sock.getOutputStream());
			Object input = ois.readObject();

			if (input instanceof Expression) {
				System.out.println("Received expression: " + input);
				Instance i = new Instance(green, null, ((Expression) input));
				Set<Instance> sliced = slicer.processRequest(i);
				HashMap ret = new HashMap();
				for(Instance j : sliced)
				{
					Expression exp = j.getFullExpression();
					System.out.println("Factor: " + exp);
					
					Expression c = canonizer.canonize(j.getFullExpression(), variableMap);
					if(c != null)
						exp = c;
					Instance k = new Instance(green, j, null, exp);
					System.out.println("Modeling: " + k.getFullExpression());
					modeler.processRequest(k);
					HashMap sol = (HashMap)k.getData(modeler.getClass());
					if(sol != null)
						ret.putAll(sol);
				}
				oos.writeObject(ret);
				oos.close();
				
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
