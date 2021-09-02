package anon.knarr.server;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.LinkedList;

import za.ac.sun.cs.green.expr.Operation;

public class ConstraintNode {
	public Object UNSOLVABLE = new Object();
	public boolean isTerminal = false;
	public Object constraint;
	public ConstraintNode andWhenTrue;
	public ConstraintNode andWhenFalse;
	public HashMap<String, Serializable> solution;
	public LinkedList<Operation> pc;

	public ConstraintNode(Operation c, ConstraintNode truec, ConstraintNode falsec) {
		this.constraint = c;
		this.andWhenTrue = truec;
		this.andWhenFalse = falsec;
	}

	public ConstraintNode() {

	}

	public ConstraintNode(Operation c) {
		this.constraint = c;
		this.andWhenFalse = new ConstraintNode();
		this.andWhenTrue = new ConstraintNode();
	}

	public void print() {
		System.out.println("Constraint Tree");
		print("", true, true, true);
	}

	private String constraintToString(Object o) {
		if (o instanceof Operation) {
			return o.toString();
		} else
			throw new IllegalArgumentException("Not a constraint!" + o);
	}

	private String prettySolution()
	{
		if(solution == null)
			return "UNSOLVABLE";

		String ret = "{";
		for(String s : solution.keySet())
		{
			ret = ret + s+"=["+solution.get(s)+"] ";
		}
		ret += "}";
		return ret;
	}

	private void print(String prefix, boolean isTail, boolean trueBranch, boolean isFirst) {
		try {
			if (constraint != null)
				System.out.println(prefix + (isTail ? new String("└── ".getBytes("UTF-8")) : new String("├── ".getBytes("UTF-8"))) + (isFirst? "" : (trueBranch ? "T " : "F ")) + (isTerminal ? " X " : "")
						+  (constraint == null ?  null : constraintToString(constraint)) + (isFirst ? "" :" " + prettySolution()));
			else if (pc != null)
				System.out.println(prefix + (isTail ? new String("└── ".getBytes("UTF-8")) : new String("├── ".getBytes("UTF-8"))) + (isFirst ? "" : (trueBranch ? "T " : "F ")) + (isTerminal ? " X " : "") + prettySolution());
			if (andWhenTrue.constraint != null || andWhenTrue.pc != null)
				andWhenTrue.print(prefix + (isTail ? "    " : new String("│  ".getBytes("UTF-8"))), (andWhenFalse.constraint == null ? true : false), true,false);
			if (andWhenFalse.constraint != null || andWhenFalse.pc != null)
				andWhenFalse.print(prefix + (isTail ? "    " : new String("│  ".getBytes("UTF-8"))), true, false, false);
			//
			//		if (children.size() >= 1) {
			//			children.get(children.size() - 1).print(prefix + (isTail ? "    " : "│   "), true);
			//		}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
