package edu.gmu.swe.knarr.runtime;

import java.io.Serializable;
import java.util.LinkedList;

import gov.nasa.jpf.symbc.numeric.Constraint;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.symbc.string.StringConstraint;
import gov.nasa.jpf.symbc.string.StringPathCondition;

public class PathConditionWrapper extends PathCondition implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8116156330739369246L;
	public LinkedList<Serializable> constraints = new LinkedList<Serializable>();

	public PathConditionWrapper() {
		spc = new StringPathConditionWrapper(this);
	}

	@Override
	public boolean prependUnlessRepeated(Constraint t) {
//		if (super.prependUnlessRepeated(t)) {
		//TODO remove duplicates???
			constraints.add(t);
			return true;
//		}
//		return false;
	}

	public class StringPathConditionWrapper extends StringPathCondition implements Serializable {

		/**
		 * 
		 */
		private static final long serialVersionUID = -2501829705046774079L;

		@Override
		public boolean hasConstraint(StringConstraint c) {

			if (!super.hasConstraint(c)) {
				constraints.add(c);
				return false;
			}
			return true;
		}

		public StringPathConditionWrapper(PathCondition npc) {
			super(npc);
		}

	}
}
