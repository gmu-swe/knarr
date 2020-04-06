package edu.gmu.swe.knarr.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import edu.gmu.swe.knarr.runtime.Coverage;
import za.ac.sun.cs.green.Green;
import za.ac.sun.cs.green.Instance;
import za.ac.sun.cs.green.expr.BinaryOperation;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.NaryOperation;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.expr.StringConstant;
import za.ac.sun.cs.green.expr.StringVariable;
import za.ac.sun.cs.green.expr.Variable;
import za.ac.sun.cs.green.service.canonizer.ModelCanonizerService;
import za.ac.sun.cs.green.service.factorizer.ModelFactorizerService;
import za.ac.sun.cs.green.service.z3.ModelZ3JavaService;
import za.ac.sun.cs.green.service.z3.ModelZ3JavaService.Solution;
import za.ac.sun.cs.green.service.z3.Z3JavaTranslator.Z3GreenBridge;
import za.ac.sun.cs.green.util.Configuration;
import za.ac.sun.cs.green.util.NotSatException;

public class ConstraintServerHandler extends Thread {
	ObjectInputStream ois;
	Socket sock;
	ObjectOutputStream oos;
	static AtomicInteger clientID = new AtomicInteger();

	private static class Data {
		Green green;
		ModelFactorizerService slicer;
		ModelCanonizerService canonizer;
		ModelZ3JavaService modeler;
		Map<Variable, Variable> variableMap;
		StateStore stateStore;
		ConstraintOptionGenerator optionGenerator;
	}

	private static final ThreadLocal<Data> data = new ThreadLocal<Data>() {
		@Override
		protected Data initialValue() {
		    Data ret = new Data();
			ret.green = new Green();
			Properties props = new Properties();
			props.setProperty("green.services", "model");
			props.setProperty("green.service.model", "(slice (canonize z3))");
			props.setProperty("green.service.model.slice", "za.ac.sun.cs.green.service.slicer.SATSlicerService");
			props.setProperty("green.service.model.canonize", "za.ac.sun.cs.green.service.canonizer.ModelCanonizerService");
			props.setProperty("green.service.model.z3", "za.ac.sun.cs.green.service.z3.ModelZ3JavaService");
			// props.setProperty("green.store",
			// "za.ac.sun.cs.green.store.redis.RedisStore");
			Configuration config = new Configuration(ret.green, props);
			config.configure();
			ret.slicer = new ModelFactorizerService(ret.green);
			ret.canonizer = new ModelCanonizerService(ret.green);
			ret.variableMap = new HashMap<Variable, Variable>();
			ret.modeler = new ModelZ3JavaService(ret.green, null);
			ret.stateStore = new HashMapStateStore();
			ret.optionGenerator = new ConstraintOptionGenerator();

			return ret;
		}
	};

//	static {
//		green = new Green();
//		Properties props = new Properties();
//		props.setProperty("green.services", "model");
//		props.setProperty("green.service.model", "(slice (canonize z3))");
//		props.setProperty("green.service.model.slice", "za.ac.sun.cs.green.service.slicer.SATSlicerService");
//		props.setProperty("green.service.model.canonize", "za.ac.sun.cs.green.service.canonizer.ModelCanonizerService");
//		props.setProperty("green.service.model.z3", "za.ac.sun.cs.green.service.z3.ModelZ3JavaService");
//		// props.setProperty("green.store",
//		// "za.ac.sun.cs.green.store.redis.RedisStore");
//		Configuration config = new Configuration(green, props);
//		config.configure();
//		slicer = new ModelFactorizerService(green);
//		canonizer = new ModelCanonizerService(green);
//		variableMap = new HashMap<Variable, Variable>();
//		modeler = new ModelZ3JavaService(green, null);
//		stateStore = new HashMapStateStore();
//		optionGenerator = new ConstraintOptionGenerator();
//	}

