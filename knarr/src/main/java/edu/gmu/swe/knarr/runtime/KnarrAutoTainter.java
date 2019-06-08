package edu.gmu.swe.knarr.runtime;

import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.runtime.TaintSourceWrapper;

public class KnarrAutoTainter extends TaintSourceWrapper {
	@Override
	public void combineTaintsOnArray(Object inputArray, Taint tag) {
		//NOP on purpose - don't set the char taints after setting the string taint
	}
}
