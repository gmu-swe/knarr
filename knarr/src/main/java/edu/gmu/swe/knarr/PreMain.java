package edu.gmu.swe.knarr;

import java.lang.instrument.Instrumentation;

import edu.columbia.cs.psl.phosphor.Configuration;
import edu.gmu.swe.knarr.runtime.PathConstraintTagFactory;

public class PreMain {
	public static void premain(String args, Instrumentation inst) {
		Configuration.IMPLICIT_TRACKING = false;
		// Configuration.ARRAY_LENGTH_TRACKING = true;
		Configuration.WITH_TAGS_FOR_JUMPS = true;
		Configuration.taintTagFactory = new PathConstraintTagFactory();
		Configuration.PREALLOC_STACK_OPS = true;
		// Configuration.extensionClassVisitor =
		// DependencyTrackingClassVisitor.class;
		Configuration.init();
		edu.columbia.cs.psl.phosphor.PreMain.premain(args, inst);
	}
}
