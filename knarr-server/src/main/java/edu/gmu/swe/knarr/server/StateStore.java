package edu.gmu.swe.knarr.server;

import java.util.Set;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;

import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.service.z3.Z3JavaTranslator.Z3GreenBridge;

public abstract class StateStore {
	public abstract boolean addOption(Z3GreenBridge factoredExpressions);
	public abstract boolean addCompletedExecution(Z3GreenBridge factoredExpressions);
	public abstract Z3GreenBridge getNewOption();
	public abstract void addUnsat(Z3GreenBridge newExp);
	
}
