package edu.gmu.swe.knarr.runtime;

import za.ac.sun.cs.green.expr.Expression;
import edu.columbia.cs.psl.phosphor.runtime.Taint;

public class ExpressionTaint extends Taint<Expression> {

	public ExpressionTaint(Expression exp) {
		super(exp);
	}

	@Override
	public boolean addDependency(Taint<Expression> d) {
		throw new UnsupportedOperationException();
	}

}
