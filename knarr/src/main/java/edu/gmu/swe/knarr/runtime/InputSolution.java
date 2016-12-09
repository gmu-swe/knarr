package edu.gmu.swe.knarr.runtime;

import java.io.Serializable;
import java.util.HashMap;

public class InputSolution implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4652916336670934186L;
	public int solverId;
	public HashMap<String, Serializable> varMapping = new HashMap<String, Serializable>();
	public String result;
	public boolean isUnconstrained;
	public boolean isFullyExplored;

	public InputSolution(HashMap<String, Serializable> vars) {
		this.varMapping = vars;
	}

	public InputSolution() {
	}

	@Override
	public String toString() {
		if(varMapping != null)
		return varMapping.toString();
		return "NULL";
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof InputSolution))
			return false;
		if (((InputSolution) obj).varMapping == null && varMapping != null)
			return false;
		if (((InputSolution) obj).varMapping == null && varMapping == null)
			return true;
		return ((InputSolution) obj).varMapping.equals(varMapping);
	}

	@Override
	public int hashCode() {
		if (varMapping == null)
			return 0;
		return varMapping.hashCode();
	}
}
