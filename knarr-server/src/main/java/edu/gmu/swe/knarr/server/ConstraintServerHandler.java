package edu.gmu.swe.knarr.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;

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
import za.ac.sun.cs.green.expr.Visitor;
import za.ac.sun.cs.green.expr.VisitorException;
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
import za.ac.sun.cs.green.service.z3.Z3JavaTranslator.Z3GreenBridge;
import za.ac.sun.cs.green.util.Configuration;
import za.ac.sun.cs.green.util.NotSatException;

public class ConstraintServerHandler extends Thread {
	ObjectInputStream ois;
	Socket sock;
	ObjectOutputStream oos;
	static AtomicInteger clientID = new AtomicInteger();

	static final Green green;
	static final ModelFactorizerService slicer;
	static final ModelCanonizerService canonizer;
	static final ModelZ3JavaService modeler;
	static final Map<Variable, Variable> variableMap;
	static final StateStore stateStore;
	static final ConstraintOptionGenerator optionGenerator;
	static {
		green = new Green();
		Properties props = new Properties();
		props.setProperty("green.services", "model");
		props.setProperty("green.service.model", "(slice (canonize z3))");
		props.setProperty("green.service.model.slice", "za.ac.sun.cs.green.service.slicer.SATSlicerService");
		props.setProperty("green.service.model.canonize", "za.ac.sun.cs.green.service.canonizer.ModelCanonizerService");
		props.setProperty("green.service.model.z3", "za.ac.sun.cs.green.service.z3.ModelZ3JavaService");
		// props.setProperty("green.store",
		// "za.ac.sun.cs.green.store.redis.RedisStore");
		Configuration config = new Configuration(green, props);
		config.configure();
		slicer = new ModelFactorizerService(green);
		canonizer = new ModelCanonizerService(green);
		variableMap = new HashMap<Variable, Variable>();
		modeler = new ModelZ3JavaService(green, null);
		stateStore = new HashMapStateStore();
		optionGenerator = new ConstraintOptionGenerator();
	}

	public static void main(String[] args) throws Throwable {

		StringVariable sv = new StringVariable("myStr");
		Expression e3 = new Operation(Operator.EQUALS, new StringConstant("foo"), new Operation(Operator.SUBSTRING, sv, new IntConstant(4), new IntConstant(3)));
		Expression e2 = new Operation(Operator.GT, new IntConstant(100), new Operation(Operator.CHARAT,sv,new IntConstant(0)));
		e2 = new Operation(Operator.AND, e2, e3);
		e2 = new Operation(Operator.AND, e2, new Operation(Operator.GT, new IntConstant(105), new Operation(Operator.CHARAT,sv,new IntConstant(0))));

		// e3 = new Operation(Operator.STARTSWITH, new StringConstant("foo"),
		// sv);
		// e2 = new Operation(Operator.AND, e2,e3);
		// e3 = new Operation(Operator.ENDSWITH, new StringConstant("bar"), sv);
		// e2 = new Operation(Operator.AND, e2,e3);
		//
		// e3 = new Operation(Operator.EQ, new IntConstant(20), new
		// Operation(Operator.LENGTH, sv));
		// e2 = new Operation(Operator.AND, e2, e3);
		//
		// ArrayVariable av = new ArrayVariable("myAr");
		// e3 = new Operation(Operator.EQ, v, new Operation(Operator.SELECT, av,
		// new IntConstant(0)));
		// e2 = new Operation(Operator.AND, e2, e3);

		Instance i = new Instance(green, null, e2);
		System.out.println(e2);
		
		ModelFactorizerService slicer = new ModelFactorizerService(green);
		Set<Instance> r = slicer.processRequest(i);
		ModelCanonizerService cs = new ModelCanonizerService(green);
		final Map<Variable, Variable> variableMap = new HashMap<Variable, Variable>();
		ModelZ3JavaService m = new ModelZ3JavaService(green, null);
		System.out.println(m.getUnderlyingExpr(i));
		ConstraintOptionGenerator g = new ConstraintOptionGenerator();
		for(Z3GreenBridge o : g.generateOptions(m.getUnderlyingExpr(i)))
		{
			System.out.println(m.solve(o));
		}
//		for (Instance j : r) {
//			System.out.println("Fact: " + j.getFullExpression());
//			Expression exp = j.getFullExpression();
//
//			Expression c = cs.canonize(j.getFullExpression(), variableMap);
//			if (c != null)
//				exp = c;
//			System.out.println(c);
//			Instance k = new Instance(green, j, null, exp);
//			System.out.println("Modeling: " + k.getFullExpression());
//			m.processRequest(k);
//			System.out.println(k.getData(m.getClass()));
//			HashMap<Variable, Object> sol = (HashMap<Variable, Object>) k.getData(m.getClass());
//			for(Variable va : sol.keySet())
//			{
//				System.out.println(va.getOriginal());
//			}
//
//		}
		//
		// // i = new Instance(green, i, e3);
		// System.out.println("Orig:" + i.getFullExpression());
		// ModelService m = new ModelChoco3Service(green);
		// m.processRequest(i);
		// System.out.println(i.getData(m.getClass()));

		// System.out.println(i.request("model"));
	}

	private void generateAndAddNewOptions(Z3GreenBridge data) {
		int nAdded = 0;
		for (Z3GreenBridge e : optionGenerator.generateOptions(data)) {
			// Slice, then canonize, then try to add to list
//			Instance i = new Instance(green, null, e);
//			Set<Instance> sliced = slicer.processRequest(i);
//			Set<Expression> exps = new HashSet<Expression>();
//			for (Instance j : sliced) {
//				Expression fact = j.getFullExpression();
//				Expression c = canonizer.canonize(j.getFullExpression(), variableMap);
//				if (c != null)
//					fact = c;
//				exps.add(fact);
//			}
			if (stateStore.addOption(e))
				nAdded++;
		}
	}

