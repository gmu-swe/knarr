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
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import za.ac.sun.cs.green.Green;
import za.ac.sun.cs.green.Instance;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.expr.IntConstant;
import za.ac.sun.cs.green.expr.Operation;
import za.ac.sun.cs.green.expr.StringConstant;
import za.ac.sun.cs.green.expr.StringVariable;
import za.ac.sun.cs.green.expr.Variable;
import za.ac.sun.cs.green.expr.Operation.Operator;
import za.ac.sun.cs.green.service.canonizer.ModelCanonizerService;
import za.ac.sun.cs.green.service.factorizer.ModelFactorizerService;
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

	private static void generateAndAddNewOptions(Z3GreenBridge data) {
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

	private ArrayList<SimpleEntry<String, Object>> solution = null;
	private Expression req = null;
	
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
				
				if (solve)
					solution = solve(req, true);
				else
					solution = new ArrayList<>();

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
				
	public static ArrayList<SimpleEntry<String, Object>> solve(Expression req, boolean dedup) {
		
//				modeler.processRequest(in);

		Instance in;

		if (dedup) {
			Canonizer c = new Canonizer();
			c.canonize(req);
			in = new Instance(green, null, c.getExpression());
		} else {
			in = new Instance(green, null, req);
		}

		generateAndAddNewOptions(modeler.getUnderlyingExpr(in));

		Z3GreenBridge newExp = stateStore.getNewOption();
		boolean sat = false;
		ArrayList<SimpleEntry<String, Object>> ret = new ArrayList<>();
		final String prefix = "autoVar_";
		while (newExp != null && !sat) {
			sat = true;
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
					System.out.println("SAT");
//								System.out.println("SAT: " + sol);
					for(String v : sol.keySet())	
					{
//									if (v.startsWith(prefix))
							ret.add(new SimpleEntry<String, Object>(v, sol.get(v)));
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

		Collections.sort(ret, new Comparator<SimpleEntry<String, Object>>() {
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
		
		return ret;
	}

	public ArrayList<SimpleEntry<String, Object>> getSolution() {
		if (solution == null)
			solution = solve(req, true);

		return solution;
	}

}
