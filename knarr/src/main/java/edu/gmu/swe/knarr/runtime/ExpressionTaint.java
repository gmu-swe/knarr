package edu.gmu.swe.knarr.runtime;

import edu.columbia.cs.psl.phosphor.runtime.Taint;
import gov.nasa.jpf.symbc.numeric.Expression;

public class ExpressionTaint extends Taint<Expression>{

	public ExpressionTaint(Expression exp)
	{
		super(exp);
	}
	@Override
	public boolean addDependency(Taint<Expression> d) {
		throw new UnsupportedOperationException();
	}
	
}
