package edu.gmu.swe.knarr.runtime;

import org.junit.Assert;

import edu.columbia.cs.psl.phosphor.runtime.Taint;

public class JunitAssert {
	
	public static void assertEquals$$PHOSPHORTAGGED(Taint<?> tExpected, double expected, Taint<?> tActual, double actual, Taint<?> tDelta, double delta) {
		// TODO
		Assert.assertEquals(expected, actual, delta);
	}
	
	public static void assertEquals$$PHOSPHORTAGGED(Taint<?> tExpected, float expected, Taint<?> tActual, float actual, Taint<?> tDelta, float delta) {
		// TODO
		Assert.assertEquals(expected, actual, delta);
	}
	
	public static void assertEquals$$PHOSPHORTAGGED(Taint<?> tExpected, long expected, Taint<?> tActual, long actual) {
		// TODO
		Assert.assertEquals(expected, actual);
	}
	
	public static void assertEquals(Object expected, Object actual) {
		// TODO
		Assert.assertEquals(expected, actual);
	}
	
	public static void assertSame(Object expected, Object actual) {
		// TODO
		Assert.assertSame(expected, actual);
	}
	
	public static void assertTrue$$PHOSPHORTAGGED(Taint t, boolean condition) {
		// TODO
		Assert.assertTrue(condition);
	}
	
	public static void assertFalse$$PHOSPHORTAGGED(Taint t, boolean condition) {
		// TODO
		Assert.assertFalse(condition);
	}

}
