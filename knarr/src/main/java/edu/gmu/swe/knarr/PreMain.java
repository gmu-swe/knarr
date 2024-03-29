package edu.gmu.swe.knarr;

import java.lang.instrument.Instrumentation;
import java.util.Optional;

import edu.columbia.cs.psl.phosphor.Configuration;
import edu.gmu.swe.knarr.runtime.Coverage;
import edu.gmu.swe.knarr.runtime.PathConstraintTagFactory;
import edu.gmu.swe.knarr.runtime.TaintListener;

public class PreMain {
	public static void premain(String args, Instrumentation inst) {
		Configuration.IMPLICIT_TRACKING = false;
		// Configuration.ARRAY_LENGTH_TRACKING = true;
		Configuration.WITH_TAGS_FOR_JUMPS = true;
		Configuration.ARRAY_INDEX_TRACKING = true;
		Configuration.ARRAY_INDEX_TRACKING = true;
		Configuration.setTaintTagFactory(PathConstraintTagFactory.class);
		Configuration.derivedTaintListener = new TaintListener();
		Configuration.WITH_HEAVY_OBJ_EQUALS_HASHCODE = true;
		Configuration.WITH_ENUM_BY_VAL = true;
		Configuration.PREALLOC_STACK_OPS = true;
		Configuration.SINGLE_TAINT_LABEL = true;
		// Configuration.extensionClassVisitor =
		// DependencyTrackingClassVisitor.class;
		edu.columbia.cs.psl.phosphor.PreMain.DEBUG = true;

		Optional<String[]> o = Optional.empty(); // Java's type inference really sucks
		Coverage.setCov(true, o);

		Configuration.init();
		edu.columbia.cs.psl.phosphor.PreMain.premain(args, inst);
		System.out.println(Configuration.ARRAY_INDEX_TRACKING);
	}
}