	static int nSolved;
	static int nSat;
	static long inZ3;
	static{
		Timer timer = new Timer(true);
		timer.schedule(new TimerTask() {
			
			@Override
			public void run() {
				System.out.println("Solved: " + nSolved + ", sat: "+nSat +", Solver time: " + inZ3);
				System.out.println("Options added: " + HashMapStateStore.optionsAdded +", ignored: " + HashMapStateStore.optionsIgnored +", trivilaly not sat: " + HashMapStateStore.optionsFoundUnsat);
			}
		}, 1000,1000);
		
	}

	private static Operation operationFromSet(Set<Operation> constraints)
	{
		Operation ret = null;
		
		for (Operation op: constraints)
		{
			if (ret == null)
				ret = op;
			else
				ret = new Operation(Operator.AND, ret, op);
		}
		
		return ret;
	}

	// Sort common expressions by variable name
	private static TreeSet<Operation> sort(Set<Operation> constraints)
	{
		TreeSet<Operation> ret = new TreeSet<>(new Comparator<Operation>() {

			private Variable getVarFromOperation(Operation op) {
				{
					Expression e1 = op.getOperand(0);
					if (e1 instanceof Variable)
					{
						// var OP num
						return (Variable) e1;
					}
					else if (e1 instanceof Operation)
					{
						Expression e2 = ((Operation)e1).getOperand(0);
						if (e2 instanceof Variable)
							// (var OP num) OP num
							return (Variable) e2;
					}
				}
				{
					Expression e1 = op.getOperand(1);
					if (e1 instanceof Variable)
					{
						// num OP var
						return (Variable) e1;
					}
					else if (e1 instanceof Operation)
					{
						Expression e2 = ((Operation)e1).getOperand(0);
						if (e2 instanceof Variable)
							// num OP (var OP num)
							return (Variable) e2;
					}
				}
				
				return null;
			}

			@Override
			public int compare(Operation o1, Operation o2) {
				Variable v1 = getVarFromOperation(o1);
				Variable v2 = getVarFromOperation(o2);
				
				if (v1 != null && v2 != null) {
					int ret = v1.getName().compareTo(v2.getName());
					if (ret != 0)
						return ret;
					else return o1.toString().compareTo(o2.toString());
				}
				else if (v1 != null)
					return -1;
				else if (v2 != null)
					return 1;
				else
					return o1.toString().compareTo(o2.toString());
			}
		});
		
		ret.addAll(constraints);
		
		return ret;
	}
	
	// Remove duplicated expressions
	private static Set<Operation> dedup(Expression constraints)
	{
		HashSet<Operation> ret = new HashSet<>();

		// Expression has the form: ((((exp) AND exp) ...) AND  exp)
		// Extract each individual expression
		Expression e = constraints;
		while (true)
		{
			Operation op = (Operation)e;
			if (op.getOperator() == Operator.AND)
			{
				ret.add((Operation)op.getOperand(1));
				e = op.getOperand(0);
			} else {
				ret.add(op);
				break;
			}
		}
		
		return ret;
	}
	
	public ConstraintServerHandler(Socket sock) {

		this.sock = sock;
		try {
			ois = new ObjectInputStream(sock.getInputStream());
			oos = new ObjectOutputStream(sock.getOutputStream());
			Object input = ois.readObject();

			if (input instanceof Expression) {
				
				Set<Operation> dedup = dedup((Expression)input);
				TreeSet<Operation> processed = sort(dedup);
				for (Operation op : processed)
					System.out.println(op);
				
				Operation processedInput = operationFromSet(processed);

//				System.out.println("Received expression: " + input);
				Instance in = new Instance(green, null, processedInput);
				
//				modeler.processRequest(in);
				generateAndAddNewOptions(modeler.getUnderlyingExpr(in));

				Z3GreenBridge newExp = stateStore.getNewOption();
				boolean sat = false;
				HashMap<String,Object> ret = null;
				while (newExp != null && !sat) {
					sat = true;
					ret = new HashMap<String, Object>();
//					System.out.println("Trying out new version: " + newExp);
					try{
//						modeler.processRequest(k);
						@SuppressWarnings("unchecked")
						long start = System.currentTimeMillis();

						HashMap<String, Object> sol = modeler.solve(newExp);
						inZ3 += (System.currentTimeMillis()-start);
						nSolved++;

						if (sol != null) {
							nSat++;
							System.out.println("SAT: " + sol);
							for(String v : sol.keySet())	
							{
								ret.put(v, sol.get(v));
							}
						} else {
							
							stateStore.addUnsat(newExp);
							System.out.println("NOT SAT");
							sat = false;
						}
					}
					catch(NotSatException ex)
					{
						sat = false;
						System.out.println("Not sat");
					}
					newExp = stateStore.getNewOption();
				}
				oos.writeObject(ret);
				oos.close();

			} else if (input.equals("REGISTER")) // wow this is a great idea
			{
				if (ConstraintServer.STATELESS) {
				}
				ObjectOutputStream oos = new ObjectOutputStream(sock.getOutputStream());
				int newId = clientID.incrementAndGet();
			} else if (input.equals("POLL")) {
				oos = new ObjectOutputStream(sock.getOutputStream());
				// if (solutionsToTry == null)
				// oos.writeObject("Not executed yet");
				// else
				// oos.writeObject("Remaining input combs: " +
				// solutionsToTry.size());
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

}
