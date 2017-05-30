package edu.gmu.swe.knarr.server;

import java.util.Set;

import za.ac.sun.cs.green.expr.Expression;

public abstract class StateStore {
	public abstract boolean addOption(Set<Expression> factoredExpressions);
	public abstract boolean addCompletedExecution(Set<Expression> factoredExpressions);
	public abstract Set<Expression> getNewOption();
	
}
