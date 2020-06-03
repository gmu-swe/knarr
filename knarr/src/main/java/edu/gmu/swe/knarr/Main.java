package edu.gmu.swe.knarr;

import edu.columbia.cs.psl.phosphor.Configuration;
import edu.columbia.cs.psl.phosphor.Instrumenter;
import edu.columbia.cs.psl.phosphor.PreMain;
import edu.gmu.swe.knarr.runtime.*;

public class Main {
	public static void main(String[] _args) {
		String[] args = new String[_args.length + 4];
		args[0] = "-multiTaint";
		args[1] = "-withArrayLengthTags";
		args[2] = "-disableJumpOptimizations";
		args[3] = "-withArrayIndexTags";
		Configuration.IMPLICIT_TRACKING = false;
		Configuration.ARRAY_LENGTH_TRACKING = true;
		Configuration.PREALLOC_STACK_OPS = true;
		Configuration.WITH_TAGS_FOR_JUMPS = true;
		Configuration.WITH_HEAVY_OBJ_EQUALS_HASHCODE = true;

//		Configuration.ANNOTATE_LOOPS = true;
//      Luís
//      Not sure ANOTATE_LOOPS is working correctly, and we weren't using it
//      I'm disabling it for now

		Configuration.extensionMethodVisitor = RedirectMethodsTaintAdapter.class;
		Configuration.extensionClassVisitor = StringTagFactory.class; // CountBytecodeAdapter.class;

		PathConstraintTagFactory.isRunning = false;

		//Configuration.ignoredMethods.add("java/lang/Double.parseDouble(Ljava/lang/String;)D");


		PathUtils.DISABLE_FLOATS = true;

		PreMain.DEBUG = System.getProperty("DEBUG") != null;
		Configuration.taintTagFactory = new PathConstraintTagFactory();
		Configuration.autoTainter = new KnarrAutoTainter();
		System.arraycopy(_args, 0, args, 4, _args.length);
		// Instrumenter.addlTransformer = new CRClassFileTransformer();
		Instrumenter.main(args);
	}
}
