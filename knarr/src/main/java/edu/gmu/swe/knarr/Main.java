package edu.gmu.swe.knarr;

import edu.columbia.cs.psl.phosphor.Configuration;
import edu.columbia.cs.psl.phosphor.Instrumenter;
import edu.gmu.swe.knarr.runtime.PathConstraintTagFactory;

public class Main {
	public static void main(String[] _args) {
		String[] args = new String[_args.length + 3];
		args[0] = "-multiTaint";
		args[1] = "-withArrayLengthTags";
		args[2] = "-disableJumpOptimizations";
		args[3] = "-disableJumpOptimizations";
		Configuration.IMPLICIT_TRACKING = false;
		Configuration.ARRAY_LENGTH_TRACKING = true;
		Configuration.PREALLOC_STACK_OPS = true;
		Configuration.WITH_TAGS_FOR_JUMPS = true;
		Configuration.taintTagFactory = new PathConstraintTagFactory();
		System.arraycopy(_args, 0, args, 3, _args.length);
		// Instrumenter.addlTransformer = new CRClassFileTransformer();
		Instrumenter.main(args);
	}
}