	public static void main(String[] args) throws Throwable {

		StringVariable sv = new StringVariable("myStr");
		Expression e3 = new NaryOperation(Operator.EQUALS, new StringConstant("foo"), new NaryOperation(Operator.SUBSTRING, sv, Operation.FOUR, Operation.THREE));
		Expression e2 = new NaryOperation(Operator.GT, new IntConstant(100), new BinaryOperation(Operator.CHARAT,sv,Operation.ZERO));
		e2 = new BinaryOperation(Operator.AND, e2, e3);
		e2 = new BinaryOperation(Operator.AND, e2, new BinaryOperation(Operator.GT, new IntConstant(105), new BinaryOperation(Operator.CHARAT,sv,Operation.ZERO)));

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

		Instance i = new Instance(data.get().green, null, e2);
		System.out.println(e2);
		
		ModelFactorizerService slicer = new ModelFactorizerService(data.get().green);
		Set<Instance> r = slicer.processRequest(i);
		ModelCanonizerService cs = new ModelCanonizerService(data.get().green);
		final Map<Variable, Variable> variableMap = new HashMap<Variable, Variable>();
		ModelZ3JavaService m = new ModelZ3JavaService(data.get().green, null);
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

	private static void generateAndAddNewOptions(Z3GreenBridge d) {
		int nAdded = 0;
		for (Z3GreenBridge e : data.get().optionGenerator.generateOptions(d)) {
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
			if (data.get().stateStore.addOption(e))
				nAdded++;
		}
	}

	static int nSolved;
	static int nSat;
	static long inZ3;
//	static{
//		Timer timer = new Timer(true);
//		timer.schedule(new TimerTask() {
//
//			@Override
//			public void run() {
//				System.out.println("Solved: " + nSolved + ", sat: "+nSat +", Solver time: " + inZ3);
//				System.out.println("Options added: " + HashMapStateStore.optionsAdded +", ignored: " + HashMapStateStore.optionsIgnored +", trivilaly not sat: " + HashMapStateStore.optionsFoundUnsat);
//			}
//		}, 1000,1000);
//
//	}

	private ArrayList<SimpleEntry<String, Object>> solution = null;
	public Expression req = null;
	public Coverage cov = null;
	
	public ConstraintServerHandler(Socket sock) {

		this.sock = sock;
		try {
			ois = new ObjectInputStream(sock.getInputStream());
			oos = new ObjectOutputStream(sock.getOutputStream());
			Object input = ois.readObject();
			
			boolean solve = false;
			File save = null;

			if (input instanceof Expression) {
				req   = (Expression) input;
				solve = ois.readBoolean();
				save  = (File) ois.readObject();
				cov = (Coverage) ois.readObject();
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
				
			if (req != null) {
//				System.out.println("Received expression: " + req.getConstraints());
				
				if (save != null) {
					System.out.println("Saving to file: " + save);
					ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(save));
					oos.writeObject(req);
					oos.close();
				}
				
				if (solve) {
					solution = new ArrayList<>();
					solve(req, true, solution, new HashSet<String>());
				} else {
					solution = new ArrayList<>();
				}

				oos.writeObject(solution);
			}

		} catch (Throwable e) {
			e.printStackTrace();
		}

		try {
			this.sock.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
			
	}
				
	public static void solve(Expression req, boolean dedup, ArrayList<SimpleEntry<String, Object>> sat, Set<String> unsat) {
		
//				modeler.processRequest(in);

		Instance in;

		if (dedup) {
			Canonizer c = new Canonizer();
			c.canonize(req);
			in = new Instance(data.get().green, null, c.getExpression());
		} else {
			in = new Instance(data.get().green, null, req);
		}
		
		solve(in, sat, unsat);
	}
				
	public static void solve(Map<String, Expression> expressions,  ArrayList<SimpleEntry<String, Object>> sat, Set<String> unsat) {
		
//				modeler.processRequest(in);

		Instance in = new Instance(data.get().green, null, expressions);
		solve(in, sat, unsat);
	}

	private static void solve(Instance in,  ArrayList<SimpleEntry<String, Object>> sat, Set<String> unsat) {
		generateAndAddNewOptions(data.get().modeler.getUnderlyingExpr(in));

		Z3GreenBridge newExp = data.get().stateStore.getNewOption();
		boolean issat = false;
		final String prefix = "autoVar_";
		while (newExp != null && !issat) {
			issat = true;
//					System.out.println("Trying out new version: " + newExp);
			try{
//						modeler.processRequest(k);
				@SuppressWarnings("unchecked")
				long start = System.currentTimeMillis();
				
				
				Solution sol = data.get().modeler.solve(newExp);
				inZ3 += (System.currentTimeMillis()-start);
				nSolved++;

				if (sol.sat) {
					nSat++;
//					System.out.println("SAT");
//								System.out.println("SAT: " + sol);
					for(String v : sol.data.keySet())	
					{
//									if (v.startsWith(prefix))
							sat.add(new SimpleEntry<String, Object>(v, sol.data.get(v)));
					}
				} else {
					data.get().stateStore.addUnsat(newExp);
//					System.out.println("NOT SAT");
					for (String k : sol.data.keySet()) {
						unsat.add(k);
					}
					issat = false;
				}
			}
			catch(NotSatException ex)
			{
				issat = false;
				System.out.println("Not sat");
			}
			newExp = data.get().stateStore.getNewOption();
		}

		Collections.sort(sat, new Comparator<SimpleEntry<String, Object>>() {
				@Override
				public int compare(SimpleEntry<String, Object> o1, SimpleEntry<String, Object> o2) {
					if (o1.getKey().startsWith(prefix) && o2.getKey().startsWith(prefix)) {
						Integer i1 = Integer.valueOf(o1.getKey().substring(prefix.length()));
						Integer i2 = Integer.valueOf(o2.getKey().substring(prefix.length()));
						return i1.compareTo(i2);
					}
					
					return o1.getKey().compareTo(o2.getKey());
				}
			});
	}

	public ArrayList<SimpleEntry<String, Object>> getSolution() {
		if (solution == null)
			solve(req, true, solution, new HashSet<String>());

		return solution;
	}

}
