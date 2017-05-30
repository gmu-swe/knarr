package edu.gmu.swe.knarr.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import za.ac.sun.cs.green.expr.Expression;

public class HashMapStateStore extends StateStore {

	private HashSet<String> seenSets = new HashSet<String>();
	private LinkedList<Set<Expression>> completed = new LinkedList<Set<Expression>>();
	private LinkedList<Set<Expression>> todo = new LinkedList<Set<Expression>>();

	@Override
	public boolean addCompletedExecution(Set<Expression> factoredExpressions) {
		completed.add(factoredExpressions);
		return false;
	}

	@Override
	public boolean addOption(Set<Expression> factoredExpressions) {
		ArrayList<String> l = new ArrayList<String>(factoredExpressions.size());
		for(Expression e : factoredExpressions)
			l.add(e.toString());
		Collections.sort(l);
		if(seenSets.add(l.toString()))
		{
			todo.add(factoredExpressions);
			return true;
		}
		return false;
	}

	@Override
	public Set<Expression> getNewOption() {
		Iterator<Set<Expression>> iter = todo.iterator();
		if(iter.hasNext())
		{
			Set<Expression> r = iter.next();
			completed.add(r);
			iter.remove();
			return r;
		}
		return null;
	}
}
