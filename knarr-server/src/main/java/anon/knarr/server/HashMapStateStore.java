package anon.knarr.server;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import za.ac.sun.cs.green.service.z3.Z3JavaTranslator.Z3GreenBridge;

public class HashMapStateStore extends StateStore {

	private HashSet<String> seenSets = new HashSet<String>();
	private LinkedList<Z3GreenBridge> completed = new LinkedList<Z3GreenBridge>();
	private LinkedList<Z3GreenBridge> toSolve = new LinkedList<Z3GreenBridge>();

	private LinkedList<Z3GreenBridge> toTry = new LinkedList<Z3GreenBridge>();

	private Thread[] workers;

	public HashMapStateStore() {
//		int nThreads = 8;//Whatever
//		workers = new Thread[nThreads];
//		for(int i= 0; i < nThreads; i++)
//		{
//			workers[i] = new Thread(new Runnable() {
//
//				@Override
//				public void run() {
//					while(true)
//					{
//						Z3GreenBridge s = null;
//						while(toSolve.isEmpty())
//						{
//							synchronized (toSolve) {
//								try {
//									toSolve.wait();
//								} catch (InterruptedException e) {
//								}
//								s = toSolve.getFirst();
//							}
//						}
//						if(s != null)
//						{
//							//TODO solve the constraint then notify the main thread
//						}
//					}
//
//				}
//			});
//			workers[i].setDaemon(true);
//			workers[i].start();
//		}
	}

	@Override
	public boolean addCompletedExecution(Z3GreenBridge factoredExpressions) {
		completed.add(factoredExpressions);
		return false;
	}
	public static int optionsAdded;
	public static int optionsIgnored;
	public static int optionsFoundUnsat;

	HashSet<String> unsats = new HashSet<String>();
	@Override
	public void addUnsat(Z3GreenBridge newExp) {
		unsats.add(newExp.constraints.toString());
	}
	@Override
	public boolean addOption(Z3GreenBridge factoredExpressions) {
//		if(seenSets.add(factoredExpressions.constraints.toString()))
//		{
//			optionsAdded++;
			toSolve.add(factoredExpressions);
//			synchronized (toSolve) {
//				try {
//					toSolve.wait();
//				} catch (InterruptedException e) {
//				}
//			}
			return true;
//		}
//		optionsIgnored++;
//		return false;
	}

	@Override
	public Z3GreenBridge getNewOption() {
//		Iterator<Z3GreenBridge> iter = toTry.iterator();
//		while(!iter.hasNext() && toSolve.size() > 0)
//		{
//			try {
//				synchronized (toTry) {
//					toTry.wait();
//				}
//			} catch (InterruptedException e) {
//			}
//		}
		Iterator<Z3GreenBridge> iter = toSolve.iterator();
		while(iter.hasNext())
		{
			Z3GreenBridge r = iter.next();
//			completed.add(r);
			iter.remove();

			return r;

//			String t = r.constraints.toString();
//			boolean unsat = false;
//			//Check for unsat
//			for(String s : unsats)
//			{
//				if(t.contains(s))
//				{
//					unsat = true;
//					break;
//				}
//			}
//			if(unsat)
//			{
//				optionsFoundUnsat++;
//			}
//			else
//			{
//				return r;
//			}
		}
		return null;
	}
}
